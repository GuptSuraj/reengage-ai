"use client";
import {useCallback,useEffect,useState} from "react";
import {api} from "@/lib/api";

type Dashboard={metrics:Record<string,number>;activity:Record<string,unknown>[];notifications:Record<string,unknown>[];eventSeries:Record<string,unknown>[];variants:Record<string,unknown>[]};
type Profile={intent_score:number;intent_level:string;intent_signals:Record<string,unknown>[];profile:Record<string,unknown>;model_version:string};

const labels:Record<string,string>={total_events:"Behaviour events",active_users:"Active users",high_intent_users:"High intent",
  abandoned_carts:"Abandoned carts",scheduled_notifications:"Scheduled",delivered_notifications:"Delivered",
  recovered_carts:"Recovered carts",outbox_lag:"Outbox lag"};

export default function Admin(){
  const [data,setData]=useState<Dashboard|null>(null);const [profile,setProfile]=useState<Profile|null>(null);
  const [error,setError]=useState("");const [simulating,setSimulating]=useState(false);
  const load=useCallback(async()=>{try{setData(await api<Dashboard>("admin/analytics"));setProfile(await api<Profile>("profile"));setError("")}
    catch(e){setError(e instanceof Error?e.message:"Unable to load dashboard")}},[]);
  useEffect(()=>{load();const timer=setInterval(load,3000);return()=>clearInterval(timer)},[load]);
  async function simulate(){
    setSimulating(true);const sessionId=crypto.randomUUID();const now=Date.now();
    const kinds=["session_started","search_performed","filter_applied","product_viewed","product_viewed","product_compared","time_spent","add_to_cart"];
    const events=kinds.map((eventType,i)=>({eventId:crypto.randomUUID(),sessionId,eventType,productId:i<3?undefined:"sony-wh-ch720n",
      timestamp:new Date(now-(kinds.length-i)*1000).toISOString(),sourcePage:"/products/sony-wh-ch720n",device:{type:"desktop"},
      metadata:eventType==="search_performed"?{query:"wireless noise cancelling headphones"}:eventType==="time_spent"?{seconds:280}:{}}));
    await api("events/batch",{method:"POST",body:JSON.stringify({events})});setTimeout(()=>{load();setSimulating(false)},2500);
  }
  if(error)return <main className="admin-shell"><div className="access-card"><h1>Administrator access required</h1><p>{error}</p><a className="button primary" href="/login">Sign in as administrator</a></div></main>;
  const metrics=data?.metrics??{};const score=Math.round(Number(profile?.intent_score??0)*100);
  return <main className="admin-shell"><header className="admin-header"><div><span className="eyebrow">PRODUCTION WORKSPACE</span><h1>Intelligence overview</h1><p>Event pipeline, purchase intent, recommendations, and notification delivery.</p></div>
    <div><span className="healthy">● All systems healthy</span><button className="button primary" disabled={simulating} onClick={simulate}>{simulating?"Processing…":"▶ Simulate high-intent journey"}</button></div></header>
    <section className="metrics">{Object.entries(labels).map(([key,label])=><article key={key}><span>{label}</span><strong>{metrics[key]??0}</strong><small>{key==="outbox_lag"?(metrics[key]?"Waiting for Kafka":"Caught up"):"Live database metric"}</small></article>)}</section>
    <section className="dashboard-grid"><article className="panel intent-panel"><div className="panel-title"><div><h2>Current admin intent</h2><p>Explainable FastAPI score</p></div><span className={`pill ${profile?.intent_level?.toLowerCase()}`}>{profile?.intent_level??"LOW"}</span></div>
      <div className="gauge" style={{"--score":`${score}%`} as React.CSSProperties}><div><b>{score}%</b><span>purchase intent</span></div></div>
      <div className="signals">{(profile?.intent_signals??[]).map((s,i)=><span key={i}>{String(s.name)} +{String(s.impact)}</span>)}</div><small>Model: {profile?.model_version??"awaiting events"}</small></article>
      <article className="panel events-panel"><div className="panel-title"><div><h2>Live events</h2><p>Most recently ingested</p></div><span className="pill live">● LIVE</span></div>
        <div className="table">{(data?.activity??[]).map((event,i)=><div className="table-row" key={i}><i>↯</i><span><b>{String(event.event_type).replaceAll("_"," ")}</b><small>{String(event.product_id??event.user_id)}</small></span><time>{new Date(String(event.received_at)).toLocaleTimeString()}</time></div>)}</div></article>
      <article className="panel queue-panel"><div className="panel-title"><div><h2>Notification lifecycle</h2><p>Canonical PostgreSQL jobs</p></div></div>
        <div className="table">{(data?.notifications??[]).length?(data?.notifications??[]).map((job,i)=><div className="table-row" key={i}><i>{job.channel==="WHATSAPP"?"WA":"EM"}</i><span><b>{String(job.product_name)}</b><small>{String(job.channel)} · retry {String(job.retry_count)}</small></span><span className={`pill ${String(job.status).toLowerCase()}`}>{String(job.status)}</span></div>):<div className="empty compact">No jobs yet. Run the simulation.</div>}</div></article>
      <article className="panel architecture-panel"><div className="panel-title"><div><h2>Runtime topology</h2><p>Every box is a real container boundary</p></div></div>
        <div className="topology"><span>Next.js</span><b>→</b><span>Spring Boot</span><b>→</b><span>Kafka</span><b>→</b><span>FastAPI</span><i>PostgreSQL</i><i>Redis</i><i>Qdrant</i><i>OpenTelemetry</i></div></article>
    </section>
  </main>;
}

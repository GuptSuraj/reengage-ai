"use client";
import {FormEvent,useState} from "react";
import {useRouter} from "next/navigation";

export default function Login(){
  const router=useRouter(); const [email,setEmail]=useState("demo@reengage.ai");
  const [password,setPassword]=useState("Demo@12345"); const [error,setError]=useState("");
  async function submit(event:FormEvent){event.preventDefault();setError("");
    const response=await fetch("/api/auth/login",{method:"POST",headers:{"content-type":"application/json"},body:JSON.stringify({email,password})});
    const body=await response.json(); if(!response.ok){setError(body.detail??"Sign in failed");return;}
    router.push(body.role==="ADMIN"?"/admin":"/");router.refresh();
  }
  function useAdmin(){setEmail("admin@reengage.ai");setPassword("Admin@12345");}
  return <main className="auth-screen"><form className="auth-card" onSubmit={submit}>
    <div className="brand"><span className="brand-mark"><i/><i/><i/></span><b>REENGAGE<span>AI</span></b></div>
    <span className="eyebrow">SECURE DEMO ACCESS</span><h1>Commerce with better timing.</h1><p>Sign in to generate behaviour, inspect recommendations, and watch notification cancellation happen live.</p>
    <label>Email<input type="email" value={email} onChange={e=>setEmail(e.target.value)} required/></label>
    <label>Password<input type="password" value={password} onChange={e=>setPassword(e.target.value)} required/></label>
    {error&&<p className="error">{error}</p>}<button className="button primary full">Sign in <span>→</span></button>
    <button className="text-button" type="button" onClick={useAdmin}>Use administrator account</button>
    <small>Customer: demo@reengage.ai · Demo@12345<br/>Admin: admin@reengage.ai · Admin@12345</small>
  </form></main>;
}

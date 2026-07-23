"use client";

import Image from "next/image";
import Link from "next/link";
import {useRouter} from "next/navigation";
import {useEffect,useMemo,useState} from "react";
import {ProductVisual} from "@/components/ProductVisual";
import {useTracker} from "@/components/TrackerProvider";
import {api,money,Product} from "@/lib/api";

type Collection={products:Product[];categories:string[];brands:string[]};

export default function Store(){
  const router=useRouter();
  const tracker=useTracker();
  const [data,setData]=useState<Collection>({products:[],categories:[],brands:[]});
  const [query,setQuery]=useState(""); const [category,setCategory]=useState("");
  const [brand,setBrand]=useState(""); const [maxPrice,setMaxPrice]=useState(40000);
  const [compare,setCompare]=useState<string[]>([]); const [loading,setLoading]=useState(true);
  useEffect(()=>{
    const timer=setTimeout(async()=>{
      setLoading(true);
      const p=new URLSearchParams({q:query,category,brand,maxPrice:String(maxPrice)});
      try{setData(await api<Collection>(`products?${p}`));}finally{setLoading(false);}
      if(query)tracker?.track("search_performed",{metadata:{query}});
    },query?300:0);
    return()=>clearTimeout(timer);
  },[query,category,brand,maxPrice,tracker]);
  const compared=useMemo(()=>data.products.filter(p=>compare.includes(p.id)),[data.products,compare]);
  async function add(product:Product){
    try{
      await api(`cart/items/${product.id}`,{method:"PUT",body:JSON.stringify({quantity:1})});
      tracker?.track("add_to_cart",{productId:product.id});
      window.dispatchEvent(new Event("cart-updated"));
    }catch{router.push("/login")}
  }
  function toggle(id:string){
    setCompare(current=>current.includes(id)?current.filter(x=>x!==id):current.length<3?[...current,id]:current);
    tracker?.track("product_compared",{productId:id,metadata:{shortlist:[...compare,id]}});
  }
  return <main>
    <section className="hero shell">
      <Image src="/commerce-hero.png" fill priority sizes="100vw" alt="Premium personal electronics"/>
      <div className="hero-shade"/>
      <div className="hero-copy"><span className="eyebrow light">CURATED FOR HOW YOU SHOP</span>
        <h1>Good choices,<br/><em>made obvious.</em></h1>
        <p>Technology that fits your day. Recommendations adapt to what you explore—without noisy follow-ups.</p>
        <a className="button light-button" href="#catalog">Explore audio <span>↗</span></a>
      </div>
      <div className="hero-proof"><small>PERSONALISED IN REAL TIME</small><b>4.8 <i>★★★★★</i></b></div>
    </section>
    <section className="catalog shell" id="catalog">
      <div className="section-heading"><div><span className="eyebrow">THE EDIT</span><h2>Find your next favourite</h2></div><p>{data.products.length} products selected</p></div>
      <div className="search-row"><label className="search"><span>⌕</span><input value={query} onChange={e=>setQuery(e.target.value)} placeholder="Search headphones, speakers, wearables…"/></label>
        <select value={brand} onChange={e=>{setBrand(e.target.value);tracker?.track("filter_applied",{metadata:{brand:e.target.value}})}}><option value="">All brands</option>{data.brands.map(x=><option key={x}>{x}</option>)}</select>
      </div>
      <div className="filter-row"><div className="tabs"><button className={!category?"active":""} onClick={()=>setCategory("")}>All products</button>{data.categories.map(x=><button className={category===x?"active":""} onClick={()=>{setCategory(x);tracker?.track("filter_applied",{metadata:{category:x}})}} key={x}>{x}</button>)}</div>
        <label className="range">Up to {money(maxPrice)} <input type="range" min="5000" max="40000" step="1000" value={maxPrice} onChange={e=>setMaxPrice(Number(e.target.value))}/></label></div>
      {compare.length>=2&&<div className="compare-bar"><span>{compare.length} products shortlisted</span>{compared.map(p=><b key={p.id}>{p.name}</b>)}<button onClick={()=>setCompare([])}>Clear</button></div>}
      <div className="product-grid">{loading?<div className="empty">Loading the catalogue…</div>:data.products.map((product,index)=><article className="product-card" key={product.id}>
        <div className="product-art" style={{"--accent":product.accent} as React.CSSProperties}>
          {index===0&&<span className="badge">MOST RELEVANT</span>}
          <button aria-label="Compare" className={`compare ${compare.includes(product.id)?"selected":""}`} onClick={()=>toggle(product.id)}>{compare.includes(product.id)?"✓":"+"}</button>
          <Link href={`/products/${product.id}`} onClick={()=>tracker?.track("product_viewed",{productId:product.id})}><ProductVisual kind={product.imageKey}/></Link>
        </div><div className="product-info"><Link href={`/products/${product.id}`}><h3>{product.name}</h3></Link><p>{product.category} · {product.brand}</p>
          <div><b>{money(product.priceInr)}</b><span>★ {product.rating}</span></div><button className="quick-add" onClick={()=>add(product)}>Add to bag</button></div>
      </article>)}</div>
    </section>
    <section className="principles shell"><div><span className="eyebrow">SHOP WITH CONFIDENCE</span><h2>Less noise.<br/>Better decisions.</h2></div>
      <div className="principle-grid"><article><span>01</span><h3>Explainable intent</h3><p>Every decision shows the behaviour that influenced it.</p></article><article><span>02</span><h3>Relevant choices</h3><p>Content, price, quality, and recent affinity shape each recommendation.</p></article><article><span>03</span><h3>Consent first</h3><p>Eligibility is checked before scheduling and again before delivery.</p></article></div>
    </section>
  </main>;
}

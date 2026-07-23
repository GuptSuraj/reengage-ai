"use client";
import Link from "next/link";
import {useParams,useRouter} from "next/navigation";
import {useEffect,useState} from "react";
import {ProductVisual} from "@/components/ProductVisual";
import {useTracker} from "@/components/TrackerProvider";
import {api,features,money,Product} from "@/lib/api";

export default function ProductDetail(){
  const {id}=useParams<{id:string}>(); const router=useRouter(); const tracker=useTracker();
  const [product,setProduct]=useState<Product|null>(null); const [recs,setRecs]=useState<Record<string,unknown>[]>([]);
  useEffect(()=>{api<Product>(`products/${id}`).then(p=>{setProduct(p);tracker?.page(p.id)});
    api<Record<string,unknown>[]>("recommendations").then(setRecs).catch(()=>{});},[id,tracker]);
  async function add(){if(!product)return;try{await api(`cart/items/${product.id}`,{method:"PUT",body:JSON.stringify({quantity:1})});
    tracker?.track("add_to_cart",{productId:product.id});window.dispatchEvent(new Event("cart-updated"));}
    catch{router.push("/login")}}
  if(!product)return <main className="inner shell"><div className="empty">Loading product…</div></main>;
  return <main className="inner shell"><Link className="back" href="/">← Back to collection</Link><section className="detail">
    <div className="detail-art product-art" style={{"--accent":product.accent} as React.CSSProperties}><ProductVisual kind={product.imageKey}/></div>
    <div className="detail-copy"><span className="eyebrow">{product.category.toUpperCase()}</span><h1>{product.name}</h1><p>{product.brand} · ★ {product.rating}</p><strong>{money(product.priceInr)}</strong>
      <p className="description">{product.description}</p><div className="feature-list">{features(product).map(x=><span key={x}>✓ {x}</span>)}</div><p>{product.stock} ready to dispatch</p>
      <button className="button primary full" onClick={add}>Add to bag →</button>
    </div></section>
    {recs.length>0&&<section className="recommendations"><span className="eyebrow">YOUR RANKED PICKS</span><h2>Recommended by the intelligence service</h2><div className="recommendation-grid">{recs.map((r,i)=><article key={String(r.productId??r.id)}><b>0{i+1}</b><h3>{String(r.name??r.productId)}</h3><p>{String(r.reason)}</p><span>{Math.round(Number(r.score)*100)}% match</span></article>)}</div></section>}
  </main>;
}

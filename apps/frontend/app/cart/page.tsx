"use client";
import Link from "next/link";
import {useEffect,useState} from "react";
import {ProductVisual} from "@/components/ProductVisual";
import {useTracker} from "@/components/TrackerProvider";
import {api,CartItem,money} from "@/lib/api";

export default function Cart(){
  const tracker=useTracker(); const [cart,setCart]=useState<{items:CartItem[];totalInr:number}>({items:[],totalInr:0});
  const [message,setMessage]=useState("");
  const load=()=>api<typeof cart>("cart").then(setCart);
  useEffect(()=>{load().catch(()=>location.href="/login")},[]);
  async function remove(id:string){await api(`cart/items/${id}`,{method:"DELETE"});tracker?.track("remove_from_cart",{productId:id});await load();window.dispatchEvent(new Event("cart-updated"))}
  async function checkout(){if(!cart.items.length)return;tracker?.track("checkout_started",{productId:cart.items[0].productId});await tracker?.flush();
    const order=await api<{id:string;totalInr:number}>("checkout",{method:"POST",headers:{"Idempotency-Key":crypto.randomUUID()}});
    setMessage(`Order ${order.id} completed. Any pending reminder was cancelled transactionally.`);await load();window.dispatchEvent(new Event("cart-updated"))}
  return <main className="inner shell"><div className="page-heading"><div><span className="eyebrow">YOUR SELECTION</span><h1>Shopping bag</h1></div><Link href="/">Continue shopping →</Link></div>
    {message&&<div className="success-banner">{message}</div>}<div className="cart-layout"><section>
      {!cart.items.length?<div className="empty"><h2>Your bag is empty</h2><Link className="button primary" href="/">Browse products</Link></div>:cart.items.map(item=><article className="cart-item" key={item.productId}>
        <div className="product-art" style={{"--accent":item.accent} as React.CSSProperties}><ProductVisual kind={item.imageKey}/></div>
        <div><h3>{item.name}</h3><p>{item.brand} · Qty {item.quantity}</p><button onClick={()=>remove(item.productId)}>Remove</button></div><b>{money(item.priceInr*item.quantity)}</b>
      </article>)}</section><aside className="summary"><span className="eyebrow">ORDER SUMMARY</span><div><span>Subtotal</span><b>{money(cart.totalInr)}</b></div><div><span>Delivery</span><b>Complimentary</b></div><div className="total"><span>Total</span><b>{money(cart.totalInr)}</b></div><button className="button primary full" disabled={!cart.items.length} onClick={checkout}>Complete order →</button><small>Stock locked and revalidated during checkout</small></aside></div>
  </main>;
}

"use client";

import Link from "next/link";
import {usePathname, useRouter} from "next/navigation";
import {useEffect, useState} from "react";
import {api} from "@/lib/api";

export function Header() {
  const path = usePathname();
  const router = useRouter();
  const [count, setCount] = useState(0);
  const [authenticated,setAuthenticated]=useState(false);
  useEffect(() => {
    setAuthenticated(document.cookie.includes("reengage_role="));
    api<{items: {quantity: number}[]}>("cart").then(x => setCount(x.items.reduce((a,b)=>a+b.quantity,0))).catch(()=>{});
    const listener = () => api<{items:{quantity:number}[]}>("cart").then(x=>setCount(x.items.reduce((a,b)=>a+b.quantity,0))).catch(()=>{});
    window.addEventListener("cart-updated", listener);
    return () => window.removeEventListener("cart-updated", listener);
  }, []);
  async function logout() {
    await fetch("/api/auth/logout", {method:"POST"});
    router.push("/login"); router.refresh();
  }
  return <header className="site-header">
    <Link className="brand" href="/"><span className="brand-mark"><i/><i/><i/></span><b>REENGAGE<span>AI</span></b></Link>
    <nav><Link className={path==="/"?"active":""} href="/">Store</Link><Link href="/cart">Cart</Link><Link href="/admin">Intelligence</Link></nav>
    <div className="header-actions">{authenticated?<button className="ghost" onClick={logout}>Sign out</button>:<Link className="ghost" href="/login">Sign in</Link>}<Link className="bag" href="/cart">Bag <b>{count}</b></Link></div>
  </header>;
}

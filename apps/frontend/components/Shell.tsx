"use client";
import {usePathname} from "next/navigation";
import {Header} from "./Header";
export function Shell({children}:{children:React.ReactNode}){
  const path=usePathname();
  return <>{path!=="/login"&&<Header/>}{children}</>;
}

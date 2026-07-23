"use client";

import {createTracker, ReEngageTracker} from "@reengage/tracker";
import {createContext, useContext, useEffect, useState} from "react";

const TrackerContext = createContext<ReEngageTracker | null>(null);
export function TrackerProvider({children}:{children:React.ReactNode}) {
  const [tracker,setTracker]=useState<ReEngageTracker|null>(null);
  useEffect(()=>{
    let anonymousId=localStorage.getItem("reengage_anon");
    if(!anonymousId){anonymousId=crypto.randomUUID();localStorage.setItem("reengage_anon",anonymousId);}
    const instance=createTracker({
      endpoint:"/api/proxy/events/batch",
      anonymousId,
      batchSize:5, flushIntervalMs:2000,
    });
    setTracker(instance);
    return ()=>instance.destroy();
  },[]);
  return <TrackerContext.Provider value={tracker}>{children}</TrackerContext.Provider>;
}
export const useTracker=()=>useContext(TrackerContext);

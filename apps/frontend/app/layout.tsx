import type {Metadata} from "next";
import {Manrope, DM_Sans} from "next/font/google";
import "./globals.css";
import {Shell} from "@/components/Shell";
import {TrackerProvider} from "@/components/TrackerProvider";

const manrope=Manrope({subsets:["latin"],variable:"--font-display"});
const dm=DM_Sans({subsets:["latin"],variable:"--font-body"});
export const metadata:Metadata={
  title:{default:"ReEngageAI",template:"%s · ReEngageAI"},
  description:"Intent-aware commerce and customer re-engagement platform",
};
export default function RootLayout({children}:{children:React.ReactNode}){
  return <html lang="en"><body className={`${manrope.variable} ${dm.variable}`}>
    <TrackerProvider><Shell>{children}</Shell></TrackerProvider>
  </body></html>;
}

import { cookies } from "next/headers";
import { NextRequest, NextResponse } from "next/server";

const core = process.env.CORE_API_URL ?? "http://localhost:8080";

async function forward(request: NextRequest, context: {params: Promise<{path: string[]}>}) {
  const {path} = await context.params;
  const target = new URL(`${core}/api/v1/${path.join("/")}`);
  request.nextUrl.searchParams.forEach((value, key) => target.searchParams.append(key, value));
  const token = (await cookies()).get("reengage_session")?.value;
  const headers = new Headers();
  headers.set("accept", "application/json");
  if (request.headers.get("content-type")) headers.set("content-type", request.headers.get("content-type")!);
  if (request.headers.get("idempotency-key")) headers.set("idempotency-key", request.headers.get("idempotency-key")!);
  if (token) headers.set("authorization", `Bearer ${token}`);
  const hasBody = !["GET", "HEAD"].includes(request.method);
  const upstream = await fetch(target, {
    method: request.method, headers, body: hasBody ? await request.arrayBuffer() : undefined,
    cache: "no-store",
  });
  return new NextResponse(upstream.body, {
    status: upstream.status,
    headers: {"content-type": upstream.headers.get("content-type") ?? "application/json"},
  });
}

export const GET = forward;
export const POST = forward;
export const PUT = forward;
export const PATCH = forward;
export const DELETE = forward;

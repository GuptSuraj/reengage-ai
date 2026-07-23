import { NextRequest, NextResponse } from "next/server";

const core = process.env.CORE_API_URL ?? "http://localhost:8080";

export async function POST(request: NextRequest) {
  const response = await fetch(`${core}/api/v1/auth/login`, {
    method: "POST",
    headers: {"content-type": "application/json"},
    body: await request.text(),
    cache: "no-store",
  });
  const body = await response.json();
  if (!response.ok) return NextResponse.json(body, {status: response.status});
  const result = NextResponse.json({
    userId: body.userId, email: body.email, role: body.role,
  });
  result.cookies.set("reengage_session", body.accessToken, {
    httpOnly: true, sameSite: "strict", secure: process.env.COOKIE_SECURE === "true",
    path: "/", maxAge: 8 * 60 * 60,
  });
  result.cookies.set("reengage_role", body.role, {
    httpOnly: false, sameSite: "strict", secure: process.env.COOKIE_SECURE === "true",
    path: "/", maxAge: 8 * 60 * 60,
  });
  return result;
}

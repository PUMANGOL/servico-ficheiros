import { NextResponse } from "next/server";
import { encryptAdminKey, SESSION_COOKIE, sessionTtlSeconds } from "@/lib/session";
import { serviceApiUrl } from "@/lib/service-api";

export const runtime = "nodejs";

export async function POST(request: Request) {
  let body: { adminKey?: unknown };
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ detail: "Pedido inválido." }, { status: 400 });
  }

  const adminKey = typeof body.adminKey === "string" ? body.adminKey.trim() : "";
  if (adminKey.length < 24) {
    return NextResponse.json({ detail: "Introduza uma chave de administração válida." }, { status: 400 });
  }

  try {
    const upstream = await fetch(serviceApiUrl("/api/v1/admin/applications"), {
      headers: { "X-Admin-Key": adminKey, Accept: "application/json" },
      cache: "no-store",
    });
    if (!upstream.ok) {
      return NextResponse.json(
        { detail: upstream.status === 401 ? "Chave de administração inválida." : "Não foi possível validar a chave." },
        { status: upstream.status === 401 ? 401 : 502 },
      );
    }

    const response = NextResponse.json({ authenticated: true });
    response.cookies.set(SESSION_COOKIE, encryptAdminKey(adminKey), {
      httpOnly: true,
      secure: process.env.NODE_ENV === "production",
      sameSite: "strict",
      path: "/",
      maxAge: sessionTtlSeconds,
    });
    return response;
  } catch (error) {
    const detail = error instanceof Error && error.message.includes("DASHBOARD_SESSION_SECRET")
      ? error.message
      : "Não foi possível contactar o serviço de ficheiros.";
    return NextResponse.json({ detail }, { status: 503 });
  }
}

import { NextResponse } from "next/server";
import { proxyAdminApi } from "@/lib/service-api";

export const runtime = "nodejs";

type RouteContext = { params: Promise<{ path: string[] }> };

function allowedPath(path: string[], method: string): string | null {
  const joined = path.join("/");
  if (joined === "applications" && (method === "GET" || method === "POST")) {
    return "/api/v1/admin/applications";
  }
  if (/^applications\/[a-z0-9-]+$/.test(joined) && method === "PUT") {
    return `/api/v1/admin/${joined}`;
  }
  if (/^applications\/[a-z0-9-]+\/rotate-key$/.test(joined) && method === "POST") {
    return `/api/v1/admin/${joined}`;
  }
  const dashboardEndpoints: Record<string, string> = {
    "dashboard/health": "/api/v1/admin/dashboard/health",
    "dashboard/metrics": "/api/v1/admin/dashboard/metrics",
    "dashboard/activity": "/api/v1/admin/dashboard/activity",
    "dashboard/daily": "/api/v1/admin/dashboard/daily",
  };
  if (method === "GET" && joined === "dashboard/files") {
    return "/api/v1/admin/dashboard/files";
  }
  const fileContent = joined.match(/^dashboard\/files\/([0-9a-f-]{36})\/content$/i);
  if (method === "GET" && fileContent) {
    return `/api/v1/admin/dashboard/files/${fileContent[1]}/content`;
  }
  return method === "GET" ? dashboardEndpoints[joined] ?? null : null;
}

async function handle(request: Request, context: RouteContext) {
  const { path } = await context.params;
  const method = request.method;
  const upstreamPath = allowedPath(path, method);
  if (!upstreamPath) {
    return NextResponse.json({ detail: "Rota administrativa não permitida." }, { status: 404 });
  }
  return proxyAdminApi(upstreamPath, request, method as "GET" | "POST" | "PUT");
}

export const GET = handle;
export const POST = handle;
export const PUT = handle;

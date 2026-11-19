import { NextResponse } from "next/server";
import { getAdminKey } from "@/lib/session";

export const DEFAULT_FILESERVICE_API_URL = "https://portal.pumangol.co.ao/ficheiros";

export function serviceApiUrl(path: string): string {
  const baseUrl = (process.env.FILESERVICE_API_URL || DEFAULT_FILESERVICE_API_URL).replace(/\/+$/, "");
  return `${baseUrl}${path}`;
}

export async function proxyAdminApi(
  path: string,
  request: Request,
  method: "GET" | "POST" | "PUT",
): Promise<NextResponse> {
  const adminKey = await getAdminKey();
  if (!adminKey) {
    return NextResponse.json({ detail: "Sessão expirada. Inicie sessão novamente." }, { status: 401 });
  }

  const incomingUrl = new URL(request.url);
  const targetUrl = new URL(serviceApiUrl(path));
  targetUrl.search = incomingUrl.search;
  const headers = new Headers({ "X-Admin-Key": adminKey, Accept: "application/json" });
  let body: string | undefined;
  if (method !== "GET") {
    body = await request.text();
    headers.set("Content-Type", "application/json");
  }

  let upstream: Response;
  try {
    upstream = await fetch(targetUrl, {
      method,
      headers,
      body,
      cache: "no-store",
    });
  } catch {
    return NextResponse.json(
      { detail: "Não foi possível contactar o serviço de ficheiros." },
      { status: 502 },
    );
  }

  const responseHeaders = new Headers({
    "Content-Type": upstream.headers.get("content-type") ?? "application/json",
    "Cache-Control": "no-store",
  });
  const contentDisposition = upstream.headers.get("content-disposition");
  if (contentDisposition) responseHeaders.set("Content-Disposition", contentDisposition);
  const contentLength = upstream.headers.get("content-length");
  if (contentLength) responseHeaders.set("Content-Length", contentLength);
  return new NextResponse(upstream.body, {
    status: upstream.status,
    headers: responseHeaders,
  });
}

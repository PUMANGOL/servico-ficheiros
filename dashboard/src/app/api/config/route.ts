import { NextResponse } from "next/server";
import { DEFAULT_FILESERVICE_API_URL } from "@/lib/service-api";

export async function GET() {
  const apiUrl = process.env.FILESERVICE_API_URL || DEFAULT_FILESERVICE_API_URL;
  return NextResponse.json({
    apiConfigured: true,
    apiUrl,
    publicUrl: process.env.FILESERVICE_PUBLIC_URL ?? "",
    sessionSecretConfigured: Boolean(
      process.env.DASHBOARD_SESSION_SECRET && process.env.DASHBOARD_SESSION_SECRET.length >= 32,
    ),
    sessionCookieSecure: process.env.NODE_ENV === "production",
    eventRetentionDays: 90,
  });
}

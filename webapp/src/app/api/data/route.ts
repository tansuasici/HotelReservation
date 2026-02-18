import { NextResponse } from "next/server";

export const dynamic = "force-dynamic";

const BACKEND = process.env.BACKEND_URL || "http://localhost:3001";

async function fetchJson(url: string) {
  try {
    const res = await fetch(url);
    if (!res.ok) return null;
    return await res.json();
  } catch {
    return null;
  }
}

export async function GET() {
  const [topology, hotels, customers, activity] = await Promise.all([
    fetchJson(`${BACKEND}/api/network/topology`),
    fetchJson(`${BACKEND}/api/data/hotels`),
    fetchJson(`${BACKEND}/api/customers/status`),
    fetchJson(`${BACKEND}/api/activity?since=0`),
  ]);

  if (!topology) {
    return NextResponse.json(
      { error: "No simulation data found. Is the backend running?" },
      { status: 404 }
    );
  }

  return NextResponse.json({ topology, hotels, customers, activity });
}

import { NextResponse } from "next/server";

export const dynamic = "force-dynamic";

const BACKEND = process.env.BACKEND_URL || "http://localhost:3001";

async function fetchJson(label: string, url: string) {
  try {
    const res = await fetch(url);
    if (!res.ok) {
      console.log("[DATA]   ✗ %s → HTTP %d", label, res.status);
      return null;
    }
    const data = await res.json();
    const summary = Array.isArray(data)
      ? `[${data.length} items]`
      : JSON.stringify(data).slice(0, 200);
    console.log("[DATA]   ← %s → %s", label, summary);
    return data;
  } catch (e) {
    console.log("[DATA]   ✗ %s → %s", label, (e as Error).message);
    return null;
  }
}

export async function GET() {
  console.log("[DATA] → fetching 4 endpoints in parallel");

  const [topology, hotels, customers, activity] = await Promise.all([
    fetchJson("topology", `${BACKEND}/api/network/topology`),
    fetchJson("hotels", `${BACKEND}/api/data/hotels`),
    fetchJson("customers", `${BACKEND}/api/customers/status`),
    fetchJson("activity", `${BACKEND}/api/activity?since=0`),
  ]);

  if (!topology) {
    console.log("[DATA] ✗ topology null — returning 404");
    return NextResponse.json(
      { error: "No simulation data found. Is the backend running?" },
      { status: 404 }
    );
  }

  console.log(
    "[DATA] ✓ aggregate → nodes: %d | edges: %d | hotels: %d | customers: %d | activity: %d",
    topology.nodes?.length ?? 0,
    topology.edges?.length ?? 0,
    hotels?.length ?? 0,
    customers?.length ?? 0,
    activity?.length ?? 0
  );

  return NextResponse.json({ topology, hotels, customers, activity });
}

import { NextResponse } from "next/server";

export const dynamic = "force-dynamic";

const BACKEND = process.env.BACKEND_URL || "http://localhost:3001";

// GET — read simulation state from Spring Boot
export async function GET() {
  try {
    const res = await fetch(`${BACKEND}/api/simulation/status`);
    if (!res.ok) {
      console.log("[SIM] ✗ Backend not reachable (status %d)", res.status);
      return NextResponse.json(
        { state: "NOT_INITIALIZED", message: "Backend not reachable", processAlive: false },
        { status: 502 }
      );
    }
    const data = await res.json();
    const processAlive = data.state !== "ENDED" && data.state !== "NOT_INITIALIZED";
    console.log("[SIM] ← GET /simulation/status → %j", data);
    console.log("[SIM]   response → { state: %s, tick: %d, alive: %s }", data.state, data.currentTick ?? 0, processAlive);
    return NextResponse.json({
      state: data.state,
      message: data.message || "",
      currentTick: data.currentTick ?? 0,
      processAlive,
    });
  } catch (e) {
    console.log("[SIM] ✗ Cannot connect to backend:", (e as Error).message);
    return NextResponse.json(
      { state: "NOT_INITIALIZED", message: "Cannot connect to backend at " + BACKEND, processAlive: false },
      { status: 502 }
    );
  }
}

// POST — send action to Spring Boot simulation
export async function POST(req: Request) {
  const body = await req.json();
  const action: string = body.action;
  console.log("[SIM] → POST /simulation?action=%s  body: %j", action, body);

  try {
    const res = await fetch(`${BACKEND}/api/simulation?action=${action}`, {
      method: "POST",
    });
    if (!res.ok) {
      const text = await res.text();
      let parsed;
      try { parsed = JSON.parse(text); } catch { parsed = { message: text }; }
      console.log("[SIM] ✗ action=%s failed (%d): %j", action, res.status, parsed);
      return NextResponse.json(
        { state: parsed.state || "ERROR", message: parsed.message || text, processAlive: false },
        { status: res.status }
      );
    }
    const data = await res.json();
    const processAlive = data.state !== "ENDED" && data.state !== "NOT_INITIALIZED";
    console.log("[SIM] ← POST response: %j", data);
    console.log("[SIM]   response → { state: %s, alive: %s }", data.state, processAlive);
    return NextResponse.json({
      state: data.state,
      message: data.message || "",
      processAlive,
    });
  } catch (e) {
    console.log("[SIM] ✗ action=%s error:", action, (e as Error).message);
    return NextResponse.json(
      { state: "NOT_INITIALIZED", message: "Cannot connect to backend at " + BACKEND, processAlive: false },
      { status: 502 }
    );
  }
}

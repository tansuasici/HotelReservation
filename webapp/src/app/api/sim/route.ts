import { NextResponse } from "next/server";

export const dynamic = "force-dynamic";

const BACKEND = process.env.BACKEND_URL || "http://localhost:3001";

// GET — read simulation state from Spring Boot
export async function GET() {
  try {
    const res = await fetch(`${BACKEND}/api/simulation/status`);
    if (!res.ok) {
      return NextResponse.json(
        { state: "NOT_INITIALIZED", message: "Backend not reachable", processAlive: false },
        { status: 502 }
      );
    }
    const data = await res.json();
    const processAlive = data.state !== "ENDED" && data.state !== "NOT_INITIALIZED";
    return NextResponse.json({
      state: data.state,
      message: data.message || "",
      currentTick: data.currentTick ?? 0,
      processAlive,
    });
  } catch {
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

  try {
    const res = await fetch(`${BACKEND}/api/simulation?action=${action}`, {
      method: "POST",
    });
    if (!res.ok) {
      const text = await res.text();
      let parsed;
      try { parsed = JSON.parse(text); } catch { parsed = { message: text }; }
      return NextResponse.json(
        { state: parsed.state || "ERROR", message: parsed.message || text, processAlive: false },
        { status: res.status }
      );
    }
    const data = await res.json();
    const processAlive = data.state !== "ENDED" && data.state !== "NOT_INITIALIZED";
    return NextResponse.json({
      state: data.state,
      message: data.message || "",
      processAlive,
    });
  } catch {
    return NextResponse.json(
      { state: "NOT_INITIALIZED", message: "Cannot connect to backend at " + BACKEND, processAlive: false },
      { status: 502 }
    );
  }
}

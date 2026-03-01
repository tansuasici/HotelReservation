import { NextResponse } from "next/server";

export const dynamic = "force-dynamic";

const BACKEND = process.env.BACKEND_URL || "http://localhost:8000";

/**
 * Map SCOP ExecutionState to simplified SimState.
 * SCOP states: NOT_INIT, INITIALIZED, RUNNING, STEP, PRE_EPISODE, POST_EPISODE, PAUSED, ENDED
 */
function mapState(scopState: string): string {
  switch (scopState) {
    case "RUNNING":
    case "STEP":
    case "PRE_EPISODE":
    case "POST_EPISODE":
      return "RUNNING";
    case "PAUSED":
    case "INITIALIZED":
      return "PAUSED";
    case "ENDED":
      return "ENDED";
    default:
      return "NOT_INITIALIZED";
  }
}

// GET — read simulation state from SCOP playground + allDone
export async function GET() {
  try {
    const [pgRes, allDoneRes] = await Promise.all([
      fetch(`${BACKEND}/api/scop/playground`),
      fetch(`${BACKEND}/api/customers/allDone`),
    ]);

    if (!pgRes.ok) {
      console.log("[SIM] ✗ SCOP playground not reachable (status %d)", pgRes.status);
      return NextResponse.json(
        { state: "NOT_INITIALIZED", message: "Backend not reachable", processAlive: false },
        { status: 502 }
      );
    }

    const pgData = await pgRes.json();
    const allDoneData = allDoneRes.ok ? await allDoneRes.json() : { allDone: false };
    const allDone: boolean = allDoneData.allDone === true;

    const scopState: string = pgData.executionState || pgData.state || "NOT_INIT";
    const state = allDone ? "ENDED" : mapState(scopState);
    const processAlive = state === "RUNNING";
    const currentTick: number = pgData.tick ?? pgData.currentTick ?? 0;

    console.log("[SIM] ← SCOP state=%s → mapped=%s, tick=%d, allDone=%s", scopState, state, currentTick, allDone);

    return NextResponse.json({
      state,
      message: allDone ? "All customers finished" : "",
      currentTick,
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

// POST — send action to SCOP playground
export async function POST(req: Request) {
  const body = await req.json();
  const action: string = body.action;
  console.log("[SIM] → POST /api/scop/playground?action=%s", action);

  try {
    // Reset playground before setup so SCOP creates a fresh instance
    if (action === "setup") {
      await fetch(`${BACKEND}/api/playground/reset`, { method: "POST" }).catch(() => {});
    }

    const res = await fetch(`${BACKEND}/api/scop/playground?action=${action}`, {
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

    const pgData = await res.json();
    const scopState: string = pgData.executionState || pgData.state || "NOT_INIT";
    const state = mapState(scopState);
    const currentTick: number = pgData.tick ?? pgData.currentTick ?? 0;
    const processAlive = state === "RUNNING";

    console.log("[SIM] ← POST response: scopState=%s → %s, tick=%d", scopState, state, currentTick);

    return NextResponse.json({
      state,
      message: "",
      currentTick,
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

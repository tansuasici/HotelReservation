import { NextResponse } from "next/server";
import { readFile, writeFile, unlink } from "fs/promises";
import { join } from "path";
import { spawn, type ChildProcess } from "child_process";

export const dynamic = "force-dynamic";

const PROJECT_ROOT = join(process.cwd(), "..");
const OUTPUT_DIR = join(PROJECT_ROOT, "output-data");
const STATE_FILE = join(OUTPUT_DIR, ".sim-state");
const COMMAND_FILE = join(OUTPUT_DIR, ".sim-command");

// Module-level process reference (persists across requests in dev)
let simProcess: ChildProcess | null = null;

async function readState(): Promise<{ state: string; message: string }> {
  try {
    const content = await readFile(STATE_FILE, "utf-8");
    return JSON.parse(content);
  } catch {
    return { state: "NOT_INITIALIZED", message: "No simulation running" };
  }
}

async function sendCommand(cmd: string) {
  await writeFile(COMMAND_FILE, cmd, "utf-8");
}

// GET — read simulation state
export async function GET() {
  const state = await readState();
  return NextResponse.json(state);
}

// POST — send action to simulation
export async function POST(req: Request) {
  const body = await req.json();
  const action: string = body.action;

  if (action === "setup") {
    // Kill any existing process
    if (simProcess && !simProcess.killed) {
      simProcess.kill("SIGTERM");
      simProcess = null;
    }

    // Clean up old state/command/data files
    try { await unlink(STATE_FILE); } catch { /* ignore */ }
    try { await unlink(COMMAND_FILE); } catch { /* ignore */ }
    // Remove old simulation data so stale results aren't served
    for (const f of ["topology.json", "hotels.json", "customers.json", "activity.json", "agents.json"]) {
      try { await unlink(join(OUTPUT_DIR, f)); } catch { /* ignore */ }
    }

    // Spawn the headless simulation runner
    simProcess = spawn("mvn", [
      "exec:java",
      "-Dexec.mainClass=hotel.reservation.cli.SimulationRunner",
      "-q",
    ], {
      cwd: PROJECT_ROOT,
      stdio: ["ignore", "pipe", "pipe"],
      detached: false,
    });

    simProcess.on("exit", () => {
      simProcess = null;
    });

    // Pipe stdout/stderr to console for debugging
    simProcess.stdout?.on("data", (data: Buffer) => {
      process.stdout.write(`[SIM] ${data}`);
    });
    simProcess.stderr?.on("data", (data: Buffer) => {
      // Filter Maven noise
      const line = data.toString();
      if (!line.includes("[WARNING]") && !line.includes("Download")) {
        process.stderr.write(`[SIM] ${data}`);
      }
    });

    // Wait for state to become PAUSED (setup done), timeout 60s
    const start = Date.now();
    while (Date.now() - start < 60000) {
      await new Promise((r) => setTimeout(r, 500));
      const state = await readState();
      if (state.state === "PAUSED") {
        return NextResponse.json(state);
      }
      if (state.state === "ENDED") {
        return NextResponse.json(state);
      }
      // Check if process died
      if (simProcess === null) {
        return NextResponse.json(
          { state: "NOT_INITIALIZED", message: "Process exited unexpectedly" },
          { status: 500 }
        );
      }
    }

    return NextResponse.json(
      { state: "NOT_INITIALIZED", message: "Setup timeout" },
      { status: 504 }
    );
  }

  if (action === "run" || action === "pause" || action === "stop") {
    await sendCommand(action);

    // Wait briefly for state change
    await new Promise((r) => setTimeout(r, 800));
    const state = await readState();
    return NextResponse.json(state);
  }

  return NextResponse.json({ error: "Unknown action" }, { status: 400 });
}

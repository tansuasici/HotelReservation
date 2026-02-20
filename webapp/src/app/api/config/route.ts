import { NextResponse } from "next/server";

export const dynamic = "force-dynamic";

const BACKEND = process.env.BACKEND_URL || "http://localhost:3001";

// GET — read SCOP playground config
export async function GET() {
  try {
    const res = await fetch(`${BACKEND}/api/scop/playground/config`);
    if (!res.ok) {
      return NextResponse.json({ error: "Cannot read config" }, { status: res.status });
    }
    const data = await res.json();
    return NextResponse.json(data);
  } catch (e) {
    return NextResponse.json(
      { error: "Cannot connect to backend: " + (e as Error).message },
      { status: 502 }
    );
  }
}

// POST — update SCOP playground config
export async function POST(req: Request) {
  try {
    const body = await req.json();
    const res = await fetch(`${BACKEND}/api/scop/playground/config`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    if (!res.ok) {
      const text = await res.text();
      return NextResponse.json({ error: text }, { status: res.status });
    }
    const data = await res.json();
    return NextResponse.json(data);
  } catch (e) {
    return NextResponse.json(
      { error: "Cannot connect to backend: " + (e as Error).message },
      { status: 502 }
    );
  }
}

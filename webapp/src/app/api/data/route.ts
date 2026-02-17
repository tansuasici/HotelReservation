import { NextResponse } from "next/server";
import { readFile } from "fs/promises";
import { join } from "path";

export const dynamic = "force-dynamic";

const DATA_DIR = join(process.cwd(), "..", "output-data");

async function readJson(filename: string) {
  try {
    const content = await readFile(join(DATA_DIR, filename), "utf-8");
    return JSON.parse(content);
  } catch {
    return null;
  }
}

export async function GET() {
  const [topology, hotels, customers, activity, agents] = await Promise.all([
    readJson("topology.json"),
    readJson("hotels.json"),
    readJson("customers.json"),
    readJson("activity.json"),
    readJson("agents.json"),
  ]);

  if (!topology) {
    return NextResponse.json({ error: "No simulation data found. Run the simulation first." }, { status: 404 });
  }

  return NextResponse.json({ topology, hotels, customers, activity, agents });
}

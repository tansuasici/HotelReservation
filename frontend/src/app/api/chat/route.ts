import { NextResponse } from "next/server";
import { readFile } from "fs/promises";
import { join } from "path";

const DATA_DIR = join(process.cwd(), "..", "output-data");

async function readJson(filename: string) {
  try {
    const content = await readFile(join(DATA_DIR, filename), "utf-8");
    return JSON.parse(content);
  } catch {
    return null;
  }
}

async function readLog(agentId: string): Promise<string> {
  try {
    const logPath = join(DATA_DIR, "log", `${agentId}.log`);
    return await readFile(logPath, "utf-8");
  } catch {
    return "";
  }
}

export async function POST(request: Request) {
  const { agentId, message } = await request.json();

  if (!agentId || !message) {
    return NextResponse.json({ error: "agentId and message are required" }, { status: 400 });
  }

  // Read agent metadata
  const agents = await readJson("agents.json");
  const agentMeta = agents?.find((a: { name: string }) => a.name === agentId);

  // Read agent log
  const log = await readLog(agentId);

  // Build system prompt
  const systemPrompt = [
    `You are ${agentMeta?.displayName || agentId}, a ${agentMeta?.type || "agent"} in a hotel reservation multi-agent system.`,
    agentMeta?.type === "HotelAgent"
      ? `You represent a hotel and handle room reservations, pricing negotiations, and customer inquiries.`
      : `You are a customer agent that searches for hotels, evaluates proposals, and negotiates prices.`,
    "",
    "Your activity history from the simulation:",
    log || "(No activity recorded)",
  ].join("\n");

  try {
    // Call Ollama
    const ollamaRes = await fetch("http://localhost:11434/api/chat", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        model: "llama3.2",
        messages: [
          { role: "system", content: systemPrompt },
          { role: "user", content: message },
        ],
        stream: false,
      }),
    });

    if (!ollamaRes.ok) {
      const errText = await ollamaRes.text();
      return NextResponse.json(
        { error: `Ollama error: ${errText}` },
        { status: 502 }
      );
    }

    const data = await ollamaRes.json();
    return NextResponse.json({
      agentId,
      response: data.message?.content || "No response",
    });
  } catch (e) {
    return NextResponse.json(
      { error: `Failed to connect to Ollama: ${e instanceof Error ? e.message : "Unknown error"}` },
      { status: 502 }
    );
  }
}

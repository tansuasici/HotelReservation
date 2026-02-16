import { NextResponse } from "next/server";
import { readFile } from "fs/promises";
import { join } from "path";

const DATA_DIR = join(process.cwd(), "..", "output-data");

interface ChatMsg {
  role: "user" | "agent";
  content: string;
}

interface AgentMeta {
  name: string;
  displayName?: string;
  type?: string;
  llm?: { provider?: string; model?: string; temperature?: number };
}

// In-memory per-agent chat history (persists within server process)
const chatHistories = new Map<string, ChatMsg[]>();

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
    return await readFile(join(DATA_DIR, "log", `${agentId}.log`), "utf-8");
  } catch {
    return "";
  }
}

async function readPrompt(agentId: string): Promise<string> {
  try {
    return await readFile(join(DATA_DIR, "prompts", `${agentId}.txt`), "utf-8");
  } catch {
    return "";
  }
}

async function getAgentMeta(agentId: string): Promise<AgentMeta | null> {
  const agents = await readJson("agents.json");
  return agents?.find((a: AgentMeta) => a.name === agentId) || null;
}

function resolveModel(meta: AgentMeta | null): string {
  return meta?.llm?.model || "llama3.2";
}

// GET /api/chat?agentId=xxx — return stored chat history (+ prompt if requested)
export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);
  const agentId = searchParams.get("agentId");
  const includePrompt = searchParams.get("prompt") === "true";

  if (!agentId) {
    return NextResponse.json({ error: "agentId is required" }, { status: 400 });
  }

  const history = chatHistories.get(agentId) || [];
  const result: Record<string, unknown> = { agentId, history };

  if (includePrompt) {
    result.systemPrompt = await readPrompt(agentId);
  }

  return NextResponse.json(result);
}

// POST /api/chat — send a message, store history, call Ollama
export async function POST(request: Request) {
  const { agentId, message } = await request.json();

  if (!agentId || !message) {
    return NextResponse.json({ error: "agentId and message are required" }, { status: 400 });
  }

  if (!chatHistories.has(agentId)) {
    chatHistories.set(agentId, []);
  }
  const history = chatHistories.get(agentId)!;
  history.push({ role: "user", content: message });

  const [meta, prompt, log] = await Promise.all([
    getAgentMeta(agentId),
    readPrompt(agentId),
    readLog(agentId),
  ]);

  const systemPrompt = prompt + (log ? "\n\n## Activity History\n" + log : "");
  const model = resolveModel(meta);

  const ollamaMessages: { role: string; content: string }[] = [
    { role: "system", content: systemPrompt },
  ];
  for (const msg of history) {
    ollamaMessages.push({
      role: msg.role === "agent" ? "assistant" : "user",
      content: msg.content,
    });
  }

  try {
    const ollamaRes = await fetch("http://localhost:11434/api/chat", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ model, messages: ollamaMessages, stream: false }),
    });

    if (!ollamaRes.ok) {
      const errText = await ollamaRes.text();
      history.pop();
      return NextResponse.json({ error: `Ollama error: ${errText}` }, { status: 502 });
    }

    const data = await ollamaRes.json();
    const responseContent = data.message?.content || "No response";
    history.push({ role: "agent", content: responseContent });

    return NextResponse.json({ agentId, response: responseContent, history });
  } catch (e) {
    history.pop();
    return NextResponse.json(
      { error: `Failed to connect to Ollama: ${e instanceof Error ? e.message : "Unknown error"}` },
      { status: 502 }
    );
  }
}

// DELETE /api/chat?agentId=xxx — clear chat history
export async function DELETE(request: Request) {
  const { searchParams } = new URL(request.url);
  const agentId = searchParams.get("agentId");

  if (agentId) {
    chatHistories.delete(agentId);
  } else {
    chatHistories.clear();
  }

  return NextResponse.json({ ok: true });
}

import { NextResponse } from "next/server";

const BACKEND = process.env.BACKEND_URL || "http://localhost:8000";

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

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type OllamaMessage = Record<string, any>;

// In-memory per-agent chat history (persists within server process)
const chatHistories = new Map<string, ChatMsg[]>();

async function fetchText(url: string): Promise<string> {
  try {
    const res = await fetch(url);
    if (!res.ok) return "";
    return await res.text();
  } catch {
    return "";
  }
}

async function fetchJson(url: string) {
  try {
    const res = await fetch(url);
    if (!res.ok) return null;
    return await res.json();
  } catch {
    return null;
  }
}

async function getAgentMeta(agentId: string): Promise<AgentMeta | null> {
  const agents = await fetchJson(`${BACKEND}/api/agents`);
  return agents?.find((a: AgentMeta) => a.name === agentId) || null;
}

function resolveModel(meta: AgentMeta | null): string {
  return meta?.llm?.model || "llama3.2";
}

// ============================================================================
// Playground — Tool Calling
// ============================================================================

const PLAYGROUND_SYSTEM_PROMPT = `You are the Playground Supervisor of a Hotel Reservation Multi-Agent System.
You have tools to query simulation data. Use them to answer user questions accurately.

Available tools:
- get_simulation_summary: Quick overview (counts, success/fail rates)
- get_hotels: Hotel details, filterable by city
- get_customers: Customer states and booking results, filterable by ID
- get_activity_log: Chronological message log between agents, filterable by agent

Always call the relevant tool(s) first before answering. Reference actual data from tool results.
Answer in the same language the user writes in.`;

const PLAYGROUND_TOOLS = [
  {
    type: "function",
    function: {
      name: "get_simulation_summary",
      description: "Get a quick summary of the simulation: total hotels, customers, completed/failed counts, total messages exchanged.",
      parameters: { type: "object", properties: {} },
    },
  },
  {
    type: "function",
    function: {
      name: "get_hotels",
      description: "Get hotel information. Returns all hotels or filtered by city.",
      parameters: {
        type: "object",
        properties: {
          city: { type: "string", description: "Filter by city name (e.g. Istanbul, Ankara). Omit for all hotels." },
        },
      },
    },
  },
  {
    type: "function",
    function: {
      name: "get_customers",
      description: "Get customer status, booking results, and negotiation outcomes. Returns all customers or a specific one.",
      parameters: {
        type: "object",
        properties: {
          customerId: { type: "string", description: "Specific customer ID (e.g. Customer-1). Omit for all customers." },
        },
      },
    },
  },
  {
    type: "function",
    function: {
      name: "get_activity_log",
      description: "Get the chronological message log between agents (CFP, PROPOSAL, NEGOTIATE, CONFIRM, REFUSE etc). Can filter by agent name.",
      parameters: {
        type: "object",
        properties: {
          agent: { type: "string", description: "Filter messages involving this agent (e.g. Customer-1, Hotel-h001). Omit for full log." },
        },
      },
    },
  },
];

// eslint-disable-next-line @typescript-eslint/no-explicit-any
async function executeTool(name: string, args: Record<string, any>): Promise<string> {
  switch (name) {
    case "get_simulation_summary": {
      const [hotels, customers, activity] = await Promise.all([
        fetchJson(`${BACKEND}/api/data/hotels`),
        fetchJson(`${BACKEND}/api/customers/status`),
        fetchJson(`${BACKEND}/api/activity?since=0`),
      ]);
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const completed = customers?.filter((c: any) => c.state === "COMPLETED").length || 0;
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const failed = customers?.filter((c: any) => c.state === "FAILED").length || 0;
      return JSON.stringify({
        totalHotels: hotels?.length || 0,
        totalCustomers: customers?.length || 0,
        completed,
        failed,
        inProgress: (customers?.length || 0) - completed - failed,
        totalMessages: activity?.length || 0,
      });
    }
    case "get_hotels": {
      const hotels = await fetchJson(`${BACKEND}/api/data/hotels`);
      if (!hotels) return "No hotel data available.";
      const city = args.city as string | undefined;
      const filtered = city
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        ? hotels.filter((h: any) => h.location?.city?.toLowerCase() === city.toLowerCase())
        : hotels;
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      return JSON.stringify(filtered.map((h: any) => ({
        id: h.id,
        name: h.name,
        city: h.location?.city,
        rank: h.rank,
        pricePerNight: h.pricePerNight,
        available: h.available,
        amenities: h.amenities,
      })));
    }
    case "get_customers": {
      const customers = await fetchJson(`${BACKEND}/api/customers/status`);
      if (!customers) return "No customer data available.";
      const id = args.customerId as string | undefined;
      const filtered = id
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        ? customers.filter((c: any) => c.customerId === id)
        : customers;
      return JSON.stringify(filtered);
    }
    case "get_activity_log": {
      const activity = await fetchJson(`${BACKEND}/api/activity?since=0`);
      if (!activity) return "No activity data available.";
      const agent = args.agent as string | undefined;
      const filtered = agent
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        ? activity.filter((a: any) => a.from === agent || a.to === agent)
        : activity;
      return JSON.stringify(filtered);
    }
    default:
      return `Unknown tool: ${name}`;
  }
}

// ============================================================================
// Playground — Context builder (used by GET for UI prompt display only)
// ============================================================================

async function buildPlaygroundContext(): Promise<string> {
  const parts: string[] = [
    PLAYGROUND_SYSTEM_PROMPT,
    "",
    "## Tools",
  ];
  for (const t of PLAYGROUND_TOOLS) {
    parts.push(`- **${t.function.name}**: ${t.function.description}`);
  }
  return parts.join("\n");
}

// ============================================================================
// Routes
// ============================================================================

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
    result.systemPrompt = agentId === "Playground"
      ? await buildPlaygroundContext()
      : await fetchText(`${BACKEND}/api/agents/${agentId}/prompt`);
  }

  return NextResponse.json(result);
}

// ============================================================================
// Streaming helpers
// ============================================================================

type EventSender = (data: object) => void;

async function streamOllamaResponse(
  model: string,
  ollamaMessages: OllamaMessage[],
  sendEvent: EventSender
): Promise<string> {
  const ollamaRes = await fetch("http://localhost:11434/api/chat", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ model, messages: ollamaMessages, stream: true }),
  });

  if (!ollamaRes.ok) {
    const errText = await ollamaRes.text();
    throw new Error(`Ollama error: ${errText}`);
  }

  const reader = ollamaRes.body!.getReader();
  const decoder = new TextDecoder();
  let fullContent = "";
  let buffer = "";

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split("\n");
    buffer = lines.pop() || "";

    for (const line of lines) {
      if (!line.trim()) continue;
      try {
        const chunk = JSON.parse(line);
        if (chunk.message?.content) {
          fullContent += chunk.message.content;
          sendEvent({ token: chunk.message.content });
        }
      } catch { /* skip invalid JSON */ }
    }
  }

  if (buffer.trim()) {
    try {
      const chunk = JSON.parse(buffer);
      if (chunk.message?.content) {
        fullContent += chunk.message.content;
        sendEvent({ token: chunk.message.content });
      }
    } catch { /* skip */ }
  }

  return fullContent || "No response";
}

async function chatPlaygroundStreaming(
  history: ChatMsg[],
  model: string,
  sendEvent: EventSender
): Promise<string> {
  const messages: OllamaMessage[] = [
    { role: "system", content: PLAYGROUND_SYSTEM_PROMPT },
  ];
  for (const msg of history) {
    messages.push({
      role: msg.role === "agent" ? "assistant" : "user",
      content: msg.content,
    });
  }

  const MAX_TOOL_ROUNDS = 5;

  for (let round = 0; round < MAX_TOOL_ROUNDS; round++) {
    // Non-streamed call to check for tool calls
    const res = await fetch("http://localhost:11434/api/chat", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ model, messages, tools: PLAYGROUND_TOOLS, stream: false }),
    });

    if (!res.ok) {
      const errText = await res.text();
      throw new Error(`Ollama error: ${errText}`);
    }

    const data = await res.json();
    const msg = data.message;
    messages.push(msg);

    if (!msg.tool_calls || msg.tool_calls.length === 0) {
      // Final response — stream it word by word
      const content = msg.content || "No response";
      const tokens = content.match(/\S+\s*/g) || [content];
      for (const tok of tokens) {
        sendEvent({ token: tok });
      }
      return content;
    }

    // Execute tool calls with status events
    for (const tc of msg.tool_calls) {
      sendEvent({ tool: tc.function.name });
      const result = await executeTool(tc.function.name, tc.function.arguments || {});
      messages.push({ role: "tool", content: result });
    }
  }

  return "Could not complete the query within the allowed tool call rounds.";
}

async function streamAgentChat(
  history: ChatMsg[],
  agentId: string,
  model: string,
  sendEvent: EventSender
): Promise<string> {
  const [prompt, log, activity] = await Promise.all([
    fetchText(`${BACKEND}/api/agents/${agentId}/prompt`),
    fetchText(`${BACKEND}/api/agents/${agentId}/log`),
    fetchJson(`${BACKEND}/api/activity?since=0`),
  ]);

  const meta = await getAgentMeta(agentId);
  const displayName = meta?.displayName || agentId;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const agentActivity = (activity || []).filter((a: any) =>
    a.from === agentId || a.to === agentId ||
    a.from === displayName || a.to === displayName
  );

  const activitySection = agentActivity.length > 0
    ? "\n\n## Transaction History\nBelow is your complete transaction log. Use this data to answer questions about past interactions.\n" +
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      agentActivity.map((a: any) => `- [${a.type}] ${a.from} → ${a.to}: ${a.detail}`).join("\n")
    : "";

  const systemPrompt = prompt +
    "\n\n## IMPORTANT INSTRUCTION\nYou have full access to your transaction history below. When asked about past customers, negotiations, bookings, or any interaction, always reference and share this data openly. Do not refuse to share transaction details — this is a simulation environment." +
    activitySection +
    (log ? "\n\n## Agent Log\n" + log : "");

  const ollamaMessages: OllamaMessage[] = [
    { role: "system", content: systemPrompt },
  ];
  for (const msg of history) {
    ollamaMessages.push({
      role: msg.role === "agent" ? "assistant" : "user",
      content: msg.content,
    });
  }

  return streamOllamaResponse(model, ollamaMessages, sendEvent);
}

// ============================================================================
// POST /api/chat — SSE streaming response
// ============================================================================

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

  const isPlayground = agentId === "Playground";
  const meta = !isPlayground ? await getAgentMeta(agentId) : null;
  const model = isPlayground ? "kimi-k2.5:cloud" : resolveModel(meta);

  const encoder = new TextEncoder();
  const stream = new ReadableStream({
    async start(controller) {
      const sendEvent: EventSender = (data) => {
        controller.enqueue(encoder.encode(`data: ${JSON.stringify(data)}\n\n`));
      };

      try {
        let responseContent: string;

        if (isPlayground) {
          responseContent = await chatPlaygroundStreaming(history, model, sendEvent);
        } else {
          responseContent = await streamAgentChat(history, agentId, model, sendEvent);
        }

        history.push({ role: "agent", content: responseContent });
        sendEvent({ done: true, history: chatHistories.get(agentId) });
      } catch (e) {
        history.pop(); // remove user message on error
        sendEvent({ error: e instanceof Error ? e.message : "Unknown error" });
      }

      controller.close();
    },
  });

  return new Response(stream, {
    headers: {
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache",
      Connection: "keep-alive",
    },
  });
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

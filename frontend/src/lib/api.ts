import type { ChatMessage } from "@/lib/types";

export async function chatWithAgent(
  agentId: string,
  message: string
): Promise<{ agentId: string; response: string; history: ChatMessage[] }> {
  const res = await fetch("/api/chat", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ agentId, message }),
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.error || `API ${res.status}`);
  }
  return res.json();
}

/** SSE streaming chat — calls onToken for each text chunk, onTool for tool calls, onDone with final history */
export async function streamChatWithAgent(
  agentId: string,
  message: string,
  callbacks: {
    onToken: (token: string) => void;
    onTool?: (toolName: string) => void;
    onDone: (history: ChatMessage[]) => void;
    onError: (error: string) => void;
  }
): Promise<void> {
  const res = await fetch("/api/chat", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ agentId, message }),
  });

  if (!res.ok || !res.body) {
    const body = await res.json().catch(() => ({}));
    callbacks.onError(body.error || `API ${res.status}`);
    return;
  }

  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });
    const parts = buffer.split("\n\n");
    buffer = parts.pop() || "";

    for (const part of parts) {
      const line = part.trim();
      if (!line.startsWith("data: ")) continue;
      try {
        const data = JSON.parse(line.slice(6));
        if (data.token) callbacks.onToken(data.token);
        if (data.tool) callbacks.onTool?.(data.tool);
        if (data.done) callbacks.onDone(data.history);
        if (data.error) callbacks.onError(data.error);
      } catch { /* skip invalid */ }
    }
  }

  // Process remaining buffer
  if (buffer.trim()) {
    for (const part of buffer.split("\n\n")) {
      const line = part.trim();
      if (!line.startsWith("data: ")) continue;
      try {
        const data = JSON.parse(line.slice(6));
        if (data.token) callbacks.onToken(data.token);
        if (data.tool) callbacks.onTool?.(data.tool);
        if (data.done) callbacks.onDone(data.history);
        if (data.error) callbacks.onError(data.error);
      } catch { /* skip */ }
    }
  }
}

export async function getChatHistory(
  agentId: string
): Promise<ChatMessage[]> {
  const res = await fetch(`/api/chat?agentId=${encodeURIComponent(agentId)}`);
  if (!res.ok) return [];
  const data = await res.json();
  return data.history || [];
}

export async function getAgentPrompt(agentId: string): Promise<string> {
  const res = await fetch(`/api/chat?agentId=${encodeURIComponent(agentId)}&prompt=true`);
  if (!res.ok) return "";
  const data = await res.json();
  return data.systemPrompt || "";
}

export async function getAgentLog(agentId: string): Promise<string> {
  const res = await fetch(`/api/logs/${encodeURIComponent(agentId)}`);
  if (!res.ok) return "";
  const data = await res.json();
  return data.log || "";
}

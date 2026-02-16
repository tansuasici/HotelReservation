export async function chatWithAgent(
  agentId: string,
  message: string
): Promise<{ agentId: string; response: string }> {
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

export async function getAgentLog(agentId: string): Promise<string> {
  const res = await fetch(`/api/logs/${encodeURIComponent(agentId)}`);
  if (!res.ok) return "";
  const data = await res.json();
  return data.log || "";
}

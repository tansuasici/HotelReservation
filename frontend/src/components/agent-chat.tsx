"use client";

import { useState, useRef, useEffect, useCallback, useMemo } from "react";
import { User, Building2, Send, Loader2, MessageCircle, FileText, Terminal, ChevronDown, ChevronRight, LayoutGrid } from "lucide-react";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { streamChatWithAgent, getChatHistory, getAgentLog, getAgentPrompt } from "@/lib/api";
import { MarkdownMessage } from "@/components/markdown-message";
import type {
  TopologyNode,
  Topology,
  ChatMessage,
} from "@/lib/types";
import { CITY_COLORS, DEFAULT_CITY_COLOR } from "@/lib/types";

const PLAYGROUND_NODE: TopologyNode = {
  name: "Playground",
  displayName: "Playground",
  type: "NetworkEnvironment",
};

interface Props {
  agent: TopologyNode | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  topology: Topology | null;
}

export function AgentChat({ agent: externalAgent, open, onOpenChange, topology }: Props) {
  const [selectedAgent, setSelectedAgent] = useState<TopologyNode | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const [streamingTool, setStreamingTool] = useState<string | null>(null);
  const [agentLog, setAgentLog] = useState<string>("");
  const [loadingHistory, setLoadingHistory] = useState(false);
  const [systemPrompt, setSystemPrompt] = useState<string>("");
  const [promptOpen, setPromptOpen] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const prevAgentRef = useRef<string | null>(null);

  // Build agent list: Playground + Hotels + Customers
  const agentList = useMemo(() => {
    if (!topology) return [PLAYGROUND_NODE];
    const agents = topology.nodes.filter(
      (n) => n.type === "HotelAgent" || n.type === "CustomerAgent"
    );
    return [PLAYGROUND_NODE, ...agents];
  }, [topology]);

  // When external agent changes (clicked from graph), switch to it
  useEffect(() => {
    if (externalAgent && open) {
      setSelectedAgent(externalAgent);
    }
  }, [externalAgent, open]);

  const agent = selectedAgent;

  // When agent changes: fetch chat history and log from backend
  useEffect(() => {
    if (!agent) return;
    if (agent.name === prevAgentRef.current) return;

    prevAgentRef.current = agent.name;
    setLoadingHistory(true);
    setSystemPrompt("");
    setPromptOpen(false);

    // Fetch history, log, and prompt in parallel
    Promise.all([
      getChatHistory(agent.name),
      agent.name === "Playground" ? Promise.resolve("") : getAgentLog(agent.name),
      getAgentPrompt(agent.name),
    ])
      .then(([history, log, prompt]) => {
        setMessages(history);
        setAgentLog(log);
        setSystemPrompt(prompt);
      })
      .catch(() => {
        setMessages([]);
        setAgentLog("");
        setSystemPrompt("");
      })
      .finally(() => {
        setLoadingHistory(false);
        // Auto-send forwarded message from @mention switch
        if (pendingMsgRef.current) {
          const fwd = pendingMsgRef.current;
          pendingMsgRef.current = null;
          setInput(fwd);
          // Trigger send after input is set
          setTimeout(() => inputRef.current?.focus(), 100);
        }
      });
  }, [agent]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  useEffect(() => {
    if (open) {
      setTimeout(() => inputRef.current?.focus(), 200);
    }
  }, [open]);

  // @mention parser — matches @AgentName or @"Display Name" (inspired by SCOP MentionParser)
  const parseMention = useCallback((msg: string): { target: TopologyNode; rest: string } | null => {
    // Pattern: @Customer-10, @Hotel-h001, @Playground, @"Sea View Resort"
    const match = msg.match(/^@(?:"([^"]+)"|(\S+))\s*(.*)?$/i);
    if (!match) return null;

    const mentionRaw = match[1] || match[2]; // quoted or unquoted
    const rest = (match[3] || "").trim();
    const mentionNorm = mentionRaw.toLowerCase().replace(/[\s-]+/g, "");

    for (const a of agentList) {
      const nameNorm = a.name.toLowerCase().replace(/[\s-]+/g, "");
      if (nameNorm === mentionNorm) return { target: a, rest };
      if (a.displayName) {
        const dispNorm = a.displayName.toLowerCase().replace(/[\s-]+/g, "");
        if (dispNorm === mentionNorm) return { target: a, rest };
      }
    }
    return null;
  }, [agentList]);

  const pendingMsgRef = useRef<string | null>(null);

  const send = useCallback(async () => {
    if (!agent || !input.trim() || sending) return;
    const msg = input.trim();
    setInput("");

    // @mention dispatch — switch agent and forward remaining message
    const mention = parseMention(msg);
    if (mention) {
      const { target, rest } = mention;
      setMessages((prev) => [
        ...prev,
        { role: "user", content: msg },
        { role: "agent", content: `Switching to **${target.displayName || target.name}**...` },
      ]);
      pendingMsgRef.current = rest || null; // forward remaining text
      setTimeout(() => {
        prevAgentRef.current = null;
        setSelectedAgent(target);
      }, 400);
      return;
    }

    // Optimistic: show user message + empty agent bubble to stream into
    setMessages((prev) => [
      ...prev,
      { role: "user", content: msg },
      { role: "agent", content: "" },
    ]);
    setSending(true);
    setStreamingTool(null);

    try {
      await streamChatWithAgent(agent.name, msg, {
        onToken: (token) => {
          setStreamingTool(null);
          setMessages((prev) => {
            const updated = [...prev];
            const last = updated[updated.length - 1];
            if (last?.role === "agent") {
              updated[updated.length - 1] = { ...last, content: last.content + token };
            }
            return updated;
          });
        },
        onTool: (toolName) => {
          setStreamingTool(toolName);
        },
        onDone: (history) => {
          setMessages(history);
        },
        onError: (error) => {
          setMessages((prev) => {
            const updated = [...prev];
            const last = updated[updated.length - 1];
            if (last?.role === "agent") {
              updated[updated.length - 1] = { role: "agent", content: "Error: " + error };
            }
            return updated;
          });
        },
      });
    } catch (e) {
      setMessages((prev) => {
        const updated = [...prev];
        updated[updated.length - 1] = {
          role: "agent",
          content: "Error: " + (e instanceof Error ? e.message : "Unknown error"),
        };
        return updated;
      });
    } finally {
      setSending(false);
      setStreamingTool(null);
      inputRef.current?.focus();
    }
  }, [agent, input, sending, parseMention]);

  // Handle select change
  const handleAgentChange = useCallback((e: React.ChangeEvent<HTMLSelectElement>) => {
    const name = e.target.value;
    const found = agentList.find((a) => a.name === name);
    if (found) {
      prevAgentRef.current = null; // force reload
      setSelectedAgent(found);
    }
  }, [agentList]);

  if (!agent) return null;

  const isPlayground = agent.name === "Playground";
  const isCustomer = agent.type === "CustomerAgent";
  const isHotel = agent.type === "HotelAgent";
  const cityColor = CITY_COLORS[agent.location || ""] || DEFAULT_CITY_COLOR;

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="flex w-[400px] flex-col gap-0 p-0 sm:max-w-[400px] border-l border-border bg-white dark:bg-slate-900">
        {/* Agent Selector — right padding leaves space for Sheet close button */}
        <div className="shrink-0 px-4 pr-12 pt-4 pb-2">
          <select
            value={agent.name}
            onChange={handleAgentChange}
            className="w-full h-9 rounded-lg border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 px-3 text-sm font-medium text-foreground focus:outline-none focus:ring-2 focus:ring-indigo-500/30 cursor-pointer appearance-none"
            style={{ backgroundImage: `url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='%2394a3b8' stroke-width='2'%3E%3Cpath d='m6 9 6 6 6-6'/%3E%3C/svg%3E")`, backgroundRepeat: "no-repeat", backgroundPosition: "right 10px center" }}
          >
            {agentList.map((a) => (
              <option key={a.name} value={a.name}>
                {a.name === "Playground"
                  ? "Playground (Supervisor)"
                  : `${a.displayName || a.name} — ${a.type === "HotelAgent" ? "Hotel" : "Customer"}`}
              </option>
            ))}
          </select>
        </div>

        {/* Agent header */}
        <SheetHeader className="px-5 py-3 shrink-0">
          <SheetTitle className="flex items-center gap-2.5 text-sm">
            <div
              className="flex h-8 w-8 items-center justify-center rounded-lg"
              style={{
                background: isPlayground
                  ? "rgba(99, 102, 241, 0.12)"
                  : isCustomer
                    ? "rgba(245, 158, 11, 0.12)"
                    : `${cityColor}18`,
              }}
            >
              {isPlayground ? (
                <LayoutGrid className="h-4 w-4 text-indigo-500" />
              ) : isCustomer ? (
                <User className="h-4 w-4 text-amber-600" />
              ) : (
                <Building2 className="h-4 w-4" style={{ color: cityColor }} />
              )}
            </div>
            <div>
              <div className="text-sm font-semibold">
                {agent.displayName || agent.name}
              </div>
              <div className="text-[10px] text-muted-foreground font-normal">
                {isPlayground
                  ? "System Supervisor"
                  : isHotel ? "Hotel Agent" : "Customer Agent"}
                {isPlayground ? (
                  <span> &middot; kimi-k2.5:cloud</span>
                ) : agent.model ? (
                  <span> &middot; {agent.model}</span>
                ) : null}
              </div>
            </div>
          </SheetTitle>
        </SheetHeader>

        <div className="section-divider" />

        {/* Agent Info */}
        <div className="shrink-0 px-5 py-3 space-y-2">
          {isPlayground && (
            <div className="rounded-lg bg-indigo-50 dark:bg-indigo-950/30 p-3 space-y-1 border border-indigo-200/50 dark:border-indigo-800/50">
              <div className="text-xs text-indigo-700 dark:text-indigo-300 font-medium">
                Full system visibility
              </div>
              <div className="text-[10px] text-indigo-600/70 dark:text-indigo-400/70">
                Ask about any agent, customer state, negotiation, or simulation outcome.
              </div>
            </div>
          )}

          {isHotel && (
            <div className="flex items-center gap-2 flex-wrap text-xs text-muted-foreground">
              <span className="inline-flex items-center gap-1">
                <span className="h-1.5 w-1.5 rounded-full" style={{ background: cityColor }} />
                {agent.location}
              </span>
              <span style={{ color: "#eab308" }}>{"\u2605".repeat(agent.rank || 0)}</span>
              <span className="font-semibold text-emerald-600">${agent.basePrice}/night</span>
              {agent.totalRooms != null && agent.availableRooms != null && (
                <span className={`font-semibold ${agent.availableRooms === 0 ? "text-red-500" : "text-emerald-600"}`}>
                  {agent.availableRooms}/{agent.totalRooms} rooms
                </span>
              )}
            </div>
          )}

          {isCustomer && (
            <div className="flex items-center gap-2 text-xs text-muted-foreground">
              <span style={{ color: "#eab308" }}>{"\u2605".repeat(agent.desiredRank || 0)}</span>
              <span>{agent.location}</span>
              <span className="font-semibold">max ${agent.maxPrice}</span>
            </div>
          )}

          {/* System Prompt */}
          {systemPrompt && (
            <>
              <button
                onClick={() => setPromptOpen((v) => !v)}
                className="w-full flex items-center gap-1.5 rounded-lg bg-slate-50 dark:bg-slate-800 border border-slate-200/60 dark:border-slate-700 px-2.5 py-2 text-left transition-colors hover:bg-slate-100 dark:hover:bg-slate-700/80"
              >
                {promptOpen ? (
                  <ChevronDown className="h-3 w-3 text-muted-foreground" />
                ) : (
                  <ChevronRight className="h-3 w-3 text-muted-foreground" />
                )}
                <Terminal className="h-3 w-3 text-indigo-500" />
                <span className="text-[10px] font-semibold uppercase tracking-widest text-muted-foreground">
                  System Prompt
                </span>
              </button>

              {promptOpen && (
                <div className="rounded-lg bg-slate-900 dark:bg-slate-950 border border-slate-700 p-3 animate-slide-in">
                  <pre className="text-[10px] leading-relaxed text-slate-300 max-h-48 overflow-y-auto custom-scrollbar whitespace-pre-wrap break-words font-mono">
                    {systemPrompt}
                  </pre>
                </div>
              )}
            </>
          )}

          {/* Agent Activity Log */}
          {agentLog && !isPlayground && (
            <div className="rounded-lg bg-slate-50 dark:bg-slate-800 border border-slate-200/60 dark:border-slate-700 p-2.5">
              <div className="flex items-center gap-1.5 mb-1.5 text-[10px] font-semibold uppercase tracking-widest text-muted-foreground">
                <FileText className="h-3 w-3" />
                Activity Log
              </div>
              <pre className="text-[10px] leading-relaxed text-slate-600 dark:text-slate-300 max-h-32 overflow-y-auto custom-scrollbar whitespace-pre-wrap break-words font-mono">
                {agentLog}
              </pre>
            </div>
          )}
        </div>

        <div className="section-divider" />

        {/* Chat Messages */}
        <div className="flex-1 overflow-y-auto px-4 py-3 custom-scrollbar space-y-3">
          {loadingHistory && (
            <div className="flex h-full items-center justify-center animate-fade-in">
              <Loader2 className="h-5 w-5 animate-spin text-muted-foreground/40" />
            </div>
          )}

          {!loadingHistory && messages.length === 0 && (
            <div className="flex h-full items-center justify-center">
              <div className="text-center">
                <MessageCircle className="mx-auto mb-2 h-8 w-8 text-muted-foreground/30" />
                <p className="text-xs text-muted-foreground/40">
                  Send a message to chat with
                </p>
                <p className="text-xs font-semibold text-muted-foreground/60 mt-0.5">
                  {agent.displayName || agent.name}
                </p>
              </div>
            </div>
          )}

          {messages.map((msg, i) => {
            const isStreamingBubble = sending && msg.role === "agent" && i === messages.length - 1;
            const isEmpty = !msg.content;

            return (
              <div
                key={i}
                className={`flex ${
                  msg.role === "user" ? "justify-end" : "justify-start"
                } animate-slide-in`}
              >
                <div
                  className={`max-w-[85%] rounded-xl px-3.5 py-2 text-[13px] leading-relaxed ${
                    msg.role === "user"
                      ? "bg-indigo-600 text-white"
                      : "bg-slate-100 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 text-foreground"
                  }`}
                >
                  {isStreamingBubble && isEmpty ? (
                    <span className="flex items-center gap-2 text-muted-foreground">
                      <Loader2 className="h-3 w-3 animate-spin text-indigo-600" />
                      <span className="text-xs">Thinking...</span>
                    </span>
                  ) : msg.role === "agent" ? (
                    <MarkdownMessage content={msg.content} />
                  ) : (
                    msg.content
                  )}
                </div>
              </div>
            );
          })}

          {sending && streamingTool && (
            <div className="flex justify-start animate-fade-in">
              <div className="flex items-center gap-2 rounded-xl bg-amber-50 dark:bg-amber-950/30 border border-amber-200 dark:border-amber-800 px-3.5 py-1.5 text-[11px] text-amber-700 dark:text-amber-400">
                <Loader2 className="h-3 w-3 animate-spin" />
                <span className="font-mono">{streamingTool}</span>
              </div>
            </div>
          )}

          <div ref={messagesEndRef} />
        </div>

        {/* Chat Input */}
        <div className="shrink-0 border-t border-border/50 p-3">
          <div className="flex gap-2">
            <Input
              ref={inputRef}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter" && !e.shiftKey) {
                  e.preventDefault();
                  send();
                }
              }}
              placeholder="Message or @agent to switch..."
              className="h-9 text-sm bg-white dark:bg-slate-800 border-slate-200 dark:border-slate-700"
            />
            <Button
              size="sm"
              className="btn-press h-9 w-9 shrink-0 p-0 bg-indigo-600 hover:bg-indigo-700 text-white"
              disabled={sending || !input.trim()}
              onClick={send}
            >
              <Send className="h-3.5 w-3.5" />
            </Button>
          </div>
        </div>
      </SheetContent>
    </Sheet>
  );
}

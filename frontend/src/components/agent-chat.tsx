"use client";

import { useState, useRef, useEffect, useCallback } from "react";
import { User, Building2, Send, Loader2, MessageCircle, FileText } from "lucide-react";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { chatWithAgent, getAgentLog } from "@/lib/api";
import type {
  TopologyNode,
  Topology,
  ChatMessage,
} from "@/lib/types";
import { CITY_COLORS, DEFAULT_CITY_COLOR } from "@/lib/types";

interface Props {
  agent: TopologyNode | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  topology: Topology | null;
}

export function AgentChat({ agent, open, onOpenChange, topology }: Props) {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const [agentLog, setAgentLog] = useState<string>("");
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const prevAgentRef = useRef<string | null>(null);

  // Reset chat when agent changes
  useEffect(() => {
    if (agent && agent.name !== prevAgentRef.current) {
      setMessages([]);
      setAgentLog("");
      prevAgentRef.current = agent.name;

      getAgentLog(agent.name)
        .then(setAgentLog)
        .catch(() => setAgentLog(""));
    }
  }, [agent]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  useEffect(() => {
    if (open) {
      setTimeout(() => inputRef.current?.focus(), 200);
    }
  }, [open]);

  const send = useCallback(async () => {
    if (!agent || !input.trim() || sending) return;
    const msg = input.trim();
    setInput("");
    setMessages((prev) => [...prev, { role: "user", content: msg }]);
    setSending(true);

    try {
      const res = await chatWithAgent(agent.name, msg);
      setMessages((prev) => [
        ...prev,
        { role: "agent", content: res.response || "No response" },
      ]);
    } catch (e) {
      setMessages((prev) => [
        ...prev,
        {
          role: "agent",
          content: "Error: " + (e instanceof Error ? e.message : "Unknown error"),
        },
      ]);
    } finally {
      setSending(false);
      inputRef.current?.focus();
    }
  }, [agent, input, sending]);

  if (!agent) return null;

  const isCustomer = agent.type === "CustomerAgent";
  const isHotel = agent.type === "HotelAgent";
  const cityColor = CITY_COLORS[agent.location || ""] || DEFAULT_CITY_COLOR;

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="flex w-[400px] flex-col gap-0 p-0 sm:max-w-[400px] border-l border-border bg-white dark:bg-slate-900">
        {/* Agent header */}
        <SheetHeader className="px-5 py-4 shrink-0">
          <SheetTitle className="flex items-center gap-2.5 text-sm">
            <div
              className="flex h-8 w-8 items-center justify-center rounded-lg"
              style={{
                background: isCustomer
                  ? "rgba(245, 158, 11, 0.12)"
                  : `${cityColor}18`,
              }}
            >
              {isCustomer ? (
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
                {isHotel ? "Hotel Agent" : "Customer Agent"}
              </div>
            </div>
          </SheetTitle>
        </SheetHeader>

        <div className="section-divider" />

        {/* Agent Info */}
        <div className="shrink-0 px-5 py-3 space-y-2">
          {isHotel && (
            <div className="rounded-lg bg-secondary/30 p-3 space-y-1.5 border border-border/50">
              <div className="flex items-center gap-1.5 text-xs">
                <span
                  className="inline-block h-2 w-2 rounded-full"
                  style={{ background: cityColor }}
                />
                <span className="text-muted-foreground">
                  {agent.location}
                </span>
              </div>
              <div className="text-[11px]" style={{ color: "#eab308" }}>
                {"\u2605".repeat(agent.rank || 0)}
              </div>
              <div className="data-value text-sm font-bold text-emerald-600">
                ${agent.basePrice}/night
              </div>
              <div className="data-value text-[9px] text-muted-foreground/40">
                {agent.hotelId}
              </div>
            </div>
          )}

          {isCustomer && (
            <div className="rounded-lg bg-secondary/30 p-3 space-y-1.5 border border-border/50">
              <div className="text-xs text-muted-foreground">
                {agent.desiredRank}&#9733; {agent.location} &middot; max $
                {agent.maxPrice}
              </div>
            </div>
          )}

          {/* Agent Activity Log */}
          {agentLog && (
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
          {messages.length === 0 && (
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

          {messages.map((msg, i) => (
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
                {msg.content}
              </div>
            </div>
          ))}

          {sending && (
            <div className="flex justify-start animate-fade-in">
              <div className="flex items-center gap-2 rounded-xl bg-slate-100 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 px-3.5 py-2 text-[13px] text-muted-foreground">
                <Loader2 className="h-3 w-3 animate-spin text-indigo-600" />
                <span className="text-xs">Thinking...</span>
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
              placeholder="Type a message..."
              className="h-9 text-sm bg-white dark:bg-slate-800 border-slate-200 dark:border-slate-700"
              disabled={sending}
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

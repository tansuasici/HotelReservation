"use client";

import { useEffect, useRef } from "react";
import { MessageSquare, Radio } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import type { ActivityEntry } from "@/lib/types";
import { MSG_COLORS } from "@/lib/types";

const TYPE_BADGE_CLASS: Record<string, string> = {
  CFP: "bg-indigo-500/15 text-indigo-400 border-indigo-500/20",
  PROPOSAL: "bg-emerald-500/15 text-emerald-400 border-emerald-500/20",
  REFUSE: "bg-red-500/15 text-red-400 border-red-500/20",
  ACCEPT: "bg-emerald-500/15 text-emerald-400 border-emerald-500/20",
  REJECT: "bg-red-500/15 text-red-400 border-red-500/20",
  CONFIRM: "bg-emerald-500/15 text-emerald-400 border-emerald-500/20",
  EVALUATE: "bg-purple-500/15 text-purple-400 border-purple-500/20",
  NEGOTIATE: "bg-cyan-500/15 text-cyan-400 border-cyan-500/20",
  COUNTER_OFFER: "bg-amber-500/15 text-amber-400 border-amber-500/20",
  NEGOTIATE_ACCEPT: "bg-emerald-500/15 text-emerald-400 border-emerald-500/20",
  NEGOTIATE_REJECT: "bg-red-500/15 text-red-400 border-red-500/20",
};

function formatTime(ts: number): string {
  const diff = Date.now() - ts;
  if (diff < 1000) return "now";
  if (diff < 60000) return Math.floor(diff / 1000) + "s";
  if (diff < 3600000) return Math.floor(diff / 60000) + "m";
  return new Date(ts).toLocaleTimeString();
}

export function ActivityFeed({ activity }: { activity: ActivityEntry[] }) {
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [activity.length]);

  return (
    <div className="flex flex-1 flex-col overflow-hidden">
      {/* Header */}
      <div className="flex items-center gap-1.5 shrink-0 px-4 py-3 text-[10px] font-semibold uppercase tracking-widest text-muted-foreground">
        <Radio className="h-3 w-3" />
        Agent Messages
        {activity.length > 0 && (
          <span className="ml-auto data-value text-[9px] opacity-50">
            {activity.length}
          </span>
        )}
      </div>
      <div className="section-divider" />

      {/* Scrollable message list */}
      <div className="flex-1 overflow-y-auto p-3 custom-scrollbar">
        {activity.length === 0 ? (
          <div className="flex h-full items-center justify-center">
            <div className="text-center">
              <MessageSquare className="mx-auto mb-2 h-6 w-6 text-muted-foreground/30" />
              <p className="text-[11px] text-muted-foreground/40">
                Run simulation to see messages
              </p>
            </div>
          </div>
        ) : (
          <div className="space-y-1.5">
            {activity.map((entry, i) => (
              <ActivityItem key={`${entry.timestamp}-${i}`} entry={entry} />
            ))}
            <div ref={bottomRef} />
          </div>
        )}
      </div>
    </div>
  );
}

function ActivityItem({ entry }: { entry: ActivityEntry }) {
  const borderColor = MSG_COLORS[entry.type] || "#6366f1";
  const badgeCls =
    TYPE_BADGE_CLASS[entry.type] || "bg-zinc-500/10 text-zinc-400 border-zinc-500/20";

  return (
    <div
      className="animate-slide-in rounded-md bg-secondary/25 p-2 transition-colors hover:bg-secondary/40"
      style={{
        borderLeft: `2px solid ${borderColor}`,
        boxShadow: `inset 3px 0 8px -4px ${borderColor}30`,
      }}
    >
      <div className="mb-0.5 flex items-center gap-1.5">
        <Badge
          variant="outline"
          className={`text-[7.5px] px-1 py-0 font-bold uppercase tracking-wider ${badgeCls}`}
        >
          {entry.type}
        </Badge>
        <span className="flex-1 truncate text-[10.5px] text-muted-foreground">
          <span className="font-medium text-foreground/70">{entry.from}</span>
          {" \u2192 "}
          <span className="font-medium text-foreground/70">{entry.to}</span>
        </span>
        <span className="shrink-0 data-value text-[8px] text-muted-foreground/30">
          {formatTime(entry.timestamp)}
        </span>
      </div>
      <div className="text-[10px] leading-snug text-muted-foreground/60 pl-0.5">
        {entry.detail}
      </div>
    </div>
  );
}

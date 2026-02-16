"use client";

import { Network, Zap } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import type { SimState } from "@/lib/types";

const STATE_CONFIG: Record<
  string,
  { label: string; className: string; pulse: boolean }
> = {
  NOT_INITIALIZED: {
    label: "IDLE",
    className: "bg-muted text-muted-foreground",
    pulse: false,
  },
  PAUSED: {
    label: "PAUSED",
    className: "bg-amber-500/15 text-amber-400 border-amber-500/20",
    pulse: false,
  },
  RUNNING: {
    label: "RUNNING",
    className: "bg-emerald-500/15 text-emerald-400 border-emerald-500/20",
    pulse: true,
  },
  ENDED: {
    label: "ENDED",
    className: "bg-red-500/15 text-red-400 border-red-500/20",
    pulse: false,
  },
};

export function Header({ simState }: { simState: SimState }) {
  const config = STATE_CONFIG[simState] || STATE_CONFIG.NOT_INITIALIZED;

  return (
    <header className="flex h-12 shrink-0 items-center justify-between border-b border-border bg-card px-5">
      <div className="flex items-center gap-2.5">
        <Network className="h-4.5 w-4.5 text-indigo" />
        <h1 className="text-sm font-semibold tracking-tight">
          Hotel Reservation MAS
        </h1>
        <span className="text-xs text-muted-foreground font-mono">
          CNP
        </span>
      </div>

      <Badge
        variant="outline"
        className={`gap-1.5 text-[11px] font-semibold uppercase tracking-wider px-2.5 py-0.5 ${config.className}`}
      >
        <span
          className={`inline-block h-1.5 w-1.5 rounded-full ${
            config.pulse ? "animate-pulse-dot" : ""
          }`}
          style={{
            backgroundColor: "currentColor",
          }}
        />
        {config.label}
      </Badge>
    </header>
  );
}

"use client";

import { Orbit, Activity, Clock, Sun, Moon } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import type { SimState, SimulationStatus } from "@/lib/types";

const STATE_CONFIG: Record<
  string,
  { label: string; dotClass: string; badgeClass: string; pulse: boolean }
> = {
  NOT_INITIALIZED: {
    label: "IDLE",
    dotClass: "bg-zinc-400",
    badgeClass: "bg-zinc-100 text-zinc-600 border-zinc-200",
    pulse: false,
  },
  PAUSED: {
    label: "PAUSED",
    dotClass: "bg-amber-500",
    badgeClass: "bg-amber-50 text-amber-700 border-amber-200",
    pulse: false,
  },
  RUNNING: {
    label: "LIVE",
    dotClass: "bg-emerald-500",
    badgeClass: "bg-emerald-50 text-emerald-700 border-emerald-200",
    pulse: true,
  },
  ENDED: {
    label: "ENDED",
    dotClass: "bg-red-500",
    badgeClass: "bg-red-50 text-red-700 border-red-200",
    pulse: false,
  },
};

interface Props {
  simState: SimState;
  status: SimulationStatus;
  isDark: boolean;
  onToggleTheme: () => void;
}

export function Navbar({ simState, status, isDark, onToggleTheme }: Props) {
  const config = STATE_CONFIG[simState] || STATE_CONFIG.NOT_INITIALIZED;

  return (
    <header className="glass-navbar flex h-14 shrink-0 items-center justify-between px-5">
      {/* Left — Brand */}
      <div className="flex items-center gap-3">
        <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-indigo-50 border border-indigo-200/60">
          <Orbit className="h-4 w-4 text-indigo-600" />
        </div>
        <div className="flex flex-col">
          <h1 className="text-[13px] font-semibold tracking-tight leading-tight">
            SCOP Mission Control
          </h1>
          <span className="text-[10px] text-muted-foreground font-mono tracking-wide">
            Post-Simulation Analysis
          </span>
        </div>
      </div>

      {/* Center — Tick counter + agents */}
      <div className="flex items-center gap-4">
        {status.currentTick > 0 && (
          <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
            <Clock className="h-3 w-3" />
            <span className="data-value text-foreground/80">
              T+{status.currentTick}
            </span>
          </div>
        )}
        {status.agentCount > 0 && (
          <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
            <Activity className="h-3 w-3" />
            <span className="data-value text-foreground/80">
              {status.agentCount}
            </span>
            <span className="text-[10px]">agents</span>
          </div>
        )}
      </div>

      {/* Right — Theme toggle + State badge */}
      <div className="flex items-center gap-3">
        <Button
          variant="ghost"
          size="sm"
          className="h-8 w-8 p-0 text-muted-foreground hover:text-foreground"
          onClick={onToggleTheme}
        >
          {isDark ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
        </Button>
        <Badge
          variant="outline"
          className={`gap-1.5 text-[10px] font-semibold uppercase tracking-widest px-2.5 py-0.5 ${config.badgeClass}`}
        >
          <span
            className={`inline-block h-1.5 w-1.5 rounded-full ${config.dotClass} ${
              config.pulse ? "animate-pulse-dot" : ""
            }`}
          />
          {config.label}
        </Badge>
      </div>
    </header>
  );
}

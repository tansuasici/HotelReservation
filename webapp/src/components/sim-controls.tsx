"use client";

import { Play, Pause, Square, Settings2, Loader2, Cog, Radio, Hash, Building2, Users } from "lucide-react";
import { Button } from "@/components/ui/button";
import type { SimState, SimulationStatus } from "@/lib/types";

interface Props {
  simState: SimState;
  status: SimulationStatus;
  loading: boolean;
  revealing: boolean;
  onAction: (action: string) => void;
  onConfigOpen?: () => void;
}

const STATE_BADGE: Record<string, { label: string; cls: string }> = {
  NOT_INITIALIZED: {
    label: "Offline",
    cls: "bg-zinc-100 text-zinc-500 dark:bg-zinc-800 dark:text-zinc-400",
  },
  PAUSED: {
    label: "Ready",
    cls: "bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300",
  },
  RUNNING: {
    label: "Running",
    cls: "bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300",
  },
  ENDED: {
    label: "Ended",
    cls: "bg-rose-100 text-rose-600 dark:bg-rose-900/40 dark:text-rose-300",
  },
};

export function SimControls({ simState, status, loading, revealing, onAction, onConfigOpen }: Props) {
  const isPaused = simState === "PAUSED";
  const isRunning = simState === "RUNNING";
  const badge = STATE_BADGE[simState] ?? STATE_BADGE.NOT_INITIALIZED;

  return (
    <div className="shrink-0 p-4 pb-3 space-y-3">
      {/* Header row: title + state badge + gear */}
      <div className="flex items-center gap-2">
        <div className="flex items-center gap-1.5 text-[10px] font-semibold uppercase tracking-widest text-muted-foreground">
          <Settings2 className="h-3 w-3" />
          Simulation
        </div>
        <span className={`ml-auto inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-semibold ${badge.cls}`}>
          {simState === "RUNNING" && (
            <Radio className="h-2.5 w-2.5 animate-pulse" />
          )}
          {badge.label}
        </span>
        {onConfigOpen && (
          <button
            onClick={onConfigOpen}
            className="rounded p-1 text-muted-foreground hover:bg-muted hover:text-foreground transition-colors"
            title="Config"
          >
            <Cog className="h-3.5 w-3.5" />
          </button>
        )}
      </div>

      {/* 2x2 button grid */}
      <div className="grid grid-cols-2 gap-1.5">
        <Button
          size="sm"
          variant="outline"
          className="btn-press h-7 text-[11px] font-medium bg-indigo-50 dark:bg-indigo-950/40 text-indigo-700 dark:text-indigo-300 border-indigo-200/60 dark:border-indigo-800 hover:bg-indigo-100 dark:hover:bg-indigo-900/50"
          disabled={loading || isRunning}
          onClick={() => onAction("setup")}
        >
          {loading && !isPaused ? (
            <Loader2 className="mr-1 h-3 w-3 animate-spin" />
          ) : (
            <Settings2 className="mr-1 h-3 w-3" />
          )}
          Setup
        </Button>

        <Button
          size="sm"
          variant="outline"
          className="btn-press h-7 text-[11px] font-medium bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-300 border-emerald-200/60 dark:border-emerald-800 hover:bg-emerald-100 dark:hover:bg-emerald-900/50"
          disabled={loading || !isPaused}
          onClick={() => onAction("run")}
        >
          <Play className="mr-1 h-3 w-3" />
          Run
        </Button>

        <Button
          size="sm"
          variant="outline"
          className="btn-press h-7 text-[11px] font-medium bg-amber-50 dark:bg-amber-950/40 text-amber-700 dark:text-amber-300 border-amber-200/60 dark:border-amber-800 hover:bg-amber-100 dark:hover:bg-amber-900/50"
          disabled={!isRunning}
          onClick={() => onAction("pause")}
        >
          <Pause className="mr-1 h-3 w-3" />
          Pause
        </Button>

        <Button
          size="sm"
          variant="outline"
          className="btn-press h-7 text-[11px] font-medium bg-rose-50 dark:bg-rose-950/40 text-rose-700 dark:text-rose-300 border-rose-200/60 dark:border-rose-800 hover:bg-rose-100 dark:hover:bg-rose-900/50"
          disabled={!isRunning && !isPaused && !revealing}
          onClick={() => onAction("stop")}
        >
          <Square className="mr-1 h-3 w-3" />
          Stop
        </Button>
      </div>

      {/* Compact stats row */}
      <div className="flex items-center justify-between rounded-md bg-secondary/40 px-2.5 py-1.5 text-[10px]">
        <span className="flex items-center gap-1 text-muted-foreground">
          <Hash className="h-2.5 w-2.5" />
          <span className="font-semibold text-indigo-600 dark:text-indigo-400">{status.currentTick}</span>
        </span>
        <span className="text-border">|</span>
        <span className="flex items-center gap-1 text-muted-foreground">
          <Users className="h-2.5 w-2.5" />
          <span className="font-medium">{status.agentCount}</span>
        </span>
        <span className="text-border">|</span>
        <span className="flex items-center gap-1 text-muted-foreground">
          <Building2 className="h-2.5 w-2.5" />
          <span className="font-medium">{status.registeredHotels}</span>
        </span>
      </div>

      <div className="section-divider" />
    </div>
  );
}

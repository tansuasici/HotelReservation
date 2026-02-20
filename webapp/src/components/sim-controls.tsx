"use client";

import { Play, Square, Settings2, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import type { SimState } from "@/lib/types";

interface Props {
  simState: SimState;
  loading: boolean;
  revealing: boolean;
  onAction: (action: string) => void;
}

export function SimControls({ simState, loading, revealing, onAction }: Props) {
  const isPaused = simState === "PAUSED";
  const isRunning = simState === "RUNNING";
  const isActive = isRunning || isPaused;

  return (
    <div className="shrink-0 p-4 pb-3">
      <div className="mb-3 flex items-center gap-1.5 text-[10px] font-semibold uppercase tracking-widest text-muted-foreground">
        <Settings2 className="h-3 w-3" />
        Simulation
      </div>

      <div className="flex gap-1.5">
        <Button
          size="sm"
          variant="outline"
          className="btn-press h-8 flex-1 text-[11px] font-medium bg-indigo-50 dark:bg-indigo-950/40 text-indigo-700 dark:text-indigo-300 border-indigo-200/60 dark:border-indigo-800 hover:bg-indigo-100 dark:hover:bg-indigo-900/50"
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
          className="btn-press h-8 flex-1 text-[11px] font-medium bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-300 border-emerald-200/60 dark:border-emerald-800 hover:bg-emerald-100 dark:hover:bg-emerald-900/50"
          disabled={loading || isRunning || !isPaused}
          onClick={() => onAction("run")}
        >
          <Play className="mr-1 h-3 w-3" />
          Run
        </Button>

        <Button
          size="sm"
          variant="outline"
          className="btn-press h-8 flex-1 text-[11px] font-medium bg-rose-50 dark:bg-rose-950/40 text-rose-700 dark:text-rose-300 border-rose-200/60 dark:border-rose-800 hover:bg-rose-100 dark:hover:bg-rose-900/50"
          disabled={!isActive && !revealing}
          onClick={() => onAction("stop")}
        >
          <Square className="mr-1 h-3 w-3" />
          Stop
        </Button>
      </div>

    </div>
  );
}

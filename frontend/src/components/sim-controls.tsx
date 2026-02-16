"use client";

import { Play, Pause, Square, Settings2, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import type { SimState } from "@/lib/types";

interface Props {
  simState: SimState;
  loading: boolean;
  onAction: (action: string) => void;
}

export function SimControls({ simState, loading, onAction }: Props) {
  const isInit = simState === "NOT_INITIALIZED";
  const isPaused = simState === "PAUSED";
  const isRunning = simState === "RUNNING";
  const isEnded = simState === "ENDED";

  return (
    <div className="shrink-0 p-4 pb-3">
      <div className="mb-3 flex items-center gap-1.5 text-[10px] font-semibold uppercase tracking-widest text-muted-foreground">
        <Settings2 className="h-3 w-3" />
        Simulation
      </div>

      {/* Control buttons grid */}
      <div className="grid grid-cols-2 gap-1.5">
        <Button
          size="sm"
          variant="outline"
          className="btn-press h-8 text-[11px] font-medium bg-indigo-50 dark:bg-indigo-950/40 text-indigo-700 dark:text-indigo-300 border-indigo-200/60 dark:border-indigo-800 hover:bg-indigo-100 dark:hover:bg-indigo-900/50"
          disabled={loading || isRunning}
          onClick={() => onAction("setup")}
        >
          {loading && isInit ? (
            <Loader2 className="mr-1 h-3 w-3 animate-spin" />
          ) : (
            <Settings2 className="mr-1 h-3 w-3" />
          )}
          Setup
        </Button>

        <Button
          size="sm"
          variant="outline"
          className="btn-press h-8 text-[11px] font-medium bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-300 border-emerald-200/60 dark:border-emerald-800 hover:bg-emerald-100 dark:hover:bg-emerald-900/50"
          disabled={loading || isInit || isRunning}
          onClick={() => onAction("run")}
        >
          <Play className="mr-1 h-3 w-3" />
          Run
        </Button>

        <Button
          size="sm"
          variant="outline"
          className="btn-press h-8 text-[11px] font-medium bg-amber-50 dark:bg-amber-950/40 text-amber-700 dark:text-amber-300 border-amber-200/60 dark:border-amber-800 hover:bg-amber-100 dark:hover:bg-amber-900/50"
          disabled={loading || !isRunning}
          onClick={() => onAction("pause")}
        >
          <Pause className="mr-1 h-3 w-3" />
          Pause
        </Button>

        <Button
          size="sm"
          variant="outline"
          className="btn-press h-8 text-[11px] font-medium bg-rose-50 dark:bg-rose-950/40 text-rose-700 dark:text-rose-300 border-rose-200/60 dark:border-rose-800 hover:bg-rose-100 dark:hover:bg-rose-900/50"
          disabled={loading || isInit}
          onClick={() => onAction("stop")}
        >
          <Square className="mr-1 h-3 w-3" />
          Stop
        </Button>
      </div>

    </div>
  );
}

"use client";

import { Activity } from "lucide-react";
import type { SimulationStatus } from "@/lib/types";

export function StatusPanel({ status }: { status: SimulationStatus }) {
  const rows = [
    { label: "State", value: status.state, accent: false },
    { label: "Tick", value: status.currentTick, accent: true },
    { label: "Agents", value: status.agentCount, accent: false },
    { label: "Hotels (DF)", value: status.registeredHotels, accent: false },
  ];

  return (
    <div className="p-4 pt-3">
      <div className="mb-2.5 flex items-center gap-1.5 text-[10px] font-semibold uppercase tracking-widest text-muted-foreground">
        <Activity className="h-3 w-3" />
        Status
      </div>
      <div className="grid grid-cols-2 gap-1.5">
        {rows.map((r) => (
          <div
            key={r.label}
            className="rounded-md bg-secondary/40 px-2.5 py-1.5 border border-transparent glow-border-hover"
          >
            <div className="text-[9px] uppercase tracking-wider text-muted-foreground mb-0.5">
              {r.label}
            </div>
            <div
              className={`data-value text-sm font-semibold ${
                r.accent ? "text-indigo-600" : "text-foreground/90"
              }`}
            >
              {r.value}
            </div>
          </div>
        ))}
      </div>
      <div className="section-divider mt-3" />
    </div>
  );
}

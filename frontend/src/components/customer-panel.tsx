"use client";

import { User } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import type { CustomerStatus } from "@/lib/types";
import { STATE_COLORS } from "@/lib/types";

const STATE_BADGE_CLASS: Record<string, string> = {
  IDLE: "bg-zinc-50 text-zinc-500 border-zinc-200",
  SEARCHING: "bg-indigo-50 text-indigo-700 border-indigo-200",
  WAITING_PROPOSALS: "bg-amber-50 text-amber-700 border-amber-200",
  EVALUATING: "bg-purple-50 text-purple-700 border-purple-200",
  NEGOTIATING: "bg-cyan-50 text-cyan-700 border-cyan-200",
  RESERVING: "bg-amber-50 text-amber-700 border-amber-200",
  COMPLETED: "bg-emerald-50 text-emerald-700 border-emerald-200",
  FAILED: "bg-red-50 text-red-700 border-red-200",
};

export function CustomerPanel({
  customers,
}: {
  customers: CustomerStatus[];
}) {
  if (customers.length === 0) return null;

  return (
    <div className="p-4 pt-1">
      <div className="mb-2.5 flex items-center gap-1.5 text-[10px] font-semibold uppercase tracking-widest text-muted-foreground">
        <User className="h-3 w-3" />
        Customers
        <span className="ml-auto data-value text-[9px] opacity-50">
          {customers.length}
        </span>
      </div>
      <div className="space-y-1.5">
        {customers.map((c, i) => (
          <div
            key={c.customerId}
            className="animate-stagger"
            style={{ animationDelay: `${i * 50}ms` }}
          >
            <CustomerCard customer={c} />
          </div>
        ))}
      </div>
      <div className="section-divider mt-3" />
    </div>
  );
}

function CustomerCard({ customer: c }: { customer: CustomerStatus }) {
  const badgeCls =
    STATE_BADGE_CLASS[c.state] || "bg-zinc-500/10 text-zinc-400 border-zinc-500/20";
  const stateColor = STATE_COLORS[c.state] || "#f59e0b";

  return (
    <div
      className="rounded-lg bg-secondary/30 p-2.5 border border-transparent transition-all duration-200 glow-border-hover"
      style={{ borderLeftColor: stateColor + "40", borderLeftWidth: "2px" }}
    >
      <div className="mb-1 flex items-center justify-between">
        <span className="flex items-center gap-1.5 text-xs font-semibold">
          <User
            className="h-3 w-3"
            style={{ color: stateColor }}
          />
          {c.customerId}
        </span>
        <Badge
          variant="outline"
          className={`text-[8px] px-1.5 py-0 font-bold uppercase tracking-wider ${badgeCls}`}
        >
          {c.state}
        </Badge>
      </div>
      <div className="text-[10.5px] text-muted-foreground">
        {c.desiredRank}&#9733; {c.desiredLocation} &middot; max ${c.maxPrice}
      </div>

      {c.confirmation && (
        <div className="mt-1.5 flex items-center gap-1 text-[10.5px] font-semibold text-emerald-600">
          <span className="inline-block h-1 w-1 rounded-full bg-emerald-500" />
          {c.confirmation.hotelName} &middot; ${c.confirmation.totalPrice}
        </div>
      )}

      {!c.confirmation && c.selectedProposal && (
        <div className="mt-1.5 text-[10.5px] text-indigo-600">
          {c.selectedProposal.hotelName} &middot; $
          {c.selectedProposal.pricePerNight}/night
        </div>
      )}

      {c.negotiationRound > 0 && !c.confirmation && (
        <div className="mt-1 text-[10.5px] text-cyan-600">
          Negotiating R{c.negotiationRound} with {c.negotiatingHotel || "?"}
        </div>
      )}
    </div>
  );
}

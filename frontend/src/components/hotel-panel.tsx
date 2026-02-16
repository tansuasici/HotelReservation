"use client";

import { Building2 } from "lucide-react";
import type { Hotel } from "@/lib/types";
import { CITY_COLORS, DEFAULT_CITY_COLOR } from "@/lib/types";

function cityColor(city: string): string {
  return CITY_COLORS[city] || DEFAULT_CITY_COLOR;
}

export function HotelPanel({ hotels }: { hotels: Hotel[] }) {
  if (hotels.length === 0) {
    return (
      <div className="p-4 pt-1">
        <div className="mb-2.5 flex items-center gap-1.5 text-[10px] font-semibold uppercase tracking-widest text-muted-foreground">
          <Building2 className="h-3 w-3" />
          Hotels
        </div>
        <div className="rounded-lg border border-dashed border-border p-6 text-center">
          <Building2 className="mx-auto mb-2 h-6 w-6 text-muted-foreground/30" />
          <div className="text-xs text-muted-foreground">
            Run <span className="font-semibold text-indigo-600">Setup</span> to
            load hotels
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="p-4 pt-1">
      <div className="mb-2.5 flex items-center gap-1.5 text-[10px] font-semibold uppercase tracking-widest text-muted-foreground">
        <Building2 className="h-3 w-3" />
        Hotels
        <span className="ml-auto data-value text-[9px] opacity-50">
          {hotels.length}
        </span>
      </div>
      <div className="space-y-1.5">
        {hotels.map((h, i) => {
          const city =
            (typeof h.location === "object"
              ? h.location?.city
              : h.location) ||
            h.city ||
            "";
          const name = h.name || h.hotelName || "";
          const rank = h.stars || h.rank || 0;
          const price =
            h.pricePerNight ?? h.rooms?.[0]?.pricePerNight ?? h.basePrice ?? h.price ?? 0;
          const stars = "\u2605".repeat(rank);
          const color = cityColor(city);

          return (
            <div
              key={i}
              className="animate-stagger rounded-lg bg-secondary/30 p-2.5 border border-transparent transition-all duration-200 glow-border-hover"
              style={{
                animationDelay: `${i * 40}ms`,
                borderLeftColor: color + "40",
                borderLeftWidth: "2px",
              }}
            >
              <div className="mb-0.5 flex items-center justify-between">
                <span className="flex items-center gap-1.5 text-xs font-semibold">
                  <Building2 className="h-3 w-3" style={{ color }} />
                  {name}
                </span>
                <span className="data-value text-xs font-bold text-emerald-600">
                  ${price}
                </span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-[10.5px] text-muted-foreground">
                  {city}
                </span>
                <span
                  className="text-[10px] tracking-wider"
                  style={{ color: "#eab308" }}
                >
                  {stars}
                </span>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

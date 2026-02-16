"use client";

import { useEffect, useRef, useCallback } from "react";
import { Network as VisNetwork } from "vis-network";
import { DataSet } from "vis-data";
import { Orbit, Hotel, User } from "lucide-react";
import type {
  Topology,
  TopologyNode,
  CustomerStatus,
  ActivityEntry,
  SimState,
} from "@/lib/types";
import {
  CITY_COLORS,
  DEFAULT_CITY_COLOR,
  CUSTOMER_COLOR,
  MSG_COLORS,
  STATE_COLORS,
} from "@/lib/types";

function cityColor(city: string) {
  return CITY_COLORS[city] || DEFAULT_CITY_COLOR;
}

// --- Tooltip builders ---

function buildHotelTooltip(node: TopologyNode): HTMLDivElement {
  const div = document.createElement("div");
  div.style.cssText =
    "background:#1e1e2e;color:#e2e8f0;padding:10px 14px;border-radius:8px;font-family:Inter,system-ui,sans-serif;font-size:12px;line-height:1.5;box-shadow:0 4px 20px rgba(0,0,0,0.4);border:1px solid rgba(255,255,255,0.08);min-width:160px;";

  const name = node.displayName || node.name;
  const city = node.location || "";
  const stars = node.rank || 0;
  const price = node.basePrice || 0;

  div.innerHTML =
    `<div style="font-weight:700;font-size:13px;margin-bottom:4px;">${name}</div>` +
    (city ? `<div style="color:#94a3b8;">${city}</div>` : "") +
    (stars ? `<div style="color:#facc15;">${"\u2605".repeat(stars)}</div>` : "") +
    (price ? `<div style="color:#4ade80;font-weight:600;">$${price}/night</div>` : "");

  return div;
}

function buildCustomerTooltip(
  node: TopologyNode,
  customer?: CustomerStatus
): HTMLDivElement {
  const div = document.createElement("div");
  div.style.cssText =
    "background:#1e1e2e;color:#e2e8f0;padding:10px 14px;border-radius:8px;font-family:Inter,system-ui,sans-serif;font-size:12px;line-height:1.5;box-shadow:0 4px 20px rgba(0,0,0,0.4);border:1px solid rgba(255,255,255,0.08);min-width:160px;";

  const name = node.displayName || node.name;
  const desiredRank = customer?.desiredRank || node.desiredRank || 0;
  const desiredLocation = customer?.desiredLocation || node.location || "";
  const maxPrice = customer?.maxPrice || node.maxPrice || 0;
  const state = customer?.state || "IDLE";
  const stateColor = STATE_COLORS[state] || "#71717a";

  div.innerHTML =
    `<div style="font-weight:700;font-size:13px;margin-bottom:4px;">${name}</div>` +
    (desiredLocation
      ? `<div style="color:#94a3b8;">Looking for: ${desiredLocation} ${"\u2605".repeat(desiredRank)}</div>`
      : "") +
    (maxPrice ? `<div style="color:#94a3b8;">Max budget: $${maxPrice}</div>` : "") +
    `<div style="color:${stateColor};font-weight:600;margin-top:2px;">${state}</div>`;

  return div;
}

// Lucide icon SVGs as data URIs for vis-network nodes
function svgDataUri(pathD: string, color: string, size = 24) {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="${color}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${pathD}</svg>`;
  return "data:image/svg+xml;charset=utf-8," + encodeURIComponent(svg);
}

// Lucide "Hotel" icon path
const HOTEL_ICON_PATH = `<path d="M10 22v-6.57"/><path d="M12 11h.01"/><path d="M12 7h.01"/><path d="M14 15.43V22"/><path d="M15 16a5 5 0 0 0-6 0"/><path d="M16 11h.01"/><path d="M16 7h.01"/><path d="M8 11h.01"/><path d="M8 7h.01"/><rect x="4" y="2" width="16" height="20" rx="2"/>`;

// Lucide "User" icon path
const USER_ICON_PATH = `<path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>`;

interface Props {
  topology: Topology | null;
  customers: CustomerStatus[];
  activity: ActivityEntry[];
  simState: SimState;
  onNodeClick: (node: TopologyNode) => void;
  isDark?: boolean;
}

export function NetworkGraph({
  topology,
  customers,
  activity,
  simState,
  onNodeClick,
  isDark = false,
}: Props) {
  const containerRef = useRef<HTMLDivElement>(null);
  const networkRef = useRef<VisNetwork | null>(null);
  const nodesDSRef = useRef<DataSet<Record<string, unknown>> | null>(null);
  const edgesDSRef = useRef<DataSet<Record<string, unknown>> | null>(null);
  const nameMapRef = useRef<Record<string, string>>({});
  const isDarkRef = useRef(isDark);
  const topoNodesRef = useRef<TopologyNode[]>([]);
  const customersRef = useRef<CustomerStatus[]>([]);
  const lastActivityLen = useRef(0);

  // Build graph when topology changes
  useEffect(() => {
    if (!topology || !containerRef.current) return;

    const agentNodes = topology.nodes.filter(
      (n) => n.type === "HotelAgent" || n.type === "CustomerAgent"
    );
    const agentNames = new Set(agentNodes.map((n) => n.name));
    topoNodesRef.current = topology.nodes;

    const nodes = agentNodes.map((n) => {
      const isHotel = n.type === "HotelAgent";
      const city = n.location || "";
      const color = isHotel ? cityColor(city) : CUSTOMER_COLOR;
      const iconSize = isHotel ? 28 + (n.rank || 3) * 2 : 32;

      return {
        id: n.name,
        label: isHotel ? n.displayName || n.name : n.name,
        shape: "image",
        image: svgDataUri(
          isHotel ? HOTEL_ICON_PATH : USER_ICON_PATH,
          color,
          iconSize
        ),
        size: isHotel ? 18 + (n.rank || 3) * 2 : 22,
        title: isHotel ? buildHotelTooltip(n) : buildCustomerTooltip(n),
        color: {
          background: "transparent",
          border: "transparent",
          highlight: { background: "transparent", border: "#4f46e5" },
        },
        font: {
          color: isDarkRef.current ? "#cbd5e1" : "#334155",
          size: 11,
          face: "Inter, system-ui, sans-serif",
        },
      };
    });

    const edges = topology.edges
      .filter((e) => agentNames.has(e.from) && agentNames.has(e.to))
      .map((e, i) => ({
        id: "e" + i,
        from: e.from,
        to: e.to,
        color: {
          color: isDarkRef.current ? "rgba(200,210,220,0.12)" : "rgba(100,116,139,0.2)",
          highlight: isDarkRef.current ? "rgba(200,210,220,0.35)" : "rgba(100,116,139,0.5)",
        },
        width: 1,
        smooth: { type: "continuous" },
      }));

    if (networkRef.current) networkRef.current.destroy();

    const nodesDS = new DataSet(nodes);
    const edgesDS = new DataSet(edges);
    nodesDSRef.current = nodesDS;
    edgesDSRef.current = edgesDS;

    // Build name map
    const nm: Record<string, string> = {};
    topology.nodes.forEach((n) => {
      nm[n.name] = n.name;
      if (n.displayName) nm[n.displayName] = n.name;
    });
    nameMapRef.current = nm;

    // Create a DOM element outside React's tree for vis-network.
    // vis-network manipulates its container's children directly,
    // which conflicts with React's reconciler (removeChild errors).
    const visDiv = document.createElement("div");
    visDiv.style.width = "100%";
    visDiv.style.height = "100%";
    containerRef.current.appendChild(visDiv);

    const network = new VisNetwork(
      visDiv,
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      { nodes: nodesDS as any, edges: edgesDS as any },
      {
        physics: {
          solver: "forceAtlas2Based",
          forceAtlas2Based: {
            gravitationalConstant: -60,
            centralGravity: 0.005,
            springLength: 180,
            springConstant: 0.04,
            damping: 0.4,
          },
          stabilization: { iterations: 150 },
        },
        interaction: {
          hover: true,
          tooltipDelay: 200,
          zoomView: true,
          dragView: true,
        },
      }
    );

    network.once("stabilizationIterationsDone", () => {
      network.fit({
        animation: { duration: 400, easingFunction: "easeInOutQuad" },
      });
    });

    network.on("click", (params: { nodes?: string[] }) => {
      // Hide tooltip on click
      const tip = visDiv.querySelector(".vis-tooltip") as HTMLElement | null;
      if (tip) tip.style.visibility = "hidden";

      if (params.nodes && params.nodes.length > 0) {
        const nodeData = topoNodesRef.current.find(
          (n) => n.name === params.nodes![0]
        );
        if (nodeData) onNodeClick(nodeData);
      }
    });

    networkRef.current = network;
    lastActivityLen.current = 0;

    return () => {
      network.destroy();
      visDiv.remove();
      networkRef.current = null;
    };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [topology, onNodeClick]);

  // Update colors when theme changes (without rebuilding graph)
  useEffect(() => {
    isDarkRef.current = isDark;
    if (!nodesDSRef.current || !edgesDSRef.current) return;
    const fontColor = isDark ? "#cbd5e1" : "#334155";
    const edgeColor = isDark ? "rgba(200,210,220,0.12)" : "rgba(100,116,139,0.2)";
    const edgeHighlight = isDark ? "rgba(200,210,220,0.35)" : "rgba(100,116,139,0.5)";

    for (const node of nodesDSRef.current.get()) {
      nodesDSRef.current.update({ id: node.id, font: { ...node.font as object, color: fontColor } });
    }
    for (const edge of edgesDSRef.current.get()) {
      edgesDSRef.current.update({ id: edge.id, color: { color: edgeColor, highlight: edgeHighlight } });
    }
  }, [isDark]);

  // Update customer node icons + tooltips when state changes
  useEffect(() => {
    customersRef.current = customers;
    if (!nodesDSRef.current || customers.length === 0) return;
    for (const c of customers) {
      const color = STATE_COLORS[c.state] || CUSTOMER_COLOR;
      const topoNode = topoNodesRef.current.find((n) => n.name === c.customerId);
      try {
        nodesDSRef.current.update({
          id: c.customerId,
          image: svgDataUri(USER_ICON_PATH, color, 32),
          title: topoNode ? buildCustomerTooltip(topoNode, c) : undefined,
        });
      } catch {
        /* node might not exist yet */
      }
    }
  }, [customers]);

  // Animate new activity edges
  const animateEdge = useCallback(
    (from: string, to: string, type: string) => {
      if (!edgesDSRef.current) return;
      const fromId = nameMapRef.current[from] || from;
      const toId = nameMapRef.current[to] || to;

      const edge = edgesDSRef.current.get().find(
        (e: Record<string, unknown>) =>
          (e.from === fromId && e.to === toId) ||
          (e.from === toId && e.to === fromId)
      );
      if (!edge) return;

      const color = MSG_COLORS[type] || "#6366f1";
      edgesDSRef.current.update({
        id: edge.id,
        color: { color, opacity: 1.0 },
        width: 4,
        shadow: { enabled: true, color, size: 10 },
      });

      setTimeout(() => {
        try {
          const darkNow = document.documentElement.classList.contains("dark");
          edgesDSRef.current?.update({
            id: edge.id,
            color: {
              color: darkNow ? "rgba(200,210,220,0.12)" : "rgba(100,116,139,0.2)",
              highlight: darkNow ? "rgba(200,210,220,0.35)" : "rgba(100,116,139,0.5)",
            },
            width: 1,
            shadow: { enabled: false },
          });
        } catch {
          /* */
        }
      }, 1800);
    },
    []
  );

  useEffect(() => {
    if (activity.length <= lastActivityLen.current) return;
    const newEntries = activity.slice(lastActivityLen.current);
    lastActivityLen.current = activity.length;

    const delay = Math.min(600, 4000 / Math.max(newEntries.length, 1));
    newEntries.forEach((entry, i) => {
      setTimeout(() => {
        animateEdge(entry.from, entry.to, entry.type);
      }, i * delay);
    });
  }, [activity, animateEdge]);

  const hasTopology = !!topology;
  const nodeCount = hasTopology
    ? topology.nodes.filter(
        (n) => n.type === "HotelAgent" || n.type === "CustomerAgent"
      ).length
    : 0;
  const edgeCount = hasTopology ? topology.edges.length : 0;

  // Always render the same DOM structure — vis-network manipulates containerRef
  // children directly, so React must never add/remove it from the tree.
  return (
    <div className="relative h-full w-full">
      {/* vis-network canvas container — always in DOM, hidden when no topology */}
      <div
        ref={containerRef}
        className="absolute inset-0"
        style={{ visibility: hasTopology ? "visible" : "hidden" }}
      />

      {/* Empty state — shown on top when no topology */}
      {!hasTopology && (
        <div className="absolute inset-0 flex items-center justify-center animate-fade-in">
          <div className="text-center">
            <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-2xl bg-indigo-500/8 border border-indigo-500/10">
              <Orbit className="h-7 w-7 text-indigo-500/50" />
            </div>
            <p className="text-sm text-muted-foreground/70">
              Click <span className="font-semibold text-indigo-600">Setup</span>{" "}
              to initialize the agent network
            </p>
            <p className="mt-1.5 text-[11px] text-muted-foreground/30 data-value">
              7 Hotels &middot; 5 Customers &middot; CNP
            </p>
          </div>
        </div>
      )}

      {/* Overlay stats */}
      {hasTopology && (
        <div className="absolute top-3 right-3 flex items-center gap-2 text-[10px] text-muted-foreground/40 data-value">
          <span>{nodeCount} nodes</span>
          <span>&middot;</span>
          <span>{edgeCount} edges</span>
        </div>
      )}

      {/* Legend */}
      {hasTopology && (
        <div className="absolute bottom-3 left-3 rounded-lg glass-panel px-3 py-2 text-[10.5px]">
          <div className="mb-1.5 flex items-center gap-1.5">
            <User className="h-3 w-3" style={{ color: CUSTOMER_COLOR }} />
            <span className="text-muted-foreground/60">Customer</span>
          </div>
          {Object.keys(CITY_COLORS)
            .sort()
            .map((city) => (
              <div key={city} className="flex items-center gap-1.5">
                <Hotel className="h-3 w-3" style={{ color: CITY_COLORS[city] }} />
                <span className="text-muted-foreground/60">{city}</span>
              </div>
            ))}
        </div>
      )}
    </div>
  );
}

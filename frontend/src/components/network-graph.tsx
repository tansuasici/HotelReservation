"use client";

import { useEffect, useRef, useCallback } from "react";
import { Network as VisNetwork } from "vis-network";
import { DataSet } from "vis-data";
import { Network as NetworkIcon, Orbit } from "lucide-react";
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
  const topoNodesRef = useRef<TopologyNode[]>([]);
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

      return {
        id: n.name,
        label: isHotel ? n.displayName || n.name : n.name,
        shape: isHotel ? "dot" : "star",
        size: isHotel ? 14 + (n.rank || 3) * 3 : 22,
        color: {
          background: color,
          border: color,
          highlight: { background: color, border: "#4f46e5" },
        },
        font: {
          color: isDark ? "#cbd5e1" : "#334155",
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
          color: isDark ? "rgba(200,210,220,0.12)" : "rgba(100,116,139,0.2)",
          highlight: isDark ? "rgba(200,210,220,0.35)" : "rgba(100,116,139,0.5)",
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

    const network = new VisNetwork(
      containerRef.current,
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
      networkRef.current = null;
    };
  }, [topology, onNodeClick, isDark]);

  // Update customer node colors
  useEffect(() => {
    if (!nodesDSRef.current || customers.length === 0) return;
    for (const c of customers) {
      const color = STATE_COLORS[c.state] || CUSTOMER_COLOR;
      try {
        nodesDSRef.current.update({
          id: c.customerId,
          color: {
            background: color,
            border: color,
            highlight: { background: color, border: "#4f46e5" },
          },
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

  // Empty state
  if (!topology) {
    return (
      <div className="flex h-full items-center justify-center animate-fade-in">
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
    );
  }

  const nodeCount = topology.nodes.filter(
    (n) => n.type === "HotelAgent" || n.type === "CustomerAgent"
  ).length;
  const edgeCount = topology.edges.length;

  return (
    <>
      <div ref={containerRef} className="h-full w-full" />

      {/* Overlay stats */}
      <div className="absolute top-3 right-3 flex items-center gap-2 text-[10px] text-muted-foreground/40 data-value">
        <span>{nodeCount} nodes</span>
        <span>&middot;</span>
        <span>{edgeCount} edges</span>
      </div>

      {/* Legend */}
      <div className="absolute bottom-3 left-3 rounded-lg glass-panel px-3 py-2 text-[10.5px]">
        <div className="mb-1 flex items-center gap-1.5">
          <span
            className="inline-block h-2.5 w-2.5"
            style={{
              color: "#eab308",
              fontSize: "10px",
              lineHeight: "10px",
            }}
          >
            &#9733;
          </span>
          <span className="text-muted-foreground/60">Customer</span>
        </div>
        {Object.keys(CITY_COLORS)
          .sort()
          .map((city) => (
            <div key={city} className="flex items-center gap-1.5">
              <span
                className="inline-block h-2 w-2 rounded-full"
                style={{ background: CITY_COLORS[city] }}
              />
              <span className="text-muted-foreground/60">{city}</span>
            </div>
          ))}
      </div>
    </>
  );
}

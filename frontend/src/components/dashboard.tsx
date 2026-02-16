"use client";

import { useState, useCallback } from "react";
import { useSimulation } from "@/hooks/use-simulation";
import { useTheme } from "@/hooks/use-theme";
import { Navbar } from "./navbar";
import { SimControls } from "./sim-controls";
import { StatusPanel } from "./status-panel";
import { CustomerPanel } from "./customer-panel";
import { HotelPanel } from "./hotel-panel";
import { NetworkGraph } from "./network-graph";
import { ActivityFeed } from "./activity-feed";
import { AgentChat } from "./agent-chat";
import type { TopologyNode } from "@/lib/types";

export function Dashboard() {
  const sim = useSimulation();
  const { isDark, toggle: toggleTheme } = useTheme();
  const [selectedAgent, setSelectedAgent] = useState<TopologyNode | null>(null);
  const [chatOpen, setChatOpen] = useState(false);

  const handleNodeClick = useCallback((node: TopologyNode) => {
    setSelectedAgent(node);
    setChatOpen(true);
  }, []);

  return (
    <div className="flex h-screen flex-col overflow-hidden">
      {/* NAVBAR — fixed height, never scrolls */}
      <Navbar simState={sim.simState} status={sim.status} isDark={isDark} onToggleTheme={toggleTheme} />

      {/* MAIN AREA — fills remaining height */}
      <div className="flex flex-1 overflow-hidden">
        {/* LEFT SIDEBAR — fixed width, internal scroll */}
        <aside className="flex w-72 shrink-0 flex-col overflow-hidden glass-panel-solid border-r border-border">
          {/* Pinned controls — never scrolls */}
          <SimControls
            simState={sim.simState}
            loading={sim.loading}
            onAction={sim.doAction}
          />
          {/* Scrollable content */}
          <div className="flex-1 overflow-y-auto custom-scrollbar">
            <StatusPanel status={sim.status} />
            <CustomerPanel customers={sim.customers} />
            <HotelPanel hotels={sim.hotels} />
          </div>
        </aside>

        {/* CENTER — graph fills remaining space */}
        <main className="relative flex-1 overflow-hidden graph-grid-bg">
          <NetworkGraph
            topology={sim.topology}
            customers={sim.customers}
            activity={sim.activity}
            simState={sim.simState}
            onNodeClick={handleNodeClick}
            isDark={isDark}
          />
        </main>

        {/* RIGHT SIDEBAR — fixed width, internal scroll */}
        <aside className="flex w-80 shrink-0 flex-col overflow-hidden glass-panel-solid border-l border-border">
          <ActivityFeed activity={sim.activity} />
        </aside>
      </div>

      {/* Agent Chat Sheet */}
      <AgentChat
        agent={selectedAgent}
        open={chatOpen}
        onOpenChange={setChatOpen}
        topology={sim.topology}
      />
    </div>
  );
}

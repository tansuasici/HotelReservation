"use client";

import { useState, useCallback, useEffect, useRef } from "react";
import type {
  SimulationStatus,
  SimState,
  Topology,
  Hotel,
  CustomerStatus,
  ActivityEntry,
} from "@/lib/types";

/**
 * Derive a customer's visual state from the last revealed activity message
 * that involves that customer (by customerId).
 */
const MSG_TO_STATE: Record<string, string> = {
  CFP: "SEARCHING",
  PROPOSAL: "EVALUATING",
  REFUSE: "SEARCHING",
  EVALUATE: "EVALUATING",
  NEGOTIATE: "NEGOTIATING",
  COUNTER_OFFER: "NEGOTIATING",
  NEGOTIATE_ACCEPT: "NEGOTIATING",
  NEGOTIATE_REJECT: "SEARCHING",
  CONFIRM: "COMPLETED",
};

function deriveCustomerStates(
  revealedActivity: ActivityEntry[],
  fullCustomers: CustomerStatus[],
  allRevealed: boolean
): CustomerStatus[] {
  if (fullCustomers.length === 0) return [];

  // If all messages have been revealed, use the real API states
  if (allRevealed) return fullCustomers;

  // Build a map: customerId → last relevant message type
  const lastMsgType: Record<string, string> = {};
  for (const entry of revealedActivity) {
    // Check if from or to is a customer
    for (const c of fullCustomers) {
      if (entry.from === c.customerId || entry.to === c.customerId) {
        lastMsgType[c.customerId] = entry.type;
      }
    }
  }

  return fullCustomers.map((c) => {
    const msgType = lastMsgType[c.customerId];
    if (!msgType) {
      // No message revealed for this customer yet → IDLE
      return { ...c, state: "IDLE" };
    }
    const derived = MSG_TO_STATE[msgType];
    if (derived) {
      return { ...c, state: derived };
    }
    return { ...c, state: "IDLE" };
  });
}

export function useSimulation() {
  const [status, setStatus] = useState<SimulationStatus>({
    state: "NOT_INITIALIZED",
    currentTick: 0,
    agentCount: 0,
    registeredHotels: 0,
    message: "",
  });
  const [topology, setTopology] = useState<Topology | null>(null);
  const [hotels, setHotels] = useState<Hotel[]>([]);
  const [customers, setCustomers] = useState<CustomerStatus[]>([]);
  const [activity, setActivity] = useState<ActivityEntry[]>([]);
  const [loading, setLoading] = useState(false);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const staticLoadedRef = useRef(false);

  // Progressive reveal refs
  const fullActivityRef = useRef<ActivityEntry[]>([]);
  const fullCustomersRef = useRef<CustomerStatus[]>([]);
  const revealIndexRef = useRef(0);
  const revealTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const simEndedRef = useRef(false);
  const initPollCountRef = useRef(0);

  function stopRevealTimer() {
    if (revealTimerRef.current) {
      clearInterval(revealTimerRef.current);
      revealTimerRef.current = null;
    }
  }

  function resetRevealState() {
    stopRevealTimer();
    fullActivityRef.current = [];
    fullCustomersRef.current = [];
    revealIndexRef.current = 0;
    simEndedRef.current = false;
    initPollCountRef.current = 0;
  }

  /** Reveal the next message and derive customer states */
  const revealNext = useCallback(() => {
    const full = fullActivityRef.current;
    const idx = revealIndexRef.current;

    if (idx >= full.length) {
      // All caught up — if sim ended, stop the timer
      if (simEndedRef.current) {
        stopRevealTimer();
        // Show real customer states
        setCustomers(fullCustomersRef.current);
      }
      return;
    }

    revealIndexRef.current = idx + 1;
    const revealed = full.slice(0, idx + 1);
    setActivity(revealed);
    setCustomers(
      deriveCustomerStates(revealed, fullCustomersRef.current, false)
    );
  }, []);

  /** Start reveal timer with given interval */
  const startRevealTimer = useCallback(
    (intervalMs: number) => {
      stopRevealTimer();
      revealTimerRef.current = setInterval(() => {
        revealNext();
      }, intervalMs);
    },
    [revealNext]
  );

  const loadData = useCallback(async () => {
    try {
      const res = await fetch("/api/data");
      if (!res.ok) return;
      const data = await res.json();

      // First load: full topology + hotels
      if (!staticLoadedRef.current && data.topology) {
        setTopology(data.topology);
        setHotels(data.hotels || []);
        staticLoadedRef.current = true;
        setStatus((prev) => ({
          ...prev,
          agentCount: data.topology?.nodes?.length || 0,
          registeredHotels: data.hotels?.length || 0,
        }));
      } else if (staticLoadedRef.current && data.topology) {
        // Subsequent loads: update availableRooms on hotel nodes
        setTopology((prev) => {
          if (!prev) return prev;
          const freshNodes = data.topology.nodes as { name: string; availableRooms?: number }[];
          const freshMap = new Map(freshNodes.map((n) => [n.name, n.availableRooms]));
          let changed = false;
          const updatedNodes = prev.nodes.map((node) => {
            if (node.type === "HotelAgent") {
              const freshRooms = freshMap.get(node.name);
              if (freshRooms != null && freshRooms !== node.availableRooms) {
                changed = true;
                return { ...node, availableRooms: freshRooms };
              }
            }
            return node;
          });
          return changed ? { ...prev, nodes: updatedNodes } : prev;
        });
      }

      // Store full data in refs (progressive reveal will drip-feed to state)
      const newActivity: ActivityEntry[] = data.activity || [];
      const newCustomers: CustomerStatus[] = data.customers || [];
      fullActivityRef.current = newActivity;
      fullCustomersRef.current = newCustomers;

      // Auto-stop: if all customers reached a terminal state, stop the simulation
      if (
        newCustomers.length > 0 &&
        !simEndedRef.current &&
        newCustomers.every((c) => c.state === "COMPLETED" || c.state === "FAILED")
      ) {
        simEndedRef.current = true;
        stopPolling();
        // Send stop to backend
        fetch("/api/sim", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ action: "stop" }),
        }).catch(() => {});
        setStatus((prev) => ({ ...prev, state: "ENDED", message: "All customers finished" }));
        if (revealTimerRef.current) {
          startRevealTimer(100);
        }
      }

      // If no reveal timer is running and there's new data to reveal, start one
      if (!revealTimerRef.current && revealIndexRef.current < newActivity.length) {
        startRevealTimer(simEndedRef.current ? 100 : 700);
      }

      // If nothing to reveal (e.g. initial setup), set customers directly
      if (newActivity.length === 0) {
        setCustomers(newCustomers);
        setActivity([]);
      }
    } catch {
      /* */
    }
  }, [startRevealTimer]);

  function stopPolling() {
    if (pollRef.current) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }
  }

  const pollState = useCallback(async () => {
    try {
      const res = await fetch("/api/sim");
      if (!res.ok) return;
      const data = await res.json();

      setStatus((prev) => ({
        ...prev,
        state: data.state,
        message: data.message || "",
        currentTick: data.currentTick ?? prev.currentTick,
      }));

      // Still waiting for Java to start — keep polling, don't load data yet
      if (data.state === "NOT_INITIALIZED") {
        initPollCountRef.current++;
        // Timeout after ~30s (20 polls × 1.5s) — Java failed to start
        if (initPollCountRef.current > 20) {
          stopPolling();
          setStatus((prev) => ({
            ...prev,
            message: "Java process failed to start. Check console for errors.",
          }));
        }
        return;
      }
      initPollCountRef.current = 0;

      // Stop polling on stable states: PAUSED, ENDED, or process dead
      if (!data.processAlive || data.state === "PAUSED" || data.state === "ENDED") {
        stopPolling();

        // Mark sim as ended — speed up reveal
        if (data.state === "ENDED") {
          simEndedRef.current = true;
          // If timer is running, restart with faster interval
          if (revealTimerRef.current) {
            startRevealTimer(100);
          }
        }

        await loadData();
        return;
      }

      // While RUNNING, keep loading fresh data each poll
      if (data.state === "RUNNING") {
        await loadData();
      }
    } catch {
      /* */
    }
  }, [loadData, startRevealTimer]);

  const startPolling = useCallback(() => {
    stopPolling();
    pollRef.current = setInterval(pollState, 1500);
  }, [pollState]);

  // Clean up on unmount
  useEffect(() => {
    return () => {
      stopPolling();
      stopRevealTimer();
    };
  }, []);

  const simState = status.state as SimState;

  const doAction = useCallback(
    async (action: string) => {
      setLoading(true);

      // Reset UI on setup
      if (action === "setup") {
        setTopology(null);
        setHotels([]);
        setCustomers([]);
        setActivity([]);
        staticLoadedRef.current = false;
        stopPolling();
        resetRevealState();
        // Clear all chat histories on the backend
        fetch("/api/chat", { method: "DELETE" }).catch(() => {});
      }

      try {
        const res = await fetch("/api/sim", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ action }),
        });

        if (res.ok) {
          const data = await res.json();
          setStatus((prev) => ({
            ...prev,
            state: data.state,
            message: data.message || "",
          }));

          // Start polling after setup (wait for PAUSED) or run (track progress)
          if (action === "setup" || action === "run") {
            startPolling();
          }

          // Stop polling on stop, load final data
          if (action === "stop") {
            stopPolling();
            simEndedRef.current = true;
            if (revealTimerRef.current) {
              startRevealTimer(100);
            }
            await loadData();
          }
        }
      } catch (e) {
        setStatus((prev) => ({
          ...prev,
          message: "Error: " + (e instanceof Error ? e.message : "Unknown"),
        }));
      } finally {
        setLoading(false);
      }
    },
    [loadData, startPolling, startRevealTimer]
  );

  return {
    status,
    simState,
    topology,
    hotels,
    customers,
    activity,
    loading,
    doAction,
  };
}

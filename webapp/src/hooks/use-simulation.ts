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
 * Maximum number of activity entries to keep in the UI.
 * Older entries are dropped to prevent memory/DOM bloat.
 */
const MAX_ACTIVITY_UI = 300;

/**
 * When the reveal backlog exceeds this, skip animation entirely.
 */
const SKIP_REVEAL_THRESHOLD = 200;

const MSG_TO_STATE: Record<string, string> = {
  CFP: "SEARCHING",
  PROPOSAL: "WAITING_PROPOSALS",
  REFUSE: "WAITING_PROPOSALS",
  EVALUATE: "EVALUATING",
  SHORTLIST: "EVALUATING",
  ACCEPT: "RESERVING",
  NEGOTIATE: "NEGOTIATING",
  COUNTER_OFFER: "NEGOTIATING",
  NEGOTIATE_ACCEPT: "RESERVING",
  NEGOTIATE_REJECT: "EVALUATING",
  FALLBACK: "NEGOTIATING",
  CONFIRM: "COMPLETED",
  AUDIT: "COMPLETED",
  FAIL: "FAILED",
};

const STATE_PRIORITY: Record<string, number> = {
  IDLE: 0,
  SEARCHING: 1,
  WAITING_PROPOSALS: 2,
  EVALUATING: 3,
  NEGOTIATING: 4,
  RESERVING: 5,
  COMPLETED: 6,
  FAILED: 6,
};

function deriveCustomerStates(
  revealedActivity: ActivityEntry[],
  fullCustomers: CustomerStatus[],
  allRevealed: boolean
): CustomerStatus[] {
  if (fullCustomers.length === 0) return [];
  if (allRevealed) return fullCustomers;

  const derivedState: Record<string, string> = {};
  for (const entry of revealedActivity) {
    const targetState = MSG_TO_STATE[entry.type];
    if (!targetState) continue;
    for (const c of fullCustomers) {
      if (entry.from === c.customerId || entry.to === c.customerId) {
        const current = derivedState[c.customerId] || "IDLE";
        const currentPri = STATE_PRIORITY[current] ?? 0;
        const newPri = STATE_PRIORITY[targetState] ?? 0;

        if (
          newPri > currentPri ||
          targetState === "FAILED" ||
          (current === "NEGOTIATING" && targetState === "EVALUATING")
        ) {
          derivedState[c.customerId] = targetState;
        }
      }
    }
  }

  return fullCustomers.map((c) => {
    const state = derivedState[c.customerId];
    return state ? { ...c, state } : { ...c, state: "IDLE" };
  });
}

/** Tail-window: keep only the last N entries */
function tailWindow(arr: ActivityEntry[], max: number): ActivityEntry[] {
  return arr.length > max ? arr.slice(arr.length - max) : arr;
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
  const [revealing, setRevealing] = useState(false);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const pollingActiveRef = useRef(false);
  const staticLoadedRef = useRef(false);

  // Progressive reveal refs
  const fullActivityRef = useRef<ActivityEntry[]>([]);
  const fullCustomersRef = useRef<CustomerStatus[]>([]);
  const revealIndexRef = useRef(0);
  const revealTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const simEndedRef = useRef(false);
  const initPollCountRef = useRef(0);
  const setupLoadingRef = useRef(false);
  const userStoppedRef = useRef(false);
  const lastActivityTimestamp = useRef(0);

  function stopRevealTimer() {
    if (revealTimerRef.current) {
      clearInterval(revealTimerRef.current);
      revealTimerRef.current = null;
    }
    setRevealing(false);
  }

  function resetRevealState() {
    stopRevealTimer();
    fullActivityRef.current = [];
    fullCustomersRef.current = [];
    revealIndexRef.current = 0;
    simEndedRef.current = false;
    initPollCountRef.current = 0;
    lastActivityTimestamp.current = 0;
  }

  /** Reveal the next batch of messages and derive customer states. */
  const revealNext = useCallback(() => {
    const full = fullActivityRef.current;
    const idx = revealIndexRef.current;

    if (idx >= full.length) {
      setCustomers(fullCustomersRef.current);
      if (simEndedRef.current) {
        stopRevealTimer();
      }
      return;
    }

    // Adaptive batch: much more aggressive for large backlogs
    const remaining = full.length - idx;
    let batchSize: number;
    if (remaining > 500) batchSize = 100;
    else if (remaining > 200) batchSize = 40;
    else if (remaining > 80) batchSize = 15;
    else if (remaining > 30) batchSize = 5;
    else batchSize = 1;

    const newIdx = Math.min(idx + batchSize, full.length);
    revealIndexRef.current = newIdx;

    // Only send the tail window to state (DOM)
    const revealed = tailWindow(full.slice(0, newIdx), MAX_ACTIVITY_UI);
    setActivity(revealed);
    setCustomers(
      deriveCustomerStates(revealed, fullCustomersRef.current, false)
    );
  }, []);

  /** Start reveal timer with given interval */
  const startRevealTimer = useCallback(
    (intervalMs: number) => {
      if (userStoppedRef.current) return;
      stopRevealTimer();
      setRevealing(true);
      revealTimerRef.current = setInterval(() => {
        revealNext();
      }, intervalMs);
    },
    [revealNext]
  );

  /**
   * Skip reveal entirely — show all data immediately.
   * Used when backlog is too large to animate meaningfully.
   */
  const skipReveal = useCallback(() => {
    stopRevealTimer();
    const full = fullActivityRef.current;
    revealIndexRef.current = full.length;
    setActivity(tailWindow(full, MAX_ACTIVITY_UI));
    setCustomers(fullCustomersRef.current);
  }, []);

  const loadData = useCallback(async () => {
    try {
      const since = lastActivityTimestamp.current;
      const res = await fetch(`/api/data?since=${since}`);
      if (!res.ok) return;
      const data = await res.json();

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

      const newActivity: ActivityEntry[] = data.activity || [];
      const newCustomers: CustomerStatus[] = data.customers || [];

      // Merge incremental activity into the full buffer
      if (newActivity.length > 0) {
        // If since > 0, these are NEW entries; append them
        if (since > 0) {
          fullActivityRef.current = [...fullActivityRef.current, ...newActivity];
        } else {
          fullActivityRef.current = newActivity;
        }
        // Track the latest timestamp for incremental fetch
        const lastEntry = newActivity[newActivity.length - 1];
        if (lastEntry) {
          lastActivityTimestamp.current = lastEntry.timestamp;
        }
      }
      fullCustomersRef.current = newCustomers;

      // Auto-stop: if all customers reached terminal state
      if (
        newCustomers.length > 0 &&
        !simEndedRef.current &&
        newCustomers.every((c) => c.state === "COMPLETED" || c.state === "FAILED")
      ) {
        simEndedRef.current = true;
        stopPolling();
        fetch("/api/sim", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ action: "stop" }),
        }).catch(() => {});
        setStatus((prev) => ({ ...prev, state: "ENDED", message: "All customers finished" }));

        // If backlog is huge, skip reveal; otherwise fast-reveal
        const backlog = fullActivityRef.current.length - revealIndexRef.current;
        if (backlog > SKIP_REVEAL_THRESHOLD) {
          skipReveal();
        } else {
          startRevealTimer(60);
        }
        return;
      }

      // Decide reveal strategy based on backlog size
      const backlog = fullActivityRef.current.length - revealIndexRef.current;
      if (backlog > SKIP_REVEAL_THRESHOLD && !revealTimerRef.current) {
        // Too far behind — skip animation, show data directly
        skipReveal();
      } else if (!revealTimerRef.current && backlog > 0) {
        startRevealTimer(simEndedRef.current ? 60 : 250);
      }

      if (fullActivityRef.current.length === 0) {
        setCustomers(newCustomers);
        setActivity([]);
      }
    } catch {
      /* */
    }
  }, [startRevealTimer, skipReveal]);

  function stopPolling() {
    pollingActiveRef.current = false;
    if (pollRef.current) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }
  }

  const pollState = useCallback(async () => {
    if (!pollingActiveRef.current || userStoppedRef.current) return;
    try {
      const res = await fetch("/api/sim");
      if (!res.ok || userStoppedRef.current) return;
      const data = await res.json();

      if (userStoppedRef.current) return;

      setStatus((prev) => ({
        ...prev,
        state: data.state,
        message: data.message || "",
        currentTick: data.currentTick ?? prev.currentTick,
      }));

      if (data.state === "NOT_INITIALIZED") {
        initPollCountRef.current++;
        if (initPollCountRef.current > 20) {
          stopPolling();
          setupLoadingRef.current = false;
          setLoading(false);
          setStatus((prev) => ({
            ...prev,
            message: "Java process failed to start. Check console for errors.",
          }));
        }
        return;
      }
      initPollCountRef.current = 0;

      if (setupLoadingRef.current) {
        setupLoadingRef.current = false;
        setLoading(false);
      }

      if (!data.processAlive || data.state === "PAUSED" || data.state === "ENDED") {
        stopPolling();

        if (data.state === "ENDED") {
          simEndedRef.current = true;
          const backlog = fullActivityRef.current.length - revealIndexRef.current;
          if (backlog > SKIP_REVEAL_THRESHOLD) {
            skipReveal();
          } else if (revealTimerRef.current) {
            startRevealTimer(60);
          }
        }

        await loadData();
        return;
      }

      if (data.state === "RUNNING") {
        await loadData();
      }
    } catch {
      /* */
    }
  }, [loadData, startRevealTimer, skipReveal]);

  const startPolling = useCallback(() => {
    stopPolling();
    pollingActiveRef.current = true;
    pollRef.current = setInterval(pollState, 1500);
  }, [pollState]);

  useEffect(() => {
    return () => {
      stopPolling();
      stopRevealTimer();
    };
  }, []);

  const simState = status.state as SimState;

  const doAction = useCallback(
    async (action: string) => {
      if (action === "stop") {
        userStoppedRef.current = true;
        stopPolling();
        stopRevealTimer();

        fetch("/api/sim", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ action: "stop" }),
        }).catch(() => {});
        setStatus((prev) => ({ ...prev, state: "ENDED", message: "Stopped" }));
        return;
      }

      if (action === "pause") {
        stopPolling();
        stopRevealTimer();

        fetch("/api/sim", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ action: "pause" }),
        }).catch(() => {});
        setStatus((prev) => ({ ...prev, state: "PAUSED", message: "Paused" }));
        return;
      }

      setLoading(true);

      if (action === "setup") {
        userStoppedRef.current = false;
        setTopology(null);
        setHotels([]);
        setCustomers([]);
        setActivity([]);
        staticLoadedRef.current = false;
        stopPolling();
        resetRevealState();
        fetch("/api/chat", { method: "DELETE" }).catch(() => {});
      }

      try {
        if (action === "setup") {
          setupLoadingRef.current = true;
        }

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

          if (action === "setup" || action === "run") {
            userStoppedRef.current = false;
            simEndedRef.current = false;
            startPolling();
          }
        }
      } catch (e) {
        setupLoadingRef.current = false;
        setStatus((prev) => ({
          ...prev,
          message: "Error: " + (e instanceof Error ? e.message : "Unknown"),
        }));
      } finally {
        if (!setupLoadingRef.current) {
          setLoading(false);
        }
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
    revealing,
    doAction,
  };
}

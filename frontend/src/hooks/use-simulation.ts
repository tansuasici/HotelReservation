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

  // Load data from output files
  const loadData = useCallback(async () => {
    try {
      const res = await fetch("/api/data");
      if (!res.ok) return;

      const data = await res.json();
      setTopology(data.topology);
      setHotels(data.hotels || []);
      setCustomers(data.customers || []);
      setActivity(data.activity || []);

      const custs: CustomerStatus[] = data.customers || [];
      const allDone =
        custs.length > 0 &&
        custs.every((c) => c.state === "COMPLETED" || c.state === "FAILED");

      setStatus((prev) => ({
        ...prev,
        agentCount: data.topology?.nodes?.length || 0,
        registeredHotels: data.hotels?.length || 0,
        state: allDone ? "ENDED" : prev.state,
      }));
    } catch {
      /* ignore */
    }
  }, []);

  // Poll simulation state from runner
  const pollState = useCallback(async () => {
    try {
      const res = await fetch("/api/sim");
      if (!res.ok) return;
      const data = await res.json();
      setStatus((prev) => ({
        ...prev,
        state: data.state,
        message: data.message || "",
      }));

      // When running or ended, also reload data
      if (data.state === "RUNNING" || data.state === "ENDED" || data.state === "PAUSED") {
        await loadData();
      }

      // Stop polling when ended
      if (data.state === "ENDED") {
        stopPolling();
      }
    } catch {
      /* ignore */
    }
  }, [loadData]);

  const startPolling = useCallback(() => {
    stopPolling();
    pollRef.current = setInterval(pollState, 2000);
  }, [pollState]);

  function stopPolling() {
    if (pollRef.current) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }
  }

  // Auto-load data on mount
  useEffect(() => {
    loadData();
    return () => stopPolling();
  }, [loadData]);

  const simState = status.state as SimState;

  const doAction = useCallback(
    async (action: string) => {
      if (action === "reload") {
        setLoading(true);
        await loadData();
        // Also check sim state
        try {
          const res = await fetch("/api/sim");
          if (res.ok) {
            const data = await res.json();
            setStatus((prev) => ({
              ...prev,
              state: data.state,
              message: data.message || "",
            }));
          }
        } catch {
          /* ignore */
        }
        setLoading(false);
        return;
      }

      // Sim control actions: setup, run, pause, stop
      setLoading(true);

      // Reset frontend state immediately on setup
      if (action === "setup") {
        setTopology(null);
        setHotels([]);
        setCustomers([]);
        setActivity([]);
        stopPolling();
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

          // Start polling after setup or run
          if (action === "setup" || action === "run") {
            await loadData();
            startPolling();
          }

          // Stop polling on stop
          if (action === "stop") {
            stopPolling();
            await loadData();
          }

          // Reload data on pause
          if (action === "pause") {
            await loadData();
          }
        }
      } catch (e) {
        setStatus((prev) => ({
          ...prev,
          message: "Action failed: " + (e instanceof Error ? e.message : "Unknown error"),
        }));
      } finally {
        setLoading(false);
      }
    },
    [loadData, startPolling]
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

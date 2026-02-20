"use client";

import { useState, useEffect, useCallback } from "react";
import { Loader2, Save, RefreshCw, Clock, Timer, Repeat, Handshake, ToggleLeft, Hash } from "lucide-react";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import type { SimState, ScopConfig } from "@/lib/types";

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  simState: SimState;
}

interface FieldDef {
  key: string;
  label: string;
  icon: React.ReactNode;
  hint?: string;
}

const RUN_FIELDS: FieldDef[] = [
  { key: "STEP_DELAY", label: "Step Delay", icon: <Clock className="h-3 w-3" />, hint: "ms" },
  { key: "TIMEOUT_TICK", label: "Timeout", icon: <Timer className="h-3 w-3" />, hint: "ticks" },
  { key: "NUMBER_OF_EPISODES", label: "Episodes", icon: <Repeat className="h-3 w-3" /> },
];

const CNP_FIELDS: FieldDef[] = [
  { key: "PROPOSAL_DEADLINE_TICKS", label: "Deadline", icon: <Timer className="h-3 w-3" />, hint: "ticks" },
  { key: "MAX_NEGOTIATION_ROUNDS", label: "Max Rounds", icon: <Hash className="h-3 w-3" /> },
  { key: "NEGOTIATION_ENABLED", label: "Negotiation", icon: <ToggleLeft className="h-3 w-3" />, hint: "1/0" },
];

function ConfigField({
  field,
  value,
  disabled,
  onChange,
}: {
  field: FieldDef;
  value: string;
  disabled: boolean;
  onChange: (v: string) => void;
}) {
  return (
    <div className="flex items-center gap-2 rounded-md bg-secondary/30 px-2.5 py-1.5">
      <span className="text-muted-foreground shrink-0">{field.icon}</span>
      <span className="text-[11px] text-muted-foreground shrink-0 min-w-[70px]">{field.label}</span>
      <Input
        type="number"
        className="h-6 flex-1 text-xs text-right bg-background/60 border-border/50 px-2"
        value={value}
        disabled={disabled}
        onChange={(e) => onChange(e.target.value)}
      />
      {field.hint && (
        <span className="text-[9px] text-muted-foreground/60 shrink-0 w-6">{field.hint}</span>
      )}
    </div>
  );
}

export function ConfigPanel({ open, onOpenChange, simState }: Props) {
  const [config, setConfig] = useState<ScopConfig | null>(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [saved, setSaved] = useState(false);

  const editable = simState === "NOT_INITIALIZED" || simState === "PAUSED" || simState === "ENDED";

  const loadConfig = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const res = await fetch("/api/config");
      if (!res.ok) {
        setError("Failed to load config");
        return;
      }
      const data = await res.json();
      setConfig(data);
    } catch {
      setError("Cannot connect to backend");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (open) {
      loadConfig();
      setSaved(false);
    }
  }, [open, loadConfig]);

  const handleChange = (section: "run" | "cnp", key: string, value: string) => {
    if (!config) return;
    setSaved(false);
    setConfig({
      ...config,
      [section]: { ...config[section], [key]: value },
    });
  };

  const handleSave = async () => {
    if (!config) return;
    setSaving(true);
    setError("");
    try {
      const res = await fetch("/api/config", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(config),
      });
      if (!res.ok) {
        setError("Failed to save config");
      } else {
        setSaved(true);
      }
    } catch {
      setError("Cannot connect to backend");
    } finally {
      setSaving(false);
    }
  };

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="w-[340px] sm:w-[380px]">
        <SheetHeader>
          <SheetTitle className="flex items-center gap-2 text-sm">
            Simulation Config
            <button onClick={loadConfig} className="p-1 rounded hover:bg-muted transition-colors" title="Reload">
              <RefreshCw className={`h-3 w-3 ${loading ? "animate-spin" : ""}`} />
            </button>
          </SheetTitle>
        </SheetHeader>

        <div className="mt-5 space-y-4">
          {loading && (
            <div className="flex items-center justify-center py-8">
              <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
            </div>
          )}

          {error && (
            <p className="text-xs text-destructive bg-destructive/10 rounded-md px-3 py-2">{error}</p>
          )}

          {!loading && config && (
            <>
              {/* Run section */}
              <div>
                <h3 className="flex items-center gap-1.5 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground mb-2">
                  <Handshake className="h-3 w-3" />
                  Runtime
                </h3>
                <div className="space-y-1.5">
                  {RUN_FIELDS.map((field) => (
                    <ConfigField
                      key={field.key}
                      field={field}
                      value={config.run?.[field.key] ?? ""}
                      disabled={!editable}
                      onChange={(v) => handleChange("run", field.key, v)}
                    />
                  ))}
                </div>
              </div>

              {/* CNP section */}
              <div>
                <h3 className="flex items-center gap-1.5 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground mb-2">
                  <Handshake className="h-3 w-3" />
                  Contract Net Protocol
                </h3>
                <div className="space-y-1.5">
                  {CNP_FIELDS.map((field) => (
                    <ConfigField
                      key={field.key}
                      field={field}
                      value={config.cnp?.[field.key] ?? ""}
                      disabled={!editable}
                      onChange={(v) => handleChange("cnp", field.key, v)}
                    />
                  ))}
                </div>
              </div>

              {/* Save */}
              <Button
                size="sm"
                className="w-full h-8"
                disabled={!editable || saving}
                onClick={handleSave}
              >
                {saving ? (
                  <Loader2 className="mr-2 h-3.5 w-3.5 animate-spin" />
                ) : (
                  <Save className="mr-2 h-3.5 w-3.5" />
                )}
                {saved ? "Saved" : "Save Config"}
              </Button>

              {!editable && (
                <p className="text-[10px] text-muted-foreground text-center">
                  Stop or pause the simulation to edit config.
                </p>
              )}
            </>
          )}
        </div>
      </SheetContent>
    </Sheet>
  );
}

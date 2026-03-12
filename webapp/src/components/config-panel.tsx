"use client";

import { useState, useEffect, useCallback } from "react";
import { Loader2, RefreshCw, Clock, Timer, Repeat, Handshake, ToggleLeft, Hash, Settings2 } from "lucide-react";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetDescription,
  SheetFooter,
} from "@/components/ui/sheet";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Separator } from "@/components/ui/separator";
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
  { key: "STEP_DELAY", label: "Step Delay", icon: <Clock className="h-4 w-4" />, hint: "ms" },
  { key: "TIMEOUT_TICK", label: "Timeout", icon: <Timer className="h-4 w-4" />, hint: "ticks" },
  { key: "NUMBER_OF_EPISODES", label: "Episodes", icon: <Repeat className="h-4 w-4" /> },
];

const CNP_FIELDS: FieldDef[] = [
  { key: "PROPOSAL_DEADLINE_TICKS", label: "Deadline", icon: <Timer className="h-4 w-4" />, hint: "ticks" },
  { key: "MAX_NEGOTIATION_ROUNDS", label: "Max Rounds", icon: <Hash className="h-4 w-4" /> },
  { key: "NEGOTIATION_ENABLED", label: "Negotiation", icon: <ToggleLeft className="h-4 w-4" />, hint: "1/0" },
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
    <div className="space-y-2">
      <Label className="flex items-center gap-2 text-sm font-medium">
        <span className="text-muted-foreground">{field.icon}</span>
        {field.label}
      </Label>
      <div className="flex items-center gap-2">
        <Input
          type="number"
          className="h-9 text-sm font-mono"
          value={value}
          disabled={disabled}
          onChange={(e) => onChange(e.target.value)}
        />
        {field.hint && (
          <span className="text-xs text-muted-foreground shrink-0">{field.hint}</span>
        )}
      </div>
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
      <SheetContent side="right" className="w-[400px] sm:w-[440px] flex flex-col">
        <SheetHeader>
          <SheetTitle className="flex items-center gap-2">
            <Settings2 className="h-5 w-5" />
            Simulation Config
          </SheetTitle>
          <SheetDescription>
            Configure runtime and protocol parameters.
          </SheetDescription>
        </SheetHeader>

        <div className="flex-1 overflow-y-auto px-4 py-2 space-y-5">
          {loading && (
            <div className="flex items-center justify-center py-12">
              <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
            </div>
          )}

          {error && (
            <p className="text-sm text-destructive bg-destructive/10 rounded-lg px-4 py-3">{error}</p>
          )}

          {!loading && config && (
            <>
              <Card>
                <CardHeader className="pb-3">
                  <CardTitle className="flex items-center gap-2 text-sm font-semibold">
                    <Clock className="h-4 w-4 text-muted-foreground" />
                    Runtime
                  </CardTitle>
                </CardHeader>
                <CardContent className="space-y-4">
                  {RUN_FIELDS.map((field, i) => (
                    <div key={field.key}>
                      <ConfigField
                        field={field}
                        value={config.run?.[field.key] ?? ""}
                        disabled={!editable}
                        onChange={(v) => handleChange("run", field.key, v)}
                      />
                      {i < RUN_FIELDS.length - 1 && <Separator className="mt-4" />}
                    </div>
                  ))}
                </CardContent>
              </Card>

              <Card>
                <CardHeader className="pb-3">
                  <CardTitle className="flex items-center gap-2 text-sm font-semibold">
                    <Handshake className="h-4 w-4 text-muted-foreground" />
                    Contract Net Protocol
                  </CardTitle>
                </CardHeader>
                <CardContent className="space-y-4">
                  {CNP_FIELDS.map((field, i) => (
                    <div key={field.key}>
                      <ConfigField
                        field={field}
                        value={config.cnp?.[field.key] ?? ""}
                        disabled={!editable}
                        onChange={(v) => handleChange("cnp", field.key, v)}
                      />
                      {i < CNP_FIELDS.length - 1 && <Separator className="mt-4" />}
                    </div>
                  ))}
                </CardContent>
              </Card>
            </>
          )}
        </div>

        {!loading && config && (
          <SheetFooter className="px-4 pb-4">
            {!editable && (
              <p className="text-xs text-muted-foreground text-center w-full mb-2">
                Stop or pause the simulation to edit config.
              </p>
            )}
            <div className="flex items-center gap-2 w-full">
              <Button
                variant="outline"
                size="icon"
                onClick={loadConfig}
                disabled={loading}
                title="Reload config"
              >
                <RefreshCw className={`h-4 w-4 ${loading ? "animate-spin" : ""}`} />
              </Button>
              <Button
                className="flex-1"
                disabled={!editable || saving}
                onClick={handleSave}
              >
                {saving ? "Saving..." : saved ? "Saved" : "Save"}
              </Button>
            </div>
          </SheetFooter>
        )}
      </SheetContent>
    </Sheet>
  );
}

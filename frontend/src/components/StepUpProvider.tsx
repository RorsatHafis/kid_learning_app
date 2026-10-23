import { useEffect, useRef, useState, type FormEvent, type ReactNode } from "react";
import { Dialog } from "@/components/ui/Dialog";
import { Button } from "@/components/ui/Button";
import { FormField } from "@/features/auth/FormField";
import { useMessages } from "@/i18n/catalog";
import { accountApi } from "@/lib/api/endpoints";
import { ApiError } from "@/lib/api/ApiError";
import { onStepUpNeeded, setStepUp } from "@/lib/api/client";
import { stepUpMessages } from "./stepUp.messages";

/**
 * M8: when the server says an action needs the password again (it moves money or access), this asks for it once,
 * gets a short proof for this sign-in, and lets the action go through; cancelling leaves the action refused.
 */
export function StepUpProvider({ children }: { children: ReactNode }) {
  const m = useMessages(stepUpMessages);
  const resolver = useRef<((ok: boolean) => void) | null>(null);
  const [open, setOpen] = useState(false);
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    onStepUpNeeded(
      () =>
        new Promise<boolean>((resolve) => {
          resolver.current = resolve;
          setPassword("");
          setError(null);
          setOpen(true);
        }),
    );
    return () => onStepUpNeeded(null);
  }, []);

  function finish(ok: boolean) {
    setOpen(false);
    setPassword("");
    resolver.current?.(ok);
    resolver.current = null;
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const proof = await accountApi.stepUp(password);
      setStepUp(proof.stepUpToken, proof.expiresAt);
      finish(true);
    } catch (err) {
      setError(err instanceof ApiError && (err.status === 422 || err.status === 423) ? (err.problem?.detail ?? m.wrong) : m.failed);
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      {children}
      <Dialog open={open} onClose={() => finish(false)} title={m.title} description={m.lead}>
        <form onSubmit={submit} className="flex flex-col gap-4">
          <FormField
            label={m.password}
            type="password"
            autoComplete="current-password"
            autoFocus
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          {error && (
            <p role="alert" className="text-sm font-medium text-brick-deep">
              {error}
            </p>
          )}
          <div className="flex flex-wrap gap-2">
            <Button type="submit" disabled={busy || !password}>
              {busy ? m.checking : m.continue}
            </Button>
            <Button type="button" variant="outline" onClick={() => finish(false)}>
              {m.cancel}
            </Button>
          </div>
        </form>
      </Dialog>
    </>
  );
}

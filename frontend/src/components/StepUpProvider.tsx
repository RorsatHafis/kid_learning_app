import { useEffect, useRef, useState, type FormEvent, type ReactNode } from "react";
import { Dialog } from "@/components/ui/Dialog";
import { Button } from "@/components/ui/Button";
import { FormField } from "@/features/auth/FormField";
import { useMessages } from "@/i18n/catalog";
import { accountApi } from "@/lib/api/endpoints";
import { ApiError } from "@/lib/api/ApiError";
import { onStepUpNeeded, setStepUp } from "@/lib/api/client";
import { useOptionalSession } from "@/lib/auth/AuthContext";
import { stepUpMessages } from "./stepUp.messages";

/**
 * M8: when the server says an action needs the password again (it moves money or access), this asks for it once,
 * gets a short proof for this sign-in, and lets the action go through; cancelling leaves the action refused.
 * M9a: an administrator also gives a code from their authenticator app, or is pointed to turning one on.
 */
export function StepUpProvider({ children }: { children: ReactNode }) {
  const m = useMessages(stepUpMessages);
  const resolver = useRef<((ok: boolean) => void) | null>(null);
  const [open, setOpen] = useState(false);
  const [password, setPassword] = useState("");
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [setupNeeded, setSetupNeeded] = useState(false);
  // The server decides; an administrator is asked for the code at once rather than after a refusal.
  const [serverAskedForCode, setServerAskedForCode] = useState(false);
  const askCode = useOptionalSession()?.platformRole === "ADMIN" || serverAskedForCode;

  useEffect(() => {
    onStepUpNeeded(
      () =>
        new Promise<boolean>((resolve) => {
          resolver.current = resolve;
          setPassword("");
          setCode("");
          setError(null);
          setSetupNeeded(false);
          setOpen(true);
        }),
    );
    return () => onStepUpNeeded(null);
  }, []);

  function finish(ok: boolean) {
    setOpen(false);
    setPassword("");
    setCode("");
    resolver.current?.(ok);
    resolver.current = null;
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const proof = await accountApi.stepUp(password, askCode ? code.trim() : undefined);
      setStepUp(proof.stepUpToken, proof.expiresAt);
      finish(true);
    } catch (err) {
      if (err instanceof ApiError && err.problem?.secondFactorSetupRequired) {
        setSetupNeeded(true);
        setError(err.problem.detail);
      } else if (err instanceof ApiError && err.problem?.secondFactorRequired) {
        setServerAskedForCode(true);
        setCode("");
        setError(err.problem.detail);
      } else {
        setError(err instanceof ApiError && (err.status === 422 || err.status === 423) ? (err.problem?.detail ?? m.wrong) : m.failed);
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      {children}
      <Dialog open={open} onClose={() => finish(false)} title={m.title} description={askCode ? m.leadWithCode : m.lead}>
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
          {askCode && (
            <FormField
              label={m.code}
              hint={m.codeHint}
              autoComplete="one-time-code"
              autoCapitalize="characters"
              spellCheck={false}
              maxLength={20}
              required
              value={code}
              onChange={(e) => setCode(e.target.value)}
            />
          )}
          {error && (
            <p role="alert" className="text-sm font-medium text-brick-deep">
              {error}
            </p>
          )}
          {setupNeeded && (
            <a href="/admin/account#authenticator" className="w-fit text-sm font-semibold text-tonle-700 underline underline-offset-2">
              {m.setUp}
            </a>
          )}
          <div className="flex flex-wrap gap-2">
            <Button type="submit" disabled={busy || !password || (askCode && !code.trim())}>
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

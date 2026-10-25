import { useState, type FormEvent } from "react";
import { CheckCircle2, Smartphone } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { accountApi } from "@/lib/api/endpoints";
import { ApiError } from "@/lib/api/ApiError";
import type { PhoneStatus } from "@/lib/api/types";
import { ErrorState, LoadingState } from "@/components/ui/States";
import { Button } from "@/components/ui/Button";
import { FormField } from "@/features/auth/FormField";
import { useMessages } from "@/i18n/catalog";
import { phoneMessages } from "./phone.messages";

type Step = { kind: "idle" } | { kind: "add" } | { kind: "code"; sentTo: string } | { kind: "remove" };

/**
 * M9b: the account's phone number. Adding it: the number and the password, then the 6-digit code texted to it. The
 * server decides everything (the number's rules, limits, who may hold it); this only asks and says what it answered.
 */
export function PhoneSection() {
  const m = useMessages(phoneMessages);
  const loaded = useAsync(() => accountApi.phone(), []);
  const [saved, setSaved] = useState<PhoneStatus | null>(null);
  const [step, setStep] = useState<Step>({ kind: "idle" });
  const [phone, setPhone] = useState("");
  const [password, setPassword] = useState("");
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const status = saved ?? loaded.data;

  function go(next: Step) {
    setStep(next);
    setError(null);
    if (next.kind !== "code") setCode("");
    if (next.kind === "idle") setPassword("");
  }

  function explain(err: unknown) {
    if (err instanceof ApiError && err.validationErrors?.currentPassword) return m.wrongPassword;
    if (err instanceof ApiError && err.status === 429 && err.problem?.retryAfterSeconds && err.problem.retryAfterSeconds < 120) {
      return m.waitSeconds(err.problem.retryAfterSeconds);
    }
    if (err instanceof ApiError && [400, 409, 422, 429, 503].includes(err.status) && err.problem?.detail) return err.problem.detail;
    return m.failed;
  }

  async function sendCode(e?: FormEvent) {
    e?.preventDefault();
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const sent = await accountApi.requestPhoneCode(password, phone);
      go({ kind: "code", sentTo: sent.phone });
    } catch (err) {
      setError(explain(err));
    } finally {
      setBusy(false);
    }
  }

  async function confirm(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      setSaved(await accountApi.confirmPhone(code.trim()));
      setPhone("");
      go({ kind: "idle" });
    } catch (err) {
      setError(explain(err));
    } finally {
      setBusy(false);
    }
  }

  async function remove(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      setSaved(await accountApi.removePhone(password));
      setNotice(m.removed);
      go({ kind: "idle" });
    } catch (err) {
      setError(explain(err));
    } finally {
      setBusy(false);
    }
  }

  const alert = error && (
    <p role="alert" className="text-sm font-medium text-brick-deep">
      {error}
    </p>
  );

  if (loaded.isLoading && !status) return <LoadingState label={m.loading} />;
  if (loaded.error) return <ErrorState error={loaded.error} onRetry={loaded.refetch} />;
  if (!status) return null;

  if (step.kind === "add") {
    return (
      <form onSubmit={sendCode} noValidate className="flex flex-col gap-4">
        <FormField
          label={m.phone}
          hint={m.phoneHint}
          type="tel"
          inputMode="tel"
          autoComplete="tel"
          autoFocus
          required
          maxLength={40}
          value={phone}
          onChange={(e) => setPhone(e.target.value)}
        />
        <FormField
          label={m.password}
          type="password"
          autoComplete="current-password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
        {alert}
        <div className="flex flex-wrap gap-2">
          <Button type="submit" size="md" disabled={busy || !phone.trim() || !password}>
            {busy ? m.sending : m.send}
          </Button>
          <Button type="button" size="md" variant="outline" onClick={() => go({ kind: "idle" })}>
            {m.cancel}
          </Button>
        </div>
      </form>
    );
  }

  if (step.kind === "code") {
    return (
      <form onSubmit={confirm} noValidate className="flex flex-col gap-4">
        <p role="status" className="text-sm leading-relaxed text-ink">
          {m.sentTo(step.sentTo)}
        </p>
        <FormField
          label={m.code}
          inputMode="numeric"
          autoComplete="one-time-code"
          autoFocus
          required
          maxLength={6}
          value={code}
          onChange={(e) => setCode(e.target.value)}
        />
        {alert}
        <p className="text-sm text-ink-faint">{m.notArriving}</p>
        <div className="flex flex-wrap gap-2">
          <Button type="submit" size="md" disabled={busy || code.trim().length !== 6}>
            {busy ? m.confirming : m.confirm}
          </Button>
          <Button type="button" size="md" variant="outline" disabled={busy} onClick={() => void sendCode()}>
            {m.sendAnother}
          </Button>
          <Button type="button" size="md" variant="ghost" onClick={() => go({ kind: "idle" })}>
            {m.cancel}
          </Button>
        </div>
      </form>
    );
  }

  if (step.kind === "remove") {
    return (
      <form onSubmit={remove} noValidate className="flex flex-col gap-4">
        <p className="text-sm text-ink-soft">{m.removeLead}</p>
        <FormField
          label={m.password}
          type="password"
          autoComplete="current-password"
          autoFocus
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
        {alert}
        <div className="flex flex-wrap gap-2">
          <Button type="submit" size="md" variant="outline" disabled={busy || !password}>
            {busy ? m.removing : m.remove}
          </Button>
          <Button type="button" size="md" variant="ghost" onClick={() => go({ kind: "idle" })}>
            {m.cancel}
          </Button>
        </div>
      </form>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      {status.phone && status.verifiedAt ? (
        <p className="flex items-start gap-2 rounded-[12px] bg-palm-soft p-3 text-sm font-semibold text-palm-deep">
          <CheckCircle2 size={18} className="mt-0.5 shrink-0" aria-hidden />
          {m.verified(status.display ?? status.phone)}
        </p>
      ) : (
        <p className="text-sm text-ink-soft">{status.available ? m.none : m.unavailable}</p>
      )}
      {notice && (
        <p role="status" className="text-sm font-medium text-palm-deep">
          {notice}
        </p>
      )}
      {status.phone && status.verifiedAt ? (
        <Button variant="outline" size="md" className="w-fit" onClick={() => go({ kind: "remove" })}>
          {m.remove}
        </Button>
      ) : (
        status.available && (
          <Button size="md" className="w-fit" onClick={() => go({ kind: "add" })}>
            <Smartphone size={16} aria-hidden /> {m.add}
          </Button>
        )
      )}
    </div>
  );
}

import { useState, type FormEvent } from "react";
import { CheckCircle2, Copy, Download, KeyRound } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { accountApi } from "@/lib/api/endpoints";
import { ApiError } from "@/lib/api/ApiError";
import { LoadingState, ErrorState } from "@/components/ui/States";
import { Button } from "@/components/ui/Button";
import { FormField } from "@/features/auth/FormField";
import { downloadFile } from "@/lib/utils/download";
import { useMessages } from "@/i18n/catalog";
import { authenticatorMessages } from "./authenticator.messages";

type Step =
  | { kind: "idle" }
  | { kind: "password" }
  | { kind: "key"; secret: string; otpauthUri: string }
  | { kind: "codes"; codes: string[] }
  | { kind: "turnOff" };

/**
 * M9a: an administrator turns on an authenticator app - the password, then the key typed into the app, then a first
 * code - and gets recovery codes once. The key and the codes live only in this component while they are shown.
 */
export function AuthenticatorApp() {
  const m = useMessages(authenticatorMessages);
  const status = useAsync(() => accountApi.secondFactor(), []);
  const [step, setStep] = useState<Step>({ kind: "idle" });
  const [password, setPassword] = useState("");
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  function go(next: Step) {
    setStep(next);
    setPassword("");
    setCode("");
    setError(null);
    setCopied(false);
  }

  async function run(action: () => Promise<void>) {
    setBusy(true);
    setError(null);
    try {
      await action();
    } catch (err) {
      if (err instanceof ApiError && err.validationErrors?.currentPassword) setError(m.wrongPassword);
      else if (err instanceof ApiError && [403, 409, 422, 423].includes(err.status) && err.problem?.detail) setError(err.problem.detail);
      else setError(m.failed);
    } finally {
      setBusy(false);
    }
  }

  function startSetup(e: FormEvent) {
    e.preventDefault();
    void run(async () => {
      const setup = await accountApi.setUpSecondFactor(password);
      go({ kind: "key", secret: setup.secret, otpauthUri: setup.otpauthUri });
    });
  }

  function activate(e: FormEvent) {
    e.preventDefault();
    void run(async () => {
      const { recoveryCodes } = await accountApi.activateSecondFactor(code.trim());
      go({ kind: "codes", codes: recoveryCodes });
    });
  }

  function turnOff(e: FormEvent) {
    e.preventDefault();
    void run(async () => {
      await accountApi.turnOffSecondFactor(password, code.trim());
      go({ kind: "idle" });
      status.refetch();
    });
  }

  async function copyKey(secret: string) {
    try {
      await navigator.clipboard.writeText(secret);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  }

  const alert = error && (
    <p role="alert" className="text-sm font-medium text-brick-deep">
      {error}
    </p>
  );

  if (step.kind === "password") {
    return (
      <form onSubmit={startSetup} noValidate className="flex flex-col gap-4">
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
          <Button type="submit" size="md" disabled={busy || !password}>
            {busy ? m.working : m.continue}
          </Button>
          <Button type="button" size="md" variant="outline" onClick={() => go({ kind: "idle" })}>
            {m.cancel}
          </Button>
        </div>
      </form>
    );
  }

  if (step.kind === "key") {
    return (
      <form onSubmit={activate} noValidate className="flex flex-col gap-4">
        <p className="text-sm leading-relaxed text-ink">{m.keyStep}</p>
        <div className="flex flex-wrap items-center gap-3">
          <code className="rounded-[12px] bg-tonle-50 px-4 py-3 font-mono text-[1.0625rem] font-semibold tracking-[0.12em] text-tonle-950 break-all">
            {step.secret.match(/.{1,4}/g)?.join(" ")}
          </code>
          <Button type="button" size="md" variant="outline" onClick={() => void copyKey(step.secret)}>
            <Copy size={16} aria-hidden /> {copied ? m.copied : m.copyKey}
          </Button>
        </div>
        <a href={step.otpauthUri} className="w-fit text-sm font-semibold text-tonle-700 underline underline-offset-2">
          {m.openInApp}
        </a>
        <p className="text-sm leading-relaxed text-ink">{m.codeStep}</p>
        <FormField
          label={m.code}
          inputMode="numeric"
          autoComplete="one-time-code"
          maxLength={6}
          required
          value={code}
          onChange={(e) => setCode(e.target.value)}
        />
        {alert}
        <div className="flex flex-wrap gap-2">
          <Button type="submit" size="md" disabled={busy || code.trim().length !== 6}>
            {busy ? m.working : m.turnOn}
          </Button>
          <Button type="button" size="md" variant="outline" onClick={() => go({ kind: "idle" })}>
            {m.cancel}
          </Button>
        </div>
      </form>
    );
  }

  if (step.kind === "codes") {
    return (
      <div className="flex flex-col gap-4">
        <h3 className="font-display text-base font-semibold text-tonle-950">{m.codesTitle}</h3>
        <p className="text-sm leading-relaxed text-ink-soft">{m.codesLead}</p>
        <ul className="grid grid-cols-2 gap-2 rounded-[12px] bg-tonle-50 p-4 font-mono text-[0.9375rem] font-semibold text-tonle-950 sm:max-w-sm">
          {step.codes.map((recovery) => (
            <li key={recovery}>{recovery}</li>
          ))}
        </ul>
        <div className="flex flex-wrap gap-2">
          <Button
            type="button"
            size="md"
            variant="outline"
            onClick={() => downloadFile("bookeyond-kid-recovery-codes.txt", m.codesFile(step.codes), "text/plain;charset=utf-8")}
          >
            <Download size={16} aria-hidden /> {m.download}
          </Button>
          <Button
            type="button"
            size="md"
            onClick={() => {
              go({ kind: "idle" });
              status.refetch();
            }}
          >
            <CheckCircle2 size={16} aria-hidden /> {m.saved}
          </Button>
        </div>
      </div>
    );
  }

  if (step.kind === "turnOff") {
    return (
      <form onSubmit={turnOff} noValidate className="flex flex-col gap-4">
        <p className="text-sm leading-relaxed text-ink-soft">{m.turnOffLead}</p>
        <FormField
          label={m.password}
          type="password"
          autoComplete="current-password"
          autoFocus
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
        <FormField
          label={m.codeOrRecovery}
          autoComplete="one-time-code"
          autoCapitalize="characters"
          spellCheck={false}
          maxLength={20}
          required
          value={code}
          onChange={(e) => setCode(e.target.value)}
        />
        {alert}
        <div className="flex flex-wrap gap-2">
          <Button type="submit" size="md" variant="outline" disabled={busy || !password || !code.trim()}>
            {busy ? m.working : m.turnOff}
          </Button>
          <Button type="button" size="md" variant="outline" onClick={() => go({ kind: "idle" })}>
            {m.cancel}
          </Button>
        </div>
      </form>
    );
  }

  if (status.isLoading && !status.data) return <LoadingState label={m.loading} />;
  if (status.error) return <ErrorState error={status.error} onRetry={status.refetch} />;
  const state = status.data;
  if (!state) return null;

  return (
    <div className="flex flex-col gap-4">
      {state.enabled ? (
        <p role="status" className="flex items-start gap-2 rounded-[12px] bg-palm-soft p-3 text-sm font-semibold text-palm-deep">
          <CheckCircle2 size={18} className="mt-0.5 shrink-0" aria-hidden />
          {m.on(state.recoveryCodesLeft)}
        </p>
      ) : (
        <p className="text-sm leading-relaxed text-ink">{state.pending ? m.pending : m.off}</p>
      )}
      {state.enabled && state.recoveryCodesLeft <= 2 && <p className="text-sm leading-relaxed text-ink-soft">{m.low}</p>}
      {state.enabled ? (
        <Button type="button" size="md" variant="outline" className="w-fit" onClick={() => go({ kind: "turnOff" })}>
          {m.turnOff}
        </Button>
      ) : (
        <Button type="button" size="md" className="w-fit" onClick={() => go({ kind: "password" })}>
          <KeyRound size={16} aria-hidden /> {state.pending ? m.startAgain : m.setUp}
        </Button>
      )}
      <p className="text-sm leading-relaxed text-ink-faint">{m.lostPhone}</p>
    </div>
  );
}

import { useEffect, useRef, useState, type FormEvent } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { ArrowRight } from "lucide-react";
import { useAuth } from "@/lib/auth/AuthContext";
import { childDevicesApi } from "@/lib/api/childDevices";
import { ApiError } from "@/lib/api/ApiError";
import { useMessages } from "@/i18n/catalog";
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";
import { LearningBuddy } from "@/features/buddy/LearningBuddy";
import { childDeviceEntryMessages } from "@/features/parent/childDevice.messages";
import { goButton, quietButton } from "./chips";
import { cn } from "@/lib/utils/cn";

/** How often the waiting device asks whether the grown-up said yes. */
const POLL_MS = 3000;

type Step = { kind: "enter" } | { kind: "waiting"; claimId: string; secret: string };

/**
 * M10a: a child's own device (a tablet, a shared phone) opening the child's learning space with a code a grown-up made.
 * It waits until the grown-up says yes on their own phone, then receives a sign-in that opens only that child's space.
 * Nothing about any child is shown before then.
 */
export function ChildDevicePage() {
  const m = useMessages(childDeviceEntryMessages);
  const { session, adoptChildDevice } = useAuth();
  const navigate = useNavigate();
  const [step, setStep] = useState<Step>({ kind: "enter" });
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement | null>(null);
  useDocumentTitle(m.title);

  useEffect(() => {
    if (step.kind !== "waiting") return;
    let stopped = false;
    const id = window.setInterval(async () => {
      try {
        const collected = await childDevicesApi.collect(step.claimId, step.secret);
        if (stopped || collected.status !== "SIGNED_IN") return;
        stopped = true;
        window.clearInterval(id);
        const opened = adoptChildDevice(collected);
        navigate(`/learn/${opened.childId}`, { replace: true });
      } catch (err) {
        // Waiting longer is fine when the network or the budget says so; an ended code starts over.
        if (err instanceof ApiError && (err.status === 400 || err.status === 409)) {
          stopped = true;
          window.clearInterval(id);
          setError(err.status === 409 ? m.codeEnded : m.codeWrong);
          setStep({ kind: "enter" });
        }
      }
    }, POLL_MS);
    return () => {
      stopped = true;
      window.clearInterval(id);
    };
  }, [step, adoptChildDevice, navigate, m.codeEnded, m.codeWrong]);

  if (session?.childId) return <Navigate to={`/learn/${session.childId}`} replace />;

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const claim = await childDevicesApi.claim(code.trim());
      setStep({ kind: "waiting", claimId: claim.claimId, secret: claim.secret });
    } catch (err) {
      setError(
        err instanceof ApiError && err.isRateLimited
          ? m.tryAgainSoon
          : err instanceof ApiError && err.status === 400
            ? m.codeWrong
            : m.failed,
      );
      inputRef.current?.focus();
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="flex min-h-dvh flex-col items-center justify-center gap-6 bg-cream px-4 py-10 text-center">
      <LearningBuddy mood={step.kind === "waiting" ? "happy" : "cheer"} size={140} />
      <div className="flex w-full max-w-md flex-col gap-4 rounded-[28px] bg-white px-6 py-8 shadow-card">
        <h1 className="font-display text-headline font-bold text-tonle-950">{m.title}</h1>
        {session && !session.childId ? (
          <>
            <p className="text-ink-soft">{m.grownUpHere}</p>
            <Link to="/parent" className={cn(quietButton, "mx-auto")}>
              {m.toGrownUpArea}
            </Link>
          </>
        ) : step.kind === "waiting" ? (
          <>
            <p role="status" className="text-lg font-medium text-ink">
              {m.waiting}
            </p>
            <button type="button" className={cn(quietButton, "mx-auto")} onClick={() => setStep({ kind: "enter" })}>
              {m.startOver}
            </button>
          </>
        ) : (
          <form onSubmit={submit} className="flex flex-col items-center gap-4">
            <p className="text-ink-soft">{m.lead}</p>
            <label className="flex flex-col items-center gap-2 font-semibold text-ink">
              {m.code}
              <input
                ref={inputRef}
                value={code}
                onChange={(e) => setCode(e.target.value.toUpperCase())}
                autoFocus
                autoComplete="one-time-code"
                autoCapitalize="characters"
                spellCheck={false}
                maxLength={8}
                className="h-16 w-56 rounded-[16px] border border-line-strong bg-white text-center font-mono text-[2rem] font-bold uppercase tracking-[0.3em] text-tonle-950 focus:outline-none focus-visible:ring-4 focus-visible:ring-tonle-300"
              />
            </label>
            {error && (
              <p role="alert" className="text-sm font-medium text-brick-deep">
                {error}
              </p>
            )}
            <button type="submit" disabled={busy || code.replace(/[\s-]/g, "").length < 6} className={cn(goButton, "disabled:opacity-60")}>
              {busy ? m.checking : m.next} <ArrowRight size={20} aria-hidden />
            </button>
          </form>
        )}
      </div>
    </main>
  );
}

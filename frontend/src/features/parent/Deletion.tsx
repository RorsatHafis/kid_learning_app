import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { Trash2, Undo2 } from "lucide-react";
import { accountApi, childrenApi } from "@/lib/api/endpoints";
import { ApiError } from "@/lib/api/ApiError";
import { Button } from "@/components/ui/Button";
import { FormField } from "@/features/auth/FormField";
import { ChildSeal } from "./ui";
import type { Child } from "@/lib/api/types";
import { deletionDay } from "./familyText";

function passwordProblem(err: unknown): string {
  if (err instanceof ApiError && err.status === 422) return "That isn't your current password.";
  if (err instanceof ApiError && err.status === 423)
    return "Too many incorrect attempts. For your security the account is locked for a while.";
  return "We couldn't do that just now. Please try again.";
}

/**
 * The quiet end of a child's page: deleting the child. Confirmed with the parent's password;
 * the child leaves learning and any school straight away and is erased after a waiting period,
 * during which the family can keep them.
 */
export function DeleteChildSection({ child }: { child: Child }) {
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [working, setWorking] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setWorking(true);
    setError(null);
    try {
      await childrenApi.requestDeletion(child.id, password);
      navigate("/parent", { replace: true });
    } catch (err) {
      setError(passwordProblem(err));
      setWorking(false);
    }
  }

  return (
    <section aria-labelledby="delete-child" className="flex flex-col gap-3 border-t border-line pt-8">
      <h2 id="delete-child" className="font-display text-lg font-semibold text-tonle-950">
        Delete {child.displayName}
      </h2>
      <p className="max-w-2xl text-sm leading-relaxed text-ink-soft">
        Deleting erases {child.displayName}&apos;s profile and everything about their learning — journeys, answers, skills and
        insights. {child.displayName} stops learning and any school&apos;s access ends straight away; we wait a few days before
        erasing, in case you change your mind. You can download your family&apos;s data from your account page first.
      </p>
      {open ? (
        <form onSubmit={onSubmit} noValidate aria-label={`Delete ${child.displayName}`} className="flex max-w-md flex-col gap-4">
          <FormField
            label="Your password"
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            error={error ?? undefined}
          />
          <div className="flex flex-wrap gap-3">
            <Button type="submit" size="md" disabled={working || !password}>
              <Trash2 size={16} aria-hidden /> {working ? "Deleting…" : `Delete ${child.displayName}`}
            </Button>
            <Button type="button" size="md" variant="ghost" onClick={() => setOpen(false)} disabled={working}>
              Cancel
            </Button>
          </div>
        </form>
      ) : (
        <Button size="md" variant="outline" onClick={() => setOpen(true)} className="w-fit">
          <Trash2 size={16} aria-hidden /> Delete {child.displayName}…
        </Button>
      )}
    </section>
  );
}

/** A child whose deletion is scheduled, as the family page shows them: the date, and a way to keep them. */
export function PendingChildChapter({ child, onKept }: { child: Child; onKept: () => void }) {
  const [working, setWorking] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function keep() {
    setWorking(true);
    setError(null);
    try {
      await childrenApi.keep(child.id);
      onKept();
    } catch (err) {
      setError(
        err instanceof ApiError && err.isPlanLimit
          ? `Your plan has no room for another child right now, so ${child.displayName} can't be kept.`
          : `We couldn't keep ${child.displayName} just now. Please try again.`,
      );
      setWorking(false);
    }
  }

  return (
    <article className="flex flex-wrap items-center gap-4 px-6 py-6 sm:px-8">
      <ChildSeal id={child.id} name={child.displayName} />
      <div className="min-w-0 flex-1">
        <h2 className="font-display text-xl font-semibold text-tonle-950">{child.displayName}</h2>
        <p className="text-sm text-ink-soft">
          Will be deleted on {child.eraseAfter ? deletionDay(child.eraseAfter) : "a date soon"}. Until then you can keep{" "}
          {child.displayName}.
        </p>
        {error && (
          <p role="alert" className="mt-1 text-sm font-medium text-brick-deep">
            {error}
          </p>
        )}
      </div>
      <Button size="md" variant="outline" onClick={keep} disabled={working}>
        <Undo2 size={16} aria-hidden /> {working ? "Keeping…" : `Keep ${child.displayName}`}
      </Button>
    </article>
  );
}

/**
 * Deleting the family account, on the account page. Nothing changes until the date, so the
 * parent can sign in and keep the account until then.
 */
export function DeleteAccount({ eraseAfter, onChanged }: { eraseAfter: string | null; onChanged: () => void }) {
  const [open, setOpen] = useState(false);
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [working, setWorking] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setWorking(true);
    setError(null);
    try {
      await accountApi.requestDeletion(password);
      setOpen(false);
      setPassword("");
      onChanged();
    } catch (err) {
      setError(passwordProblem(err));
    } finally {
      setWorking(false);
    }
  }

  async function keep() {
    setWorking(true);
    setError(null);
    try {
      await accountApi.keep();
      onChanged();
    } catch {
      setError("We couldn't keep your account just now. Please try again.");
    } finally {
      setWorking(false);
    }
  }

  if (eraseAfter) {
    return (
      <div className="flex flex-col items-start gap-3">
        <p role="status" className="rounded-[12px] bg-brick-soft p-3 text-sm font-semibold text-brick-deep">
          Your account and your family&apos;s data will be erased on {deletionDay(eraseAfter)}. Everything keeps working until
          then.
        </p>
        {error && (
          <p role="alert" className="text-sm font-medium text-brick-deep">
            {error}
          </p>
        )}
        <Button size="md" onClick={keep} disabled={working}>
          <Undo2 size={16} aria-hidden /> {working ? "Keeping…" : "Keep my account"}
        </Button>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <p className="text-sm text-ink-soft">
        Deleting your account erases it with your family: every child&apos;s profile and learning, your plan and your sign-in.
        Everything keeps working for a few days in case you change your mind; then it is erased and can&apos;t be undone.
      </p>
      {open ? (
        <form onSubmit={onSubmit} noValidate aria-label="Delete my account" className="flex flex-col gap-4">
          <FormField
            label="Your password"
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            error={error ?? undefined}
          />
          <div className="flex flex-wrap gap-3">
            <Button type="submit" size="md" disabled={working || !password}>
              <Trash2 size={16} aria-hidden /> {working ? "Deleting…" : "Delete my account"}
            </Button>
            <Button type="button" size="md" variant="ghost" onClick={() => setOpen(false)} disabled={working}>
              Cancel
            </Button>
          </div>
        </form>
      ) : (
        <Button size="md" variant="outline" onClick={() => setOpen(true)} className="w-fit">
          <Trash2 size={16} aria-hidden /> Delete my account…
        </Button>
      )}
    </div>
  );
}

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
import { messagesNow, useMessages } from "@/i18n/catalog";
import { familyMessages } from "./messages";

function passwordProblem(err: unknown): string {
  const m = messagesNow(familyMessages).deletion;
  if (err instanceof ApiError && err.status === 422) return m.wrongPassword;
  if (err instanceof ApiError && err.status === 423) return m.locked;
  return m.failed;
}

/**
 * The quiet end of a child's page: deleting the child. Confirmed with the parent's password;
 * the child leaves learning and any school straight away and is erased after a waiting period,
 * during which the family can keep them.
 */
export function DeleteChildSection({ child }: { child: Child }) {
  const navigate = useNavigate();
  const m = useMessages(familyMessages).deletion;
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
      <h2 id="delete-child" className="font-display text-lg font-semibold text-tonle-950 [overflow-wrap:anywhere]">
        {m.deleteChild(child.displayName)}
      </h2>
      <p className="max-w-2xl text-sm leading-relaxed text-ink-soft">{m.deleteChildExplain(child.displayName)}</p>
      {open ? (
        <form onSubmit={onSubmit} noValidate aria-label={m.deleteChild(child.displayName)} className="flex max-w-md flex-col gap-4">
          <FormField
            label={m.yourPassword}
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            error={error ?? undefined}
          />
          <div className="flex flex-wrap gap-3">
            <Button type="submit" size="md" className="max-w-full [overflow-wrap:anywhere]" disabled={working || !password}>
              <Trash2 size={16} aria-hidden /> {working ? m.deleting : m.deleteChild(child.displayName)}
            </Button>
            <Button type="button" size="md" variant="ghost" onClick={() => setOpen(false)} disabled={working}>
              {m.cancel}
            </Button>
          </div>
        </form>
      ) : (
        <Button size="md" variant="outline" onClick={() => setOpen(true)} className="w-fit max-w-full [overflow-wrap:anywhere]">
          <Trash2 size={16} aria-hidden /> {m.deleteChildOpen(child.displayName)}
        </Button>
      )}
    </section>
  );
}

/** A child whose deletion is scheduled, as the family page shows them: the date, and a way to keep them. */
export function PendingChildChapter({ child, onKept }: { child: Child; onKept: () => void }) {
  const m = useMessages(familyMessages).deletion;
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
        err instanceof ApiError && err.isPlanLimit ? m.noRoom(child.displayName) : m.keepFailed(child.displayName),
      );
      setWorking(false);
    }
  }

  return (
    <article className="flex flex-wrap items-center gap-4 px-6 py-6 sm:px-8">
      <ChildSeal id={child.id} name={child.displayName} />
      <div className="min-w-0 flex-1">
        <h2 className="font-display text-xl font-semibold text-tonle-950">{child.displayName}</h2>
        <p className="text-sm text-ink-soft">{m.willBeDeleted(child.eraseAfter ? deletionDay(child.eraseAfter) : null, child.displayName)}</p>
        {error && (
          <p role="alert" className="mt-1 text-sm font-medium text-brick-deep">
            {error}
          </p>
        )}
      </div>
      <Button size="md" variant="outline" onClick={keep} disabled={working}>
        <Undo2 size={16} aria-hidden /> {working ? m.keeping : m.keepChild(child.displayName)}
      </Button>
    </article>
  );
}

/**
 * Deleting the family account, on the account page. Nothing changes until the date, so the
 * parent can sign in and keep the account until then.
 */
export function DeleteAccount({ eraseAfter, onChanged }: { eraseAfter: string | null; onChanged: () => void }) {
  const m = useMessages(familyMessages).deletion;
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
      setError(m.keepAccountFailed);
    } finally {
      setWorking(false);
    }
  }

  if (eraseAfter) {
    return (
      <div className="flex flex-col items-start gap-3">
        <p role="status" className="rounded-[12px] bg-brick-soft p-3 text-sm font-semibold text-brick-deep">
          {m.accountScheduled(deletionDay(eraseAfter))}
        </p>
        {error && (
          <p role="alert" className="text-sm font-medium text-brick-deep">
            {error}
          </p>
        )}
        <Button size="md" onClick={keep} disabled={working}>
          <Undo2 size={16} aria-hidden /> {working ? m.keeping : m.keepAccount}
        </Button>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <p className="text-sm text-ink-soft">{m.accountExplain}</p>
      {open ? (
        <form onSubmit={onSubmit} noValidate aria-label={m.deleteAccount} className="flex flex-col gap-4">
          <FormField
            label={m.yourPassword}
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            error={error ?? undefined}
          />
          <div className="flex flex-wrap gap-3">
            <Button type="submit" size="md" disabled={working || !password}>
              <Trash2 size={16} aria-hidden /> {working ? m.deleting : m.deleteAccount}
            </Button>
            <Button type="button" size="md" variant="ghost" onClick={() => setOpen(false)} disabled={working}>
              {m.cancel}
            </Button>
          </div>
        </form>
      ) : (
        <Button size="md" variant="outline" onClick={() => setOpen(true)} className="w-fit">
          <Trash2 size={16} aria-hidden /> {m.deleteAccountOpen}
        </Button>
      )}
    </div>
  );
}

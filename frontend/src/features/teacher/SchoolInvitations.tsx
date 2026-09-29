import { useState } from "react";
import { CheckCircle2, School } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { schoolApi } from "@/lib/api/endpoints";
import { Button } from "@/components/ui/Button";
import type { SchoolInvitation } from "@/lib/api/types";

/**
 * Schools that have invited this teacher. Accepting puts them on the school's staff (the
 * principal can then assign classes); declining closes the invitation. Renders nothing
 * when there are none, or if they can't be loaded — the class list below still works.
 */
export function SchoolInvitations({ onAccepted }: { onAccepted: () => void }) {
  const invitations = useAsync(() => schoolApi.myInvitations(), []);
  const [joined, setJoined] = useState<string | null>(null);

  if (joined && (!invitations.data || invitations.data.length === 0)) {
    return (
      <p role="status" className="flex items-start gap-2 rounded-[10px] bg-palm-soft px-5 py-4 text-sm font-semibold text-palm-deep">
        <CheckCircle2 size={18} className="mt-0.5 shrink-0" aria-hidden />
        You've joined {joined}. Your classes will appear here once the principal assigns you to them.
      </p>
    );
  }
  if (!invitations.data || invitations.data.length === 0) return null;

  return (
    <section aria-labelledby="invitations-heading" className="flex flex-col gap-3">
      <h2 id="invitations-heading" className="text-base font-semibold text-tonle-950">
        Invitations
      </h2>
      <ul className="flex flex-col gap-3">
        {invitations.data.map((invitation) => (
          <Invitation
            key={invitation.id}
            invitation={invitation}
            onAnswered={(accepted) => {
              invitations.refetch();
              if (accepted) {
                setJoined(invitation.schoolName);
                onAccepted();
              }
            }}
          />
        ))}
      </ul>
    </section>
  );
}

function Invitation({ invitation, onAnswered }: { invitation: SchoolInvitation; onAnswered: (accepted: boolean) => void }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function answer(accept: boolean) {
    setBusy(true);
    setError(null);
    try {
      await (accept ? schoolApi.acceptInvitation(invitation.id) : schoolApi.declineInvitation(invitation.id));
      onAnswered(accept);
    } catch {
      setError("We couldn't record your answer. The invitation may have been withdrawn — refresh to check.");
      setBusy(false);
    }
  }

  return (
    <li className="flex flex-col gap-3 rounded-[10px] border border-tonle-400/40 bg-tonle-50/50 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
      <div className="flex items-start gap-3">
        <School size={18} className="mt-0.5 shrink-0 text-tonle-700" aria-hidden />
        <p className="text-sm text-ink">
          <strong>{invitation.schoolName}</strong> invited you to join its teachers.
        </p>
      </div>
      <div className="flex items-center gap-2">
        <Button size="md" disabled={busy} onClick={() => answer(true)}>
          Accept
        </Button>
        <Button size="md" variant="ghost" disabled={busy} onClick={() => answer(false)}>
          Decline
        </Button>
      </div>
      {error && (
        <p role="alert" className="text-sm font-medium text-brick-deep sm:basis-full">
          {error}
        </p>
      )}
    </li>
  );
}

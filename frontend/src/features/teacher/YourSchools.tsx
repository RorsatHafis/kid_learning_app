import { useState } from "react";
import { LogOut, School } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { schoolApi } from "@/lib/api/endpoints";
import { problemMessage } from "@/lib/api/problemMessage";
import { Button } from "@/components/ui/Button";
import type { SchoolMembership } from "@/lib/api/types";

/**
 * The schools whose staff this teacher is on (Y21), and a way to leave one: their classes there are
 * unassigned at once; nothing a child has changes. Renders nothing while there are none or if they
 * can't be loaded - the classes above still work.
 */
export function YourSchools({ onLeft }: { onLeft: () => void }) {
  const { data, refetch } = useAsync(() => schoolApi.myMemberships(), []);
  const [left, setLeft] = useState<string | null>(null);
  if (!data || (data.length === 0 && !left)) return null;
  return (
    <section aria-labelledby="your-schools" className="flex flex-col gap-3">
      <h2 id="your-schools" className="text-sm font-semibold text-ink-soft">
        Your schools
      </h2>
      {left && (
        <p role="status" className="text-sm font-medium text-palm-deep">
          You have left {left}.
        </p>
      )}
      {data.length > 0 && (
        <ul className="flex flex-col divide-y divide-line overflow-hidden rounded-[14px] border border-line bg-white">
          {data.map((membership) => (
            <Membership
              key={membership.id}
              membership={membership}
              onLeft={() => {
                setLeft(membership.schoolName);
                refetch();
                onLeft();
              }}
            />
          ))}
        </ul>
      )}
    </section>
  );
}

function Membership({ membership, onLeft }: { membership: SchoolMembership; onLeft: () => void }) {
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function leave() {
    setBusy(true);
    setError(null);
    try {
      await schoolApi.leaveSchool(membership.id);
      onLeft();
    } catch (err) {
      setError(problemMessage(err, "That didn't work. Please try again."));
      setBusy(false);
    }
  }

  return (
    <li className="flex flex-col gap-3 px-5 py-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <span className="flex items-center gap-2.5 font-semibold text-ink">
          <School size={17} className="text-tonle-700" aria-hidden /> {membership.schoolName}
        </span>
        {!confirming && (
          <Button variant="ghost" size="sm" onClick={() => setConfirming(true)}>
            <LogOut size={15} aria-hidden /> Leave…<span className="sr-only"> {membership.schoolName}</span>
          </Button>
        )}
      </div>
      {confirming && (
        <div role="group" aria-label={`Leave ${membership.schoolName}`} className="flex flex-col gap-3">
          <p className="text-sm text-ink">
            You stop teaching its classes at once and no longer see their learning. Nothing the children have changes, and its
            principal can invite you again.
          </p>
          <div className="flex flex-wrap gap-2">
            <Button size="sm" disabled={busy} onClick={() => void leave()}>
              {busy ? "Leaving…" : "Leave the school"}
            </Button>
            <Button size="sm" variant="ghost" disabled={busy} onClick={() => setConfirming(false)}>
              Stay
            </Button>
          </div>
        </div>
      )}
      {error && (
        <p role="alert" className="text-sm font-medium text-brick-deep">
          {error}
        </p>
      )}
    </li>
  );
}

import { useState } from "react";
import { FileText } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { accountApi } from "@/lib/api/endpoints";
import { useLegal } from "./useLegal";
import { Button } from "@/components/ui/Button";
import { cn } from "@/lib/utils/cn";
import { useMessages } from "@/i18n/catalog";
import { legalMessages } from "./messages";

/*
 * The privacy notice and terms of use (Y15, PD-18). The owner publishes the texts and configures
 * where they are; until then every piece here renders nothing - no placeholder pretends they exist.
 */

/** "Privacy notice · Terms of use", opening the owner's documents in a new tab. */
export function LegalLinks({ className }: { className?: string }) {
  const legal = useLegal();
  const m = useMessages(legalMessages);
  if (!legal.data?.version) return null;
  return (
    <span className={cn("inline-flex flex-wrap gap-x-3 gap-y-1", className)}>
      <a href={legal.data.privacyUrl!} target="_blank" rel="noreferrer" className="inline-flex min-h-6 items-center underline-offset-4 hover:underline">
        {m.privacy}
      </a>
      <a href={legal.data.termsUrl!} target="_blank" rel="noreferrer" className="inline-flex min-h-6 items-center underline-offset-4 hover:underline">
        {m.terms}
      </a>
    </span>
  );
}

/**
 * Shown to a signed-in account that hasn't agreed to the current version - the documents changed,
 * or the account was created before there were any. It doesn't block anything: what follows from
 * not agreeing is the owner's legal decision, not the product's.
 */
export function LegalUpdateNotice() {
  const legal = useLegal();
  const m = useMessages(legalMessages);
  const profile = useAsync(() => accountApi.me(), []);
  const [done, setDone] = useState(false);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState(false);
  const version = legal.data?.version;
  if (!version || !profile.data || profile.data.legalVersionAccepted === version || done) return null;

  async function agree() {
    setWorking(true);
    setError(false);
    try {
      await accountApi.acceptLegal(version!);
      setDone(true);
    } catch {
      setError(true);
    } finally {
      setWorking(false);
    }
  }

  return (
    <div role="region" aria-label={m.region} className="border-b border-tonle-100 bg-tonle-50">
      <div className="mx-auto flex max-w-[1180px] flex-wrap items-center gap-x-4 gap-y-2 px-4 py-2.5 text-sm text-ink sm:px-8">
        <FileText size={16} className="shrink-0 text-tonle-600" aria-hidden />
        <span className="min-w-0 flex-1">
          {m.pleaseAgree}{" "}
          <a
            href={legal.data!.termsUrl!}
            target="_blank"
            rel="noreferrer"
            className="font-semibold text-tonle-700 underline underline-offset-4"
          >
            {m.termsLink}
          </a>{" "}
          {m.and}{" "}
          <a
            href={legal.data!.privacyUrl!}
            target="_blank"
            rel="noreferrer"
            className="font-semibold text-tonle-700 underline underline-offset-4"
          >
            {m.privacyLink}
          </a>
          {m.end}
          {error && <span className="ml-1 font-medium text-brick-deep">{m.failed}</span>}
        </span>
        <Button size="sm" onClick={agree} disabled={working}>
          {working ? m.saving : m.agree}
        </Button>
      </div>
    </div>
  );
}

/** The sign-up form's agreement, when there are documents to agree to. */
export function LegalAgreement({
  checked,
  onChange,
  error,
}: {
  checked: boolean;
  onChange: (v: boolean) => void;
  error?: string;
}) {
  const legal = useLegal();
  const m = useMessages(legalMessages);
  if (!legal.data?.version) return null;
  return (
    <div className="flex flex-col gap-1">
      <label className="flex items-start gap-2 text-sm text-ink">
        <input
          type="checkbox"
          className="mt-0.5 h-4 w-4 accent-tonle-700"
          checked={checked}
          onChange={(e) => onChange(e.target.checked)}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? "legal-agreement-error" : undefined}
        />
        <span>
          {m.iAgreeTo}{" "}
          <a
            href={legal.data.termsUrl!}
            target="_blank"
            rel="noreferrer"
            className="font-semibold text-tonle-700 underline underline-offset-4"
          >
            {m.termsLink}
          </a>{" "}
          {m.and}{" "}
          <a
            href={legal.data.privacyUrl!}
            target="_blank"
            rel="noreferrer"
            className="font-semibold text-tonle-700 underline underline-offset-4"
          >
            {m.privacyLink}
          </a>
          {m.end}
        </span>
      </label>
      {error && (
        <span id="legal-agreement-error" className="text-[0.8125rem] font-medium text-brick-deep">
          {error}
        </span>
      )}
    </div>
  );
}

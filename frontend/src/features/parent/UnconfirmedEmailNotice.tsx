import { Link, useLocation } from "react-router-dom";
import { MailWarning } from "lucide-react";
import { useAsync } from "@/lib/hooks/useAsync";
import { accountApi } from "@/lib/api/endpoints";

/**
 * A quiet reminder while the sign-in address is unconfirmed — password-reset links go
 * there, so a mistyped address is how a parent would lose access for good. Hidden on the
 * account page, which has the control itself, and silent if the account can't be read.
 */
export function UnconfirmedEmailNotice() {
  const { pathname } = useLocation();
  const profile = useAsync(() => accountApi.me(), []);

  if (pathname.startsWith("/parent/account") || !profile.data || profile.data.emailVerifiedAt !== null) {
    return null;
  }

  return (
    <div className="border-b border-hairline bg-brand-amberTint">
      <p className="mx-auto flex max-w-[1100px] flex-wrap items-center gap-x-2 gap-y-1 px-4 py-2.5 text-sm text-ink sm:px-8">
        <MailWarning size={16} className="shrink-0 text-brand-amberStrong" aria-hidden />
        <span>
          Please confirm <strong className="break-all">{profile.data.email}</strong> — it's where we'd send a link if you
          ever forget your password.
        </span>
        <Link to="/parent/account" className="font-semibold text-brand-blueStrong underline-offset-2 hover:underline">
          Confirm email
        </Link>
      </p>
    </div>
  );
}

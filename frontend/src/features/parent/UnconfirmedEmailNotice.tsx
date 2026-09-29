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
    <div className="border-b border-tonle-100 bg-tonle-50">
      <p className="mx-auto flex max-w-[1180px] items-start gap-2 px-4 py-2.5 text-sm text-ink sm:items-center sm:px-8">
        <MailWarning size={16} className="mt-0.5 shrink-0 text-tonle-600 sm:mt-0" aria-hidden />
        <span className="min-w-0 flex-1 sm:flex-none">
          <span className="sm:hidden">Confirm your email so password-reset links reach you. </span>
          <span className="hidden sm:inline">
            Please confirm <strong className="break-all">{profile.data.email}</strong> — it's where we'd send a link if you
            ever forget your password.{" "}
          </span>
        </span>
        <Link to="/parent/account" className="font-semibold text-tonle-700 underline decoration-tonle-300 underline-offset-4 hover:decoration-tonle-700">
          Confirm email
        </Link>
      </p>
    </div>
  );
}

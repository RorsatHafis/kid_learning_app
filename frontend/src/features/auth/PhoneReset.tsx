import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { FormField } from "./FormField";
import { Button } from "@/components/ui/Button";
import { authApi } from "@/lib/api/endpoints";
import { ApiError, rateLimitedMessage } from "@/lib/api/ApiError";
import { useMessages } from "@/i18n/catalog";
import { phoneResetMessages } from "./phoneReset.messages";

const MIN_PASSWORD = 12; // identity/service/PasswordPolicy.MIN_LENGTH

/**
 * M9b (D-15): a forgotten password reset with a code texted to the account's verified phone. Like the email link, the
 * server answers the same whether or not the number has an account; this page never says which.
 */
export function PhoneReset() {
  const m = useMessages(phoneResetMessages);
  const navigate = useNavigate();
  const [phone, setPhone] = useState("");
  const [sent, setSent] = useState(false);
  const [code, setCode] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function explain(err: unknown) {
    if (err instanceof ApiError && err.isRateLimited) return rateLimitedMessage(err);
    if (err instanceof ApiError && err.status === 503) return m.unavailable;
    if (err instanceof ApiError && err.status === 422 && err.problem?.detail) return err.problem.detail;
    return m.failed;
  }

  async function send(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await authApi.requestPhoneReset(phone.trim());
      setSent(true);
    } catch (err) {
      setError(explain(err));
    } finally {
      setBusy(false);
    }
  }

  async function reset(e: FormEvent) {
    e.preventDefault();
    if (password !== confirm) {
      setError(m.mismatch);
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await authApi.resetWithPhoneCode(phone.trim(), code.trim(), password);
      navigate("/login", { replace: true, state: { notice: "passwordReset" } });
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

  if (!sent) {
    return (
      <form onSubmit={send} className="flex flex-col gap-4">
        <FormField
          label={m.phone}
          hint={m.phoneHint}
          type="tel"
          inputMode="tel"
          autoComplete="tel"
          required
          maxLength={40}
          value={phone}
          onChange={(e) => setPhone(e.target.value)}
        />
        {alert}
        <Button type="submit" size="lg" disabled={busy || !phone.trim()} className="mt-2 w-full">
          {busy ? m.sending : m.send}
        </Button>
      </form>
    );
  }

  return (
    <form onSubmit={reset} className="flex flex-col gap-4">
      <p role="status" className="rounded-[14px] bg-tonle-50 p-4 text-sm text-ink ring-1 ring-tonle-100">
        {m.sent}
      </p>
      <FormField
        label={m.code}
        inputMode="numeric"
        autoComplete="one-time-code"
        required
        maxLength={6}
        value={code}
        onChange={(e) => setCode(e.target.value)}
      />
      <FormField
        label={m.newPassword(MIN_PASSWORD)}
        type="password"
        autoComplete="new-password"
        required
        value={password}
        onChange={(e) => setPassword(e.target.value)}
      />
      <FormField
        label={m.confirm}
        type="password"
        autoComplete="new-password"
        required
        value={confirm}
        onChange={(e) => setConfirm(e.target.value)}
      />
      {alert}
      <Button
        type="submit"
        size="lg"
        disabled={busy || code.trim().length !== 6 || password.length < MIN_PASSWORD || !confirm}
        className="mt-2 w-full"
      >
        {busy ? m.resetting : m.reset}
      </Button>
    </form>
  );
}

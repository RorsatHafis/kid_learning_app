import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { AuthLayout } from "./AuthLayout";
import { FormField } from "./FormField";
import { Button } from "@/components/ui/Button";
import { cn } from "@/lib/utils/cn";
import { useAuth } from "@/lib/auth/AuthContext";
import { ApiError, rateLimitedMessage } from "@/lib/api/ApiError";
import { roleHomePath } from "./roleHomePath";

export function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();

  const [role, setRole] = useState<"PARENT" | "PRINCIPAL">("PARENT");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [familyName, setFamilyName] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [pendingApproval, setPendingApproval] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setFormError(null);
    setFieldErrors({});
    setIsSubmitting(true);
    try {
      const result = await register({ email, password, familyName: familyName || undefined, role });
      if (result === "PENDING_APPROVAL") {
        setPendingApproval(true);
      } else {
        navigate(roleHomePath(result.platformRole), { replace: true });
      }
    } catch (err) {
      if (err instanceof ApiError && err.validationErrors) {
        setFieldErrors(err.validationErrors);
      } else if (err instanceof ApiError && err.isRateLimited) {
        setFormError(rateLimitedMessage(err));
      } else if (err instanceof ApiError) {
        setFormError(err.problem?.detail ?? "Couldn't create that account.");
      } else {
        setFormError("Something went wrong. Please try again.");
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  if (pendingApproval) {
    return (
      <AuthLayout title="Application received" subtitle="Your principal account is awaiting approval.">
        <p className="text-center text-sm leading-relaxed text-muted">
          We've received your school administrator application. An existing Bookeyond Kid admin needs to approve it
          before you can log in. Once it's approved, log in with{" "}
          <span className="font-semibold text-ink">{email}</span>.
        </p>
        <Link to="/" className="mt-6 block text-center text-sm font-semibold text-brand-blue">
          Back to home
        </Link>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout title="Create your account" subtitle="Start your family's learning journey with Bookeyond Kid.">
      <div className="mb-6 flex rounded-pill border border-hairline p-1">
        <button
          type="button"
          onClick={() => setRole("PARENT")}
          className={cn("flex-1 rounded-pill py-2 text-sm font-semibold transition-colors", role === "PARENT" ? "bg-brand-blue text-white" : "text-muted")}
        >
          I'm a parent
        </button>
        <button
          type="button"
          onClick={() => setRole("PRINCIPAL")}
          className={cn("flex-1 rounded-pill py-2 text-sm font-semibold transition-colors", role === "PRINCIPAL" ? "bg-brand-blue text-white" : "text-muted")}
        >
          I'm a principal
        </button>
      </div>

      <form onSubmit={onSubmit} className="flex flex-col gap-4">
        <FormField
          label={role === "PARENT" ? "Family name (optional)" : "School name"}
          value={familyName}
          onChange={(e) => setFamilyName(e.target.value)}
          error={fieldErrors.familyName}
        />
        <FormField label="Email" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} error={fieldErrors.email} />
        <FormField
          label="Password"
          type="password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={fieldErrors.password}
        />
        {formError && <p className="text-sm font-medium text-brand-redStrong">{formError}</p>}
        {role === "PRINCIPAL" && (
          <p className="text-xs text-muted">Principal accounts are reviewed by a Bookeyond Kid admin before they can log in.</p>
        )}
        <Button type="submit" size="lg" disabled={isSubmitting} className="mt-2 w-full">
          {isSubmitting ? "Creating account…" : "Create account"}
        </Button>
      </form>
      <p className="mt-6 text-center text-sm text-muted">
        Already have an account?{" "}
        <Link to="/login" className="font-semibold text-brand-blue">
          Log in
        </Link>
      </p>
    </AuthLayout>
  );
}

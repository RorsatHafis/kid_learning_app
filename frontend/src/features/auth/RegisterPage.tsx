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
        <p className="text-center text-sm leading-relaxed text-ink-soft">
          We've received your school administrator application. An existing Bookeyond Kid admin needs to approve it
          before you can log in. Once it's approved, log in with{" "}
          <span className="font-semibold text-ink">{email}</span>.
        </p>
        <Link to="/" className="mt-6 block text-center text-sm font-semibold text-tonle-700">
          Back to home
        </Link>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout title="Create your account" subtitle="Start your family's learning journey with Bookeyond Kid.">
      <div role="group" aria-label="Account type" className="mb-7 grid grid-cols-2 gap-1 rounded-[14px] bg-tonle-50 p-1 ring-1 ring-inset ring-line">
        <button
          type="button"
          aria-pressed={role === "PARENT"}
          onClick={() => setRole("PARENT")}
          className={cn("h-10 rounded-[10px] text-sm font-semibold transition-[background-color,color,box-shadow] duration-150", role === "PARENT" ? "bg-white text-tonle-900 shadow-tight ring-1 ring-line" : "text-ink-soft hover:text-tonle-900")}
        >
          I'm a parent
        </button>
        <button
          type="button"
          aria-pressed={role === "PRINCIPAL"}
          onClick={() => setRole("PRINCIPAL")}
          className={cn("h-10 rounded-[10px] text-sm font-semibold transition-[background-color,color,box-shadow] duration-150", role === "PRINCIPAL" ? "bg-white text-tonle-900 shadow-tight ring-1 ring-line" : "text-ink-soft hover:text-tonle-900")}
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
        {formError && <p className="text-sm font-medium text-brick-deep">{formError}</p>}
        {role === "PRINCIPAL" && (
          <p className="text-xs text-ink-soft">Principal accounts are reviewed by a Bookeyond Kid admin before they can log in.</p>
        )}
        <Button type="submit" size="lg" disabled={isSubmitting} className="mt-2 w-full">
          {isSubmitting ? "Creating account…" : "Create account"}
        </Button>
      </form>
      <p className="mt-6 text-center text-sm text-ink-soft">
        Already have an account?{" "}
        <Link to="/login" className="font-semibold text-tonle-700">
          Log in
        </Link>
      </p>
    </AuthLayout>
  );
}

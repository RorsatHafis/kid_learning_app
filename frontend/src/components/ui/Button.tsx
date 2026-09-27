import type { ButtonHTMLAttributes } from "react";
import { cn } from "@/lib/utils/cn";

type Variant = "solid" | "outline" | "ghost";
type Size = "md" | "lg";

const variantClasses: Record<Variant, string> = {
  solid: "bg-brand-blueStrong text-white shadow-soft hover:bg-blue-700",
  outline: "border-[1.5px] border-ink text-ink hover:bg-ink hover:text-white",
  ghost: "text-ink hover:text-brand-blueStrong",
};

const sizeClasses: Record<Size, string> = {
  md: "px-5 py-3 text-sm",
  lg: "px-8 py-[18px] text-base",
};

export function Button({
  variant = "solid",
  size = "md",
  className,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant; size?: Size }) {
  return (
    <button
      className={cn(
        "inline-flex items-center justify-center gap-2 rounded-pill font-semibold transition-colors",
        "disabled:cursor-not-allowed disabled:opacity-50",
        variantClasses[variant],
        sizeClasses[size],
        className,
      )}
      {...props}
    />
  );
}

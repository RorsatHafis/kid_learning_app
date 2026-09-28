import type { ButtonHTMLAttributes } from "react";
import { cn } from "@/lib/utils/cn";

/**
 * Adult-surface buttons (parent, school, admin). Precise rounded rectangles in the blue
 * system; the rounded pill and the warm "light" belong to the child space.
 *   solid   — the one primary action in a region
 *   outline — secondary actions
 *   ghost   — tertiary / inline actions
 */
type Variant = "solid" | "outline" | "ghost";
type Size = "sm" | "md" | "lg";

const variantClasses: Record<Variant, string> = {
  solid: "bg-tonle-800 text-white shadow-tight hover:bg-tonle-900",
  outline: "bg-white text-ink ring-1 ring-inset ring-line-strong hover:bg-paper hover:ring-tonle-300",
  ghost: "text-tonle-700 hover:bg-tonle-50",
};

const sizeClasses: Record<Size, string> = {
  sm: "h-9 px-3.5 text-[0.8125rem] rounded-[10px]",
  md: "h-11 px-5 text-[0.9375rem] rounded-[12px]",
  lg: "h-[3.25rem] px-7 text-base rounded-[14px]",
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
        "inline-flex select-none items-center justify-center gap-2 whitespace-nowrap font-semibold",
        "transition-[background-color,box-shadow,transform] duration-150 ease-out active:scale-[0.98]",
        "disabled:pointer-events-none disabled:opacity-45",
        variantClasses[variant],
        sizeClasses[size],
        className,
      )}
      {...props}
    />
  );
}

/** The same styles for router links that look like buttons. */
export function buttonClasses(variant: Variant = "solid", size: Size = "md", className?: string) {
  return cn(
    "inline-flex select-none items-center justify-center gap-2 whitespace-nowrap font-semibold transition-[background-color,box-shadow,transform] duration-150 ease-out active:scale-[0.98]",
    variantClasses[variant],
    sizeClasses[size],
    className,
  );
}

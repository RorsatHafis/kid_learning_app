import type { ButtonHTMLAttributes } from "react";
import { cn } from "@/lib/utils/cn";

/**
 * Adult-surface buttons (parent, school, admin). Precise rounded rectangles in the blue
 * system; the rounded pill and the warm "light" belong to the child space.
 *   solid   — the one primary action in a region
 *   outline — secondary actions
 *   ghost   — tertiary / inline actions
 */
type Variant = "solid" | "accent" | "outline" | "ghost";
type Size = "sm" | "md" | "lg";

const variantClasses: Record<Variant, string> = {
  solid: "bg-tonle-900 text-white shadow-button hover:bg-tonle-950",
  // The marigold invitation (Y38): one per page, where a family or visitor starts something.
  accent: "bg-light text-tonle-950 shadow-[inset_0_1px_0_rgba(255,255,255,0.35),0_1px_2px_rgba(138,74,11,0.3),0_8px_18px_-10px_rgba(199,122,30,0.8)] hover:bg-light-glow",
  outline: "bg-white text-tonle-950 ring-1 ring-inset ring-line-strong hover:bg-paper hover:ring-tonle-300",
  ghost: "text-tonle-700 hover:bg-tonle-50",
};

// Y38: adult actions are pills - the product's one shape for "do this" - with room for Khmer.
const sizeClasses: Record<Size, string> = {
  sm: "min-h-9 px-4 text-[0.8125rem] rounded-full",
  md: "min-h-11 px-5 text-[0.9375rem] rounded-full",
  lg: "min-h-[3.25rem] px-7 text-base rounded-full",
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
        "inline-flex select-none items-center justify-center gap-2 font-semibold leading-tight",
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
    "inline-flex select-none items-center justify-center gap-2 font-semibold leading-tight transition-[background-color,box-shadow,transform] duration-150 ease-out active:scale-[0.98]",
    variantClasses[variant],
    sizeClasses[size],
    className,
  );
}

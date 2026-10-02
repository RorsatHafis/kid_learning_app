import { useEffect, useId, useRef, type ReactNode } from "react";
import { X } from "lucide-react";
import { cn } from "@/lib/utils/cn";

/**
 * A modal on the browser's own <dialog>: focus moves in and stays in, Escape closes it, the page
 * behind is inert, and focus returns to what opened it. Rendered only while open.
 */
export function Dialog({
  open,
  onClose,
  title,
  description,
  children,
  footer,
  size = "md",
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  description?: string;
  children: ReactNode;
  footer?: ReactNode;
  size?: "md" | "lg";
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const descriptionId = useId();

  useEffect(() => {
    const dialog = ref.current;
    if (!open || !dialog) return;
    const opener = document.activeElement as HTMLElement | null;
    // jsdom (tests) has no showModal: fall back to an open, labelled dialog.
    if (typeof dialog.showModal === "function") {
      if (!dialog.open) dialog.showModal();
    } else {
      dialog.setAttribute("open", "");
    }
    return () => {
      if (typeof dialog.close === "function" && dialog.open) dialog.close();
      opener?.focus?.();
    };
  }, [open]);

  if (!open) return null;
  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      aria-describedby={description ? descriptionId : undefined}
      onCancel={(e) => {
        e.preventDefault();
        onClose();
      }}
      onClick={(e) => {
        // A click on the backdrop (the dialog element itself, outside its panel) closes it.
        if (e.target === e.currentTarget) onClose();
      }}
      className={cn(
        "m-auto max-h-[min(90dvh,52rem)] w-[calc(100%-2rem)] overflow-hidden rounded-[20px] bg-white p-0 text-ink shadow-lift",
        "backdrop:bg-tonle-950/45 backdrop:backdrop-blur-[2px] motion-safe:animate-pop-in",
        size === "lg" ? "max-w-4xl" : "max-w-lg",
      )}
    >
      <div className="flex max-h-[inherit] flex-col">
        <header className="flex items-start justify-between gap-4 border-b border-line px-6 py-4">
          <div>
            <h2 id={titleId} className="font-display text-lg font-semibold text-tonle-950">
              {title}
            </h2>
            {description && (
              <p id={descriptionId} className="mt-0.5 text-sm text-ink-soft">
                {description}
              </p>
            )}
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="-mr-2 rounded-full p-2 text-ink-soft hover:bg-paper hover:text-ink"
          >
            <X size={18} aria-hidden />
          </button>
        </header>
        <div className="min-h-0 flex-1 overflow-y-auto px-6 py-5">{children}</div>
        {footer && <footer className="flex flex-wrap justify-end gap-2 border-t border-line px-6 py-4">{footer}</footer>}
      </div>
    </dialog>
  );
}

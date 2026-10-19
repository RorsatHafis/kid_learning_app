import { ArrowDownToLine, Link2, ListOrdered } from "lucide-react";

/**
 * How to answer, shown with a picture of the gesture as well as words (Y39): a child who can't read
 * the sentence yet still sees "put in order", "join pairs" or "put in a basket". The words stay the
 * accessible text; the picture is decorative.
 */
const PICTURE = { order: ListOrdered, match: Link2, sort: ArrowDownToLine } as const;

export function HowTo({ kind, children }: { kind: keyof typeof PICTURE; children: string }) {
  const Icon = PICTURE[kind];
  return (
    <p className="mx-auto flex w-fit max-w-full items-center gap-2.5 rounded-pill bg-tonle-50 py-1.5 pl-1.5 pr-4 text-center text-base font-bold text-tonle-800 ring-1 ring-tonle-100">
      <span aria-hidden className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-white text-tonle-600 shadow-tight">
        <Icon size={17} strokeWidth={2.4} />
      </span>
      <span className="text-left">{children}</span>
    </p>
  );
}

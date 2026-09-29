import { useEffect, useRef, useState } from "react";
import { Search } from "lucide-react";
import { useDebouncedValue } from "@/lib/hooks/useDebouncedValue";
import { cn } from "@/lib/utils/cn";

/**
 * A search box for a server-searched list. It reports the trimmed text once typing pauses, so the
 * server is asked once per search rather than once per key, and it follows `value` when the search
 * changes from elsewhere (Clear filters, the browser's back button).
 */
export function SearchField({
  label,
  value,
  onSearch,
  placeholder,
  maxLength = 200,
  className,
}: {
  /** The accessible name: "Search questions". */
  label: string;
  value: string;
  onSearch: (text: string) => void;
  placeholder?: string;
  maxLength?: number;
  className?: string;
}) {
  const [draft, setDraft] = useState(value);
  const settled = useDebouncedValue(draft);
  const reported = useRef(value);

  useEffect(() => {
    if (value !== reported.current) {
      reported.current = value;
      setDraft(value);
    }
  }, [value]);

  useEffect(() => {
    const text = settled.trim();
    if (text !== reported.current) {
      reported.current = text;
      onSearch(text);
    }
  }, [settled, onSearch]);

  return (
    <label
      className={cn(
        "flex h-11 min-w-0 items-center gap-2 rounded-[10px] border border-line-strong bg-white px-3 focus-within:border-tonle-600 focus-within:ring-2 focus-within:ring-tonle-600/15",
        className,
      )}
    >
      <Search size={16} className="shrink-0 text-ink-soft" aria-hidden />
      <span className="sr-only">{label}</span>
      <input
        type="search"
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
        maxLength={maxLength}
        placeholder={placeholder}
        className="w-full min-w-0 bg-transparent text-sm outline-none"
      />
    </label>
  );
}

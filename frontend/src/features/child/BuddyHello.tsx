import { useEffect, useState } from "react";
import { LearningBuddy, type BuddyMood } from "@/features/buddy/LearningBuddy";
import { buddyMessages } from "@/features/buddy/buddy.messages";
import { useBuddyKind } from "@/features/buddy/kinds";
import { useMessages } from "@/i18n/catalog";
import { cn } from "@/lib/utils/cn";
import { entranceMessages } from "./entrance.messages";

/** How long the buddy's own line stays after a tap, before its usual words come back. */
const HELLO_MS = 4500;

/**
 * EXP-1: the buddy answers a tap - it cheers and says its own one-line character (authored product words, the same
 * line the child read when choosing it; never generated). `line` is what it says otherwise.
 */
export function useBuddyHello(line: string) {
  const b = useMessages(buddyMessages);
  const kind = useBuddyKind();
  const [taps, setTaps] = useState(0);
  const [saying, setSaying] = useState(false);
  useEffect(() => {
    if (!saying) return;
    const timer = setTimeout(() => setSaying(false), HELLO_MS);
    return () => clearTimeout(timer);
  }, [saying, taps]);
  return {
    line: saying ? b.characters[kind] : line,
    saying,
    taps,
    greet: () => {
      setTaps((n) => n + 1);
      setSaying(true);
    },
  };
}

/** The buddy on the painting, as a button: a tap makes it cheer (the jump replays on each tap) and speak. */
export function BuddyButton({ hello, mood }: { hello: ReturnType<typeof useBuddyHello>; mood: BuddyMood }) {
  const e = useMessages(entranceMessages);
  const b = useMessages(buddyMessages);
  const kind = useBuddyKind();
  return (
    <button
      type="button"
      onClick={hello.greet}
      aria-label={e.sayHello(b.names[kind])}
      className="touch-manipulation rounded-[28px] focus-visible:outline focus-visible:outline-[3px] focus-visible:outline-offset-2 focus-visible:outline-tonle-700"
    >
      <span key={hello.taps} className={cn("flex", hello.taps > 0 && "motion-safe:animate-cheer")}>
        <LearningBuddy mood={hello.saying ? "cheer" : mood} size={170} className="hidden h-auto w-[clamp(96px,11vw,170px)] sm:block" />
        <LearningBuddy mood={hello.saying ? "cheer" : mood} size={92} className="sm:hidden" />
      </span>
    </button>
  );
}

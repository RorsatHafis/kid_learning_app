import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ArrowLeft, Check } from "lucide-react";
import { childrenApi } from "@/lib/api/endpoints";
import type { LearningBuddyKind } from "@/lib/api/types";
import { useAsync } from "@/lib/hooks/useAsync";
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";
import { cn } from "@/lib/utils/cn";
import { useMessages } from "@/i18n/catalog";
import { LearningBuddy } from "@/features/buddy/LearningBuddy";
import { BUDDY_KINDS } from "@/features/buddy/kinds";
import { buddyMessages } from "@/features/buddy/buddy.messages";
import { ChildSpaceLayout } from "./ChildSpaceLayout";
import { Trouble, Waiting } from "./ChildHome";
import { quietButton } from "./chips";
import { childMessages } from "./messages";

/** Each buddy stands on a ground of its own colour, from the same world. */
const STAGE: Record<LearningBuddyKind, string> = {
  OTTER: "bg-[#DCEBEC]",
  ELEPHANT: "bg-[#F8E4D8]",
  BUFFALO: "bg-[#EEF4E1]",
  GIBBON: "bg-[#FCEED3]",
  FROG: "bg-[#FBE6EB]",
};

/**
 * The child chooses their Learning Buddy (V58). A choice is saved on the child at once, from inside
 * their own space (not behind the grown-up gate, like the language): the buddy then walks their
 * path, stands at the corner of each question and cheers at the end. Nothing about learning,
 * scoring or what a parent is told depends on it.
 */
export function BuddyPage() {
  const { childId = "" } = useParams<{ childId: string }>();
  const child = useAsync(() => childrenApi.get(childId), [childId]);
  const b = useMessages(buddyMessages);
  const m = useMessages(childMessages);
  const [chosen, setChosen] = useState<LearningBuddyKind | null>(null);
  const [saving, setSaving] = useState<LearningBuddyKind | null>(null);
  const [failed, setFailed] = useState(false);
  const [justChose, setJustChose] = useState(false);
  useDocumentTitle(b.title);

  useEffect(() => {
    if (child.data) setChosen(child.data.learningBuddy);
  }, [child.data]);

  async function choose(kind: LearningBuddyKind) {
    if (saving || kind === chosen) return;
    setSaving(kind);
    setFailed(false);
    setJustChose(false);
    try {
      const saved = await childrenApi.chooseBuddy(childId, kind);
      setChosen(saved.learningBuddy);
      setJustChose(true);
    } catch {
      setFailed(true);
    } finally {
      setSaving(null);
    }
  }

  const current = chosen ?? child.data?.learningBuddy;
  return (
    <ChildSpaceLayout childId={childId} childName={child.data?.displayName} childLanguage={child.data?.preferredLanguage} buddy={current}>
      {child.isLoading && <Waiting line={m.home.gettingReady} />}
      {child.error && <Trouble onRetry={child.refetch} />}
      {child.data && current && (
        <div className="mx-auto flex max-w-5xl flex-col gap-8 px-4 pb-20 pt-8 sm:px-8 sm:pt-12">
          <Link to={`/learn/${childId}`} className={cn(quietButton, "min-h-11 w-fit px-4 text-sm")}>
            <ArrowLeft size={16} aria-hidden /> {b.back}
          </Link>
          <div className="flex flex-col gap-2 rounded-[28px] bg-white/95 p-6 shadow-lift ring-1 ring-tonle-950/5 sm:p-8">
            <h1 className="font-display text-headline font-bold text-tonle-950">{b.title}</h1>
            <p className="text-lg font-medium text-ink-soft">{b.lead}</p>
            <p role="status" className="min-h-7 text-lg font-bold text-palm-deep">
              {justChose ? b.chosen(b.names[current]) : ""}
            </p>
            {failed && (
              <p role="alert" className="text-base font-semibold text-brick-deep">
                {b.failed}
              </p>
            )}
          </div>
          <div role="radiogroup" aria-label={b.list} className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {BUDDY_KINDS.map((kind) => {
              const mine = kind === current;
              const name = b.names[kind];
              return (
                <button
                  key={kind}
                  type="button"
                  role="radio"
                  aria-checked={mine}
                  aria-label={mine ? `${name}, ${b.mine}` : b.choose(name)}
                  disabled={saving !== null}
                  onClick={() => void choose(kind)}
                  className={cn(
                    "group relative flex flex-col overflow-hidden rounded-[28px] bg-white text-left shadow-paper ring-1 transition-[transform,box-shadow] duration-200 ease-out hover:-translate-y-1 hover:shadow-lift disabled:cursor-wait",
                    mine ? "ring-4 ring-light" : "ring-tonle-950/5",
                  )}
                >
                  <span className={cn("relative flex h-48 items-end justify-center", STAGE[kind])}>
                    <span aria-hidden className="absolute inset-x-6 bottom-5 h-3 rounded-full bg-tonle-950/10 blur-[2px]" />
                    <LearningBuddy kind={kind} mood={mine && justChose ? "cheer" : mine ? "happy" : "idle"} size={176} className="relative -mb-2" />
                    {mine && (
                      <span className="absolute left-4 top-4 inline-flex items-center gap-1.5 rounded-pill bg-light px-3 py-1 text-sm font-bold text-tonle-950">
                        <Check size={15} strokeWidth={3} aria-hidden /> {b.mine}
                      </span>
                    )}
                  </span>
                  <span className="flex flex-col gap-1 p-5">
                    <span className="font-display text-2xl font-bold text-tonle-950">{name}</span>
                    <span className="text-base font-medium leading-snug text-ink-soft">{b.characters[kind]}</span>
                    {saving === kind && <span className="text-sm font-semibold text-tonle-700">{b.saving}</span>}
                  </span>
                </button>
              );
            })}
          </div>
        </div>
      )}
    </ChildSpaceLayout>
  );
}

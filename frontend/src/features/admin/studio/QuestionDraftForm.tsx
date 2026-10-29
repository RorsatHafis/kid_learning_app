import { useEffect, useState, type FormEvent } from "react";
import { ArrowDown, ArrowUp, Plus, Trash2 } from "lucide-react";
import type { MediaRef, QuestionType } from "@/lib/api/types";
import type { KhmerDraft, MediaItem, QuestionDraftInput } from "@/lib/api/studio";
import { Button } from "@/components/ui/Button";
import { FormField } from "@/features/auth/FormField";
import { SelectField, TextAreaField } from "./shared";
import { DIFFICULTIES } from "./studioLogic";
import { MediaSlot } from "./MediaSlot";
import { useMessages } from "@/i18n/catalog";
import { formMessages } from "./form.messages";
import { cn } from "@/lib/utils/cn";

const MAX_OPTIONS = 10;
const MAX_PAIRS = 6; // content/service/QuestionCompleteness.MAX_PAIRS
const MIN_PAIRS = 2;
const MAX_ITEMS = 8; // content/service/OrderingItems.MAX_ITEMS
const MIN_ITEMS = 3;
const MIN_GROUPS = 2; // content/service/QuestionCompleteness.MIN_GROUPS
const MAX_GROUPS = 4;
const MIN_SORT_ITEMS = 4;
const MAX_SORT_ITEMS = 10;

const fieldClass =
  "flex-1 rounded-[12px] border border-line-strong shadow-[inset_0_1px_2px_rgba(16,50,58,0.05)] px-4 py-2.5 text-sm outline-none focus:border-tonle-600 focus:ring-2 focus:ring-tonle-600/15";
const inputClass = `min-w-0 ${fieldClass}`;
const removeClass = "rounded-full p-2 text-ink-soft hover:bg-tonle-50 hover:text-ink disabled:opacity-40";
const addClass = "flex w-fit items-center gap-1 rounded-pill px-2 py-1 text-sm font-semibold text-tonle-700 hover:bg-tonle-50";

type Media = MediaRef | null | undefined;

/** A question version as the form edits it: media as references, so they can be shown and heard. */
export interface DraftValues {
  /** The Khmer of the question's own fields (LOCALIZATION.md); each row below carries its own Khmer label. */
  khmer?: { prompt: string; hint: string; explanation: string; promptImageDescription: string };
  prompt: string;
  correctAnswer: string | null;
  difficultyLevel: number | null;
  promptImage?: Media;
  promptImageDescription?: string | null;
  promptAudio?: Media;
  options?: { label: string; labelKm?: string; correct: boolean; image?: Media; audio?: Media }[];
  pairs?: { left: string; right: string; leftKm?: string; rightKm?: string; leftImage?: Media; rightImage?: Media }[];
  /** ORDERING: the items in their correct order. */
  items?: { label: string; labelKm?: string; image?: Media; audio?: Media }[];
  /** SORTING (X23): the groups, and each item with the place of its group in `groups`. */
  groups?: { label: string; labelKm?: string; image?: Media }[];
  sortItems?: { label: string; labelKm?: string; group: number; image?: Media; audio?: Media }[];
  hint?: string | null;
  explanation?: string | null;
}

const id = (media: Media) => media?.id ?? undefined;

/** An empty answer label takes the chosen picture's description (or title): the words a screen reader says. */
const labelFrom = (item: MediaItem | undefined, current: string) => item?.description ?? item?.title ?? current;

/**
 * The editable part of a question version. The answer contract follows the scorer
 * (AnswerScoringService): multiple choice is answered by choosing an option, so its
 * key lives on the options; true/false is "true" or "false"; number answers must be
 * numbers; short answers are compared to the text given; a matching question is its
 * pairs — the child sees the two columns, never which items belong together; an ordering
 * question is its items in the right order — the child gets them mixed up.
 *
 * Any question can carry a picture and be read aloud (X11), and choices and items can be
 * pictures and have a recording: for children who don't read yet, and for questions about
 * what something looks or sounds like.
 */
export function QuestionDraftForm({
  questionType,
  initial,
  submitLabel,
  busy,
  onSubmit,
  onDirtyChange,
  children,
}: {
  questionType: QuestionType;
  initial: DraftValues;
  submitLabel: string;
  busy: boolean;
  onSubmit: (draft: QuestionDraftInput) => void;
  /** Told whether the form holds changes that aren't saved (what it would send differs from what it was given). */
  onDirtyChange?: (dirty: boolean) => void;
  children?: React.ReactNode;
}) {
  const [prompt, setPrompt] = useState(initial.prompt);
  const [promptImage, setPromptImage] = useState<Media>(initial.promptImage ?? null);
  const [description, setDescription] = useState(initial.promptImageDescription ?? "");
  const [promptAudio, setPromptAudio] = useState<Media>(initial.promptAudio ?? null);
  const [answer, setAnswer] = useState(initial.correctAnswer ?? "");
  const [difficulty, setDifficulty] = useState(initial.difficultyLevel ? String(initial.difficultyLevel) : "");
  const [hint, setHint] = useState(initial.hint ?? "");
  const [explanation, setExplanation] = useState(initial.explanation ?? "");
  // The Khmer, kept beside the English (LOCALIZATION.md): switching the content language shows the other set of
  // fields and never touches the one not shown. The interface language is a separate choice.
  const [promptKm, setPromptKm] = useState(initial.khmer?.prompt ?? "");
  const [hintKm, setHintKm] = useState(initial.khmer?.hint ?? "");
  const [explanationKm, setExplanationKm] = useState(initial.khmer?.explanation ?? "");
  const [descriptionKm, setDescriptionKm] = useState(initial.khmer?.promptImageDescription ?? "");
  const translatable = questionType !== "SHORT_ANSWER";
  const [language, setLanguage] = useState<ContentLanguage>("en");
  const km = translatable && language === "km";
  const [options, setOptions] = useState<NonNullable<DraftValues["options"]>>(
    initial.options && initial.options.length > 0
      ? initial.options
      : [
          { label: "", correct: true },
          { label: "", correct: false },
        ],
  );
  const [pairs, setPairs] = useState<NonNullable<DraftValues["pairs"]>>(
    initial.pairs && initial.pairs.length > 0
      ? initial.pairs
      : [
          { left: "", right: "" },
          { left: "", right: "" },
        ],
  );
  const [items, setItems] = useState<NonNullable<DraftValues["items"]>>(
    initial.items && initial.items.length > 0 ? initial.items : Array.from({ length: MIN_ITEMS }, () => ({ label: "" })),
  );
  const [groups, setGroups] = useState<NonNullable<DraftValues["groups"]>>(
    initial.groups && initial.groups.length > 0 ? initial.groups : [{ label: "" }, { label: "" }],
  );
  const [sortItems, setSortItems] = useState<NonNullable<DraftValues["sortItems"]>>(
    initial.sortItems && initial.sortItems.length > 0
      ? initial.sortItems
      : Array.from({ length: MIN_SORT_ITEMS }, (_, i) => ({ label: "", group: i % 2 })),
  );
  const sorting = questionType === "SORTING";
  const multipleChoice = questionType === "MULTIPLE_CHOICE";
  const matching = questionType === "MATCHING";
  const ordering = questionType === "ORDERING";
  const m = useMessages(formMessages);

  function submit(e: FormEvent) {
    e.preventDefault();
    onSubmit(draft());
  }

  /** What saving would send now. */
  function draft(): QuestionDraftInput {
    return {
      prompt: prompt.trim(),
      correctAnswer: multipleChoice || matching || ordering || sorting ? null : answer.trim() || null,
      difficultyLevel: difficulty ? Number(difficulty) : null,
      promptImageAssetId: id(promptImage),
      promptImageDescription: promptImage && description.trim() ? description.trim() : undefined,
      promptAudioAssetId: id(promptAudio),
      hint: hint.trim() || null,
      explanation: explanation.trim() || null,
      options: multipleChoice
        ? options.map((o) => ({
            label: o.label.trim(),
            correct: o.correct,
            imageAssetId: id(o.image),
            audioAssetId: id(o.audio),
          }))
        : undefined,
      pairs: matching
        ? pairs.map((p) => ({
            left: p.left.trim(),
            right: p.right.trim(),
            leftImageAssetId: id(p.leftImage),
            rightImageAssetId: id(p.rightImage),
          }))
        : undefined,
      items: ordering
        ? items.map((i) => ({ label: i.label.trim(), imageAssetId: id(i.image), audioAssetId: id(i.audio) }))
        : undefined,
      groups: sorting ? groups.map((g) => ({ label: g.label.trim(), imageAssetId: id(g.image) })) : undefined,
      sortItems: sorting
        ? sortItems.map((i) => ({ label: i.label.trim(), group: i.group, imageAssetId: id(i.image), audioAssetId: id(i.audio) }))
        : undefined,
      // Sent when there is Khmer to keep or change: the question had some (clearing it is saved too), or some is typed.
      khmer: translatable && (initial.khmer || anyKhmer()) ? khmerDraft() : undefined,
    };
  }

  function anyKhmer() {
    const k = khmerDraft();
    return [k.prompt, k.hint, k.explanation, k.promptImageDescription, ...[k.options, k.pairLefts, k.pairRights, k.items, k.groups, k.sortItems].flat()].some(
      (text) => !!text,
    );
  }

  /** The Khmer as it would be saved: one label per row, in the rows' own order; blank = not translated yet. */
  function khmerDraft(): KhmerDraft {
    const t = (text: string | undefined) => text?.trim() ?? "";
    return {
      prompt: t(promptKm) || null,
      hint: t(hintKm) || null,
      explanation: t(explanationKm) || null,
      promptImageDescription: promptImage ? t(descriptionKm) || null : null,
      options: multipleChoice ? options.map((o) => t(o.labelKm)) : undefined,
      pairLefts: matching ? pairs.map((p) => t(p.leftKm)) : undefined,
      pairRights: matching ? pairs.map((p) => t(p.rightKm)) : undefined,
      items: ordering ? items.map((i) => t(i.labelKm)) : undefined,
      groups: sorting ? groups.map((g) => t(g.labelKm)) : undefined,
      sortItems: sorting ? sortItems.map((i) => t(i.labelKm)) : undefined,
    };
  }

  // How much of the Khmer is written: every field the English has, and each row's label.
  const khmerCounts = (() => {
    const pairsOf: [string, string | undefined][] = [
      [prompt, promptKm],
      [hint, hintKm],
      [explanation, explanationKm],
      [promptImage ? description : "", descriptionKm],
      ...(multipleChoice ? options.map((o): [string, string | undefined] => [o.label, o.labelKm]) : []),
      ...(matching ? pairs.flatMap((p): [string, string | undefined][] => [[p.left, p.leftKm], [p.right, p.rightKm]]) : []),
      ...(ordering ? items.map((i): [string, string | undefined] => [i.label, i.labelKm]) : []),
      ...(sorting ? groups.map((g): [string, string | undefined] => [g.label, g.labelKm]) : []),
      ...(sorting ? sortItems.map((i): [string, string | undefined] => [i.label, i.labelKm]) : []),
    ];
    const needed = pairsOf.filter(([en]) => en.trim());
    return { written: needed.filter(([, k]) => k?.trim()).length, needed: needed.length };
  })();

  // Unsaved changes: what saving would send, against what it was when the form was last given saved values. A save
  // brings new saved values (the same draft version, so the form stays mounted): they become the new baseline.
  const snapshot = JSON.stringify(draft());
  const savedValues = JSON.stringify(initial);
  const [baseline, setBaseline] = useState({ saved: savedValues, snapshot });
  if (baseline.saved !== savedValues) setBaseline({ saved: savedValues, snapshot });
  const dirty = baseline.saved === savedValues && snapshot !== baseline.snapshot;
  useEffect(() => {
    onDirtyChange?.(dirty);
  }, [dirty, onDirtyChange]);
  useEffect(() => {
    if (!dirty) return;
    // Reloading or closing the tab asks first, so typed work isn't lost by accident.
    const warn = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = "";
    };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);

  const optionsComplete =
    (!multipleChoice || options.every((o) => o.label.trim())) &&
    (!matching || pairs.every((p) => p.left.trim() && p.right.trim())) &&
    (!ordering || items.every((i) => i.label.trim())) &&
    (!sorting || (groups.every((g) => g.label.trim()) && sortItems.every((i) => i.label.trim())));

  /** Removing a group moves its items to the first group and keeps the others pointing at theirs. */
  function removeGroup(index: number) {
    setGroups(groups.filter((_, i) => i !== index));
    setSortItems(sortItems.map((it) => ({ ...it, group: it.group === index ? 0 : it.group > index ? it.group - 1 : it.group })));
  }

  function move(from: number, to: number) {
    const next = [...items];
    const [moved] = next.splice(from, 1);
    next.splice(to, 0, moved);
    setItems(next);
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-4">
      <ContentLanguageSwitch
        language={km ? "km" : "en"}
        onChange={setLanguage}
        translatable={translatable}
        written={khmerCounts.written}
        needed={khmerCounts.needed}
      />
      {km ? (
        <Translated english={prompt}>
          <TextAreaField label={m.inKhmer(m.prompt)} lang="km" value={promptKm} maxLength={2000} onChange={(e) => setPromptKm(e.target.value)} />
        </Translated>
      ) : (
        <TextAreaField label={m.prompt} value={prompt} maxLength={2000} required onChange={(e) => setPrompt(e.target.value)} />
      )}

      <fieldset className="flex min-w-0 flex-col gap-4 rounded-[12px] bg-paper px-4 py-3 ring-1 ring-inset ring-line">
        <legend className="sr-only">{m.media}</legend>
        <div className="flex flex-col gap-1.5">
          <span className="text-sm font-semibold text-ink">{m.picture}</span>
          <MediaSlot
            kind="IMAGE"
            value={promptImage ?? null}
            onChange={(media, item) => {
              setPromptImage(media);
              // A library picture already described offers that description here.
              if (media && !description.trim() && item?.description) setDescription(item.description);
            }}
            name={m.pictureName}
          />
          {promptImage &&
            (km ? (
              <FormField
                label={m.inKhmer(m.describe)}
                lang="km"
                value={descriptionKm}
                maxLength={300}
                onChange={(e) => setDescriptionKm(e.target.value)}
                placeholder={description || m.describeHint}
              />
            ) : (
              <FormField
                label={m.describe}
                value={description}
                maxLength={300}
                onChange={(e) => setDescription(e.target.value)}
                placeholder={m.describeHint}
              />
            ))}
        </div>
        <div className="flex flex-col gap-1.5">
          <span className="text-sm font-semibold text-ink">{m.readAloud}</span>
          <MediaSlot kind="AUDIO" value={promptAudio ?? null} onChange={setPromptAudio} name={m.readingName} />
          <p className="text-xs text-ink-soft">{m.readAloudHint}</p>
        </div>
      </fieldset>

      {multipleChoice && (
        <fieldset className="flex min-w-0 flex-col gap-2">
          <legend className="mb-1 text-sm font-semibold text-ink">{m.choices}</legend>
          {options.map((option, index) => (
            // As ordering and sorting rows: the words keep a usable width, the media controls wrap below them.
            <div key={index} className="flex flex-wrap items-center gap-x-2 gap-y-1.5">
              <div className="flex min-w-[13rem] flex-1 items-center gap-2">
                <input
                  type="radio"
                  name="correct-choice"
                  checked={option.correct}
                  onChange={() => setOptions(options.map((o, i) => ({ ...o, correct: i === index })))}
                  aria-label={m.choiceCorrect(index + 1)}
                  className="h-5 w-5 shrink-0 accent-tonle-700"
                />
                <input
                  value={km ? (option.labelKm ?? "") : option.label}
                  lang={km ? "km" : undefined}
                  maxLength={300}
                  onChange={(e) =>
                    setOptions(options.map((o, i) => (i === index ? (km ? { ...o, labelKm: e.target.value } : { ...o, label: e.target.value }) : o)))
                  }
                  aria-label={km ? m.inKhmer(m.choice(index + 1)) : m.choice(index + 1)}
                  placeholder={km ? option.label : option.image ? m.describePicture : undefined}
                  className={inputClass}
                />
              </div>
              <div className="ml-auto flex items-center gap-1">
                <MediaSlot
                  compact
                  kind="IMAGE"
                  value={option.image ?? null}
                  onChange={(image, item) =>
                    setOptions(
                      options.map((o, i) =>
                        i === index ? { ...o, image, label: o.label.trim() ? o.label : labelFrom(item, o.label) } : o,
                      ),
                    )
                  }
                  name={m.choicePicture(index + 1)}
                />
                <MediaSlot
                  compact
                  kind="AUDIO"
                  value={option.audio ?? null}
                  onChange={(audio) => setOptions(options.map((o, i) => (i === index ? { ...o, audio } : o)))}
                  name={m.choiceRecording(index + 1)}
                />
                <button
                  type="button"
                  onClick={() => setOptions(options.filter((_, i) => i !== index))}
                  disabled={options.length <= 2}
                  aria-label={m.removeChoice(index + 1)}
                  className={removeClass}
                >
                  <Trash2 size={16} aria-hidden />
                </button>
              </div>
            </div>
          ))}
          {options.length < MAX_OPTIONS && (
            <button type="button" onClick={() => setOptions([...options, { label: "", correct: false }])} className={addClass}>
              <Plus size={14} aria-hidden /> {m.addChoice}
            </button>
          )}
          {options.some((o) => o.image) && (
            <p className="text-xs text-ink-soft">{m.pictureOnly}</p>
          )}
        </fieldset>
      )}

      {matching && (
        <fieldset className="flex min-w-0 flex-col gap-2">
          <legend className="mb-1 text-sm font-semibold text-ink">{m.pairs}</legend>
          {pairs.map((pair, index) => (
            <div key={index} className="flex flex-wrap items-center gap-x-2 gap-y-1.5">
              <div className="flex min-w-[13rem] flex-1 items-center gap-2">
                <span aria-hidden className="w-5 shrink-0 text-right text-xs font-bold text-ink-soft">
                  {index + 1}
                </span>
                <input
                  value={km ? (pair.leftKm ?? "") : pair.left}
                  lang={km ? "km" : undefined}
                  maxLength={300}
                  onChange={(e) =>
                    setPairs(pairs.map((p, i) => (i === index ? (km ? { ...p, leftKm: e.target.value } : { ...p, left: e.target.value }) : p)))
                  }
                  aria-label={km ? m.inKhmer(m.pairItem(index + 1)) : m.pairItem(index + 1)}
                  placeholder={km ? pair.left : m.item}
                  className={inputClass}
                />
                <MediaSlot
                  compact
                  kind="IMAGE"
                  value={pair.leftImage ?? null}
                  onChange={(leftImage) => setPairs(pairs.map((p, i) => (i === index ? { ...p, leftImage } : p)))}
                  name={m.pairItemPicture(index + 1)}
                />
              </div>
              <div className="flex min-w-[13rem] flex-1 items-center gap-2">
                <span aria-hidden className="w-5 shrink-0 text-center text-ink-soft">
                  ↔
                </span>
                <input
                  value={km ? (pair.rightKm ?? "") : pair.right}
                  lang={km ? "km" : undefined}
                  maxLength={300}
                  onChange={(e) =>
                    setPairs(pairs.map((p, i) => (i === index ? (km ? { ...p, rightKm: e.target.value } : { ...p, right: e.target.value }) : p)))
                  }
                  aria-label={km ? m.inKhmer(m.pairMatch(index + 1)) : m.pairMatch(index + 1)}
                  placeholder={km ? pair.right : m.itsMatch}
                  className={inputClass}
                />
                <MediaSlot
                  compact
                  kind="IMAGE"
                  value={pair.rightImage ?? null}
                  onChange={(rightImage) => setPairs(pairs.map((p, i) => (i === index ? { ...p, rightImage } : p)))}
                  name={m.pairMatchPicture(index + 1)}
                />
                <button
                  type="button"
                  onClick={() => setPairs(pairs.filter((_, i) => i !== index))}
                  disabled={pairs.length <= MIN_PAIRS}
                  aria-label={m.removePair(index + 1)}
                  className={removeClass}
                >
                  <Trash2 size={16} aria-hidden />
                </button>
              </div>
            </div>
          ))}
          {pairs.length < MAX_PAIRS && (
            <button type="button" onClick={() => setPairs([...pairs, { left: "", right: "" }])} className={addClass}>
              <Plus size={14} aria-hidden /> {m.addPair}
            </button>
          )}
        </fieldset>
      )}

      {ordering && (
        <fieldset className="flex min-w-0 flex-col gap-2">
          <legend className="mb-1 text-sm font-semibold text-ink">{m.ordering(MIN_ITEMS, MAX_ITEMS)}</legend>
          {items.map((item, index) => (
            <div key={index} className="flex flex-wrap items-center gap-x-2 gap-y-1.5">
              <div className="flex min-w-[13rem] flex-1 items-center gap-2">
                <span aria-hidden className="w-5 shrink-0 text-right text-xs font-bold text-ink-soft">
                  {index + 1}
                </span>
                <input
                  value={km ? (item.labelKm ?? "") : item.label}
                  lang={km ? "km" : undefined}
                  maxLength={300}
                  onChange={(e) =>
                    setItems(items.map((it, i) => (i === index ? (km ? { ...it, labelKm: e.target.value } : { ...it, label: e.target.value }) : it)))
                  }
                  aria-label={km ? m.inKhmer(m.itemN(index + 1)) : m.itemN(index + 1)}
                  placeholder={km ? item.label : item.image ? m.describePicture : undefined}
                  className={inputClass}
                />
              </div>
              <div className="ml-auto flex items-center gap-1">
                <MediaSlot
                  compact
                  kind="IMAGE"
                  value={item.image ?? null}
                  onChange={(image, item) =>
                    setItems(
                      items.map((it, i) =>
                        i === index ? { ...it, image, label: it.label.trim() ? it.label : labelFrom(item, it.label) } : it,
                      ),
                    )
                  }
                  name={m.itemPicture(index + 1)}
                />
                <MediaSlot
                  compact
                  kind="AUDIO"
                  value={item.audio ?? null}
                  onChange={(audio) => setItems(items.map((it, i) => (i === index ? { ...it, audio } : it)))}
                  name={m.itemRecording(index + 1)}
                />
                <button
                  type="button"
                  onClick={() => move(index, index - 1)}
                  disabled={index === 0}
                  aria-label={m.moveUp(index + 1)}
                  className={removeClass}
                >
                  <ArrowUp size={16} aria-hidden />
                </button>
                <button
                  type="button"
                  onClick={() => move(index, index + 1)}
                  disabled={index === items.length - 1}
                  aria-label={m.moveDown(index + 1)}
                  className={removeClass}
                >
                  <ArrowDown size={16} aria-hidden />
                </button>
                <button
                  type="button"
                  onClick={() => setItems(items.filter((_, i) => i !== index))}
                  disabled={items.length <= MIN_ITEMS}
                  aria-label={m.removeItem(index + 1)}
                  className={removeClass}
                >
                  <Trash2 size={16} aria-hidden />
                </button>
              </div>
            </div>
          ))}
          {items.length < MAX_ITEMS && (
            <button type="button" onClick={() => setItems([...items, { label: "" }])} className={addClass}>
              <Plus size={14} aria-hidden /> {m.addItem}
            </button>
          )}
        </fieldset>
      )}

      {sorting && (
        <>
          <fieldset className="flex min-w-0 flex-col gap-2">
            <legend className="mb-1 text-sm font-semibold text-ink">{m.groups(MIN_GROUPS, MAX_GROUPS)}</legend>
            {groups.map((group, index) => (
              <div key={index} className="flex flex-wrap items-center gap-x-2 gap-y-1.5">
                <div className="flex min-w-[13rem] flex-1 items-center gap-2">
                  <input
                    value={km ? (group.labelKm ?? "") : group.label}
                    lang={km ? "km" : undefined}
                    maxLength={120}
                    onChange={(e) =>
                      setGroups(groups.map((g, i) => (i === index ? (km ? { ...g, labelKm: e.target.value } : { ...g, label: e.target.value }) : g)))
                    }
                    aria-label={km ? m.inKhmer(m.groupN(index + 1)) : m.groupN(index + 1)}
                    placeholder={km ? group.label : group.image ? m.describePicture : undefined}
                    className={inputClass}
                  />
                </div>
                <div className="ml-auto flex items-center gap-1">
                  <MediaSlot
                    compact
                    kind="IMAGE"
                    value={group.image ?? null}
                    onChange={(image, item) =>
                      setGroups(
                        groups.map((g, i) =>
                          i === index ? { ...g, image, label: g.label.trim() ? g.label : labelFrom(item, g.label) } : g,
                        ),
                      )
                    }
                    name={m.groupPicture(index + 1)}
                  />
                  <button
                    type="button"
                    onClick={() => removeGroup(index)}
                    disabled={groups.length <= MIN_GROUPS}
                    aria-label={m.removeGroup(index + 1)}
                    className={removeClass}
                  >
                    <Trash2 size={16} aria-hidden />
                  </button>
                </div>
              </div>
            ))}
            {groups.length < MAX_GROUPS && (
              <button type="button" onClick={() => setGroups([...groups, { label: "" }])} className={addClass}>
                <Plus size={14} aria-hidden /> {m.addGroup}
              </button>
            )}
          </fieldset>
          <fieldset className="flex min-w-0 flex-col gap-2">
            <legend className="mb-1 text-sm font-semibold text-ink">{m.sortItems(MIN_SORT_ITEMS, MAX_SORT_ITEMS)}</legend>
            {sortItems.map((item, index) => (
              <div key={index} className="flex flex-wrap items-center gap-x-2 gap-y-1.5">
                <div className="flex min-w-[13rem] flex-1 items-center gap-2">
                  <input
                    value={km ? (item.labelKm ?? "") : item.label}
                    lang={km ? "km" : undefined}
                    maxLength={300}
                    onChange={(e) =>
                      setSortItems(
                        sortItems.map((it, i) => (i === index ? (km ? { ...it, labelKm: e.target.value } : { ...it, label: e.target.value }) : it)),
                      )
                    }
                    aria-label={km ? m.inKhmer(m.itemN(index + 1)) : m.itemN(index + 1)}
                    placeholder={km ? item.label : item.image ? m.describePicture : undefined}
                    className={inputClass}
                  />
                  <select
                    value={item.group}
                    onChange={(e) =>
                      setSortItems(sortItems.map((it, i) => (i === index ? { ...it, group: Number(e.target.value) } : it)))
                    }
                    aria-label={m.groupFor(index + 1)}
                    className="h-[42px] rounded-[12px] border border-line-strong shadow-[inset_0_1px_2px_rgba(16,50,58,0.05)] bg-white px-2 text-sm text-ink"
                  >
                    {groups.map((g, gi) => (
                      <option key={gi} value={gi}>
                        {g.label.trim() || m.groupN(gi + 1)}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="ml-auto flex items-center gap-1">
                  <MediaSlot
                    compact
                    kind="IMAGE"
                    value={item.image ?? null}
                    onChange={(image, chosen) =>
                      setSortItems(
                        sortItems.map((it, i) =>
                          i === index ? { ...it, image, label: it.label.trim() ? it.label : labelFrom(chosen, it.label) } : it,
                        ),
                      )
                    }
                    name={m.itemPicture(index + 1)}
                  />
                  <MediaSlot
                    compact
                    kind="AUDIO"
                    value={item.audio ?? null}
                    onChange={(audio) => setSortItems(sortItems.map((it, i) => (i === index ? { ...it, audio } : it)))}
                    name={m.itemRecording(index + 1)}
                  />
                  <button
                    type="button"
                    onClick={() => setSortItems(sortItems.filter((_, i) => i !== index))}
                    disabled={sortItems.length <= MIN_SORT_ITEMS}
                    aria-label={m.removeItem(index + 1)}
                    className={removeClass}
                  >
                    <Trash2 size={16} aria-hidden />
                  </button>
                </div>
              </div>
            ))}
            {sortItems.length < MAX_SORT_ITEMS && (
              <button type="button" onClick={() => setSortItems([...sortItems, { label: "", group: 0 }])} className={addClass}>
                <Plus size={14} aria-hidden /> {m.addItem}
              </button>
            )}
          </fieldset>
        </>
      )}

      {questionType === "TRUE_FALSE" && (
        <SelectField label={m.correctAnswer} value={answer} onChange={(e) => setAnswer(e.target.value)} required>
          <option value="">{m.choose}</option>
          <option value="true">{m.true}</option>
          <option value="false">{m.false}</option>
        </SelectField>
      )}
      {questionType === "NUMERIC" && (
        <FormField
          label={m.numberAnswer}
          inputMode="decimal"
          value={answer}
          maxLength={40}
          onChange={(e) => setAnswer(e.target.value)}
        />
      )}
      {questionType === "SHORT_ANSWER" && (
        <FormField label={m.correctAnswer} value={answer} maxLength={300} onChange={(e) => setAnswer(e.target.value)} />
      )}

      <SelectField label={m.difficulty} value={difficulty} onChange={(e) => setDifficulty(e.target.value)}>
        <option value="">{m.notSet}</option>
        {DIFFICULTIES.map((d) => (
          <option key={d} value={d}>
            {d}
          </option>
        ))}
      </SelectField>

      {/* Y41: the educator's own help for the child, each said where and when the child sees it. */}
      <fieldset className="flex min-w-0 flex-col gap-4 rounded-[12px] bg-paper px-4 py-3 ring-1 ring-inset ring-line">
        <legend className="px-1 text-sm font-semibold text-ink">{m.helpTitle}</legend>
        <p className="-mt-1 text-xs text-ink-soft">{m.helpLead}</p>
        {km ? (
          <>
            <Translated english={hint}>
              <TextAreaField label={m.inKhmer(m.hint)} lang="km" value={hintKm} maxLength={300} rows={2} onChange={(e) => setHintKm(e.target.value)} />
            </Translated>
            <Translated english={explanation}>
              <TextAreaField
                label={m.inKhmer(m.explanation)}
                lang="km"
                value={explanationKm}
                maxLength={600}
                rows={3}
                onChange={(e) => setExplanationKm(e.target.value)}
              />
            </Translated>
          </>
        ) : (
          <>
            <TextAreaField label={m.hint} hint={m.hintHelp} value={hint} maxLength={300} rows={2} onChange={(e) => setHint(e.target.value)} />
            <TextAreaField
              label={m.explanation}
              hint={m.explanationHelp}
              value={explanation}
              maxLength={600}
              rows={3}
              onChange={(e) => setExplanation(e.target.value)}
            />
          </>
        )}
      </fieldset>

      {children}

      <Button type="submit" disabled={busy || !prompt.trim() || !optionsComplete} className="w-fit">
        {busy ? m.saving : submitLabel}
      </Button>
    </form>
  );
}

type ContentLanguage = "en" | "km";

/**
 * Which language of the question is being written - English (always needed) or Khmer (optional; a child reading
 * Khmer gets the question in Khmer once its Khmer is complete). Independent of the interface's language.
 */
function ContentLanguageSwitch({
  language,
  onChange,
  translatable,
  written,
  needed,
}: {
  language: ContentLanguage;
  onChange: (language: ContentLanguage) => void;
  translatable: boolean;
  written: number;
  needed: number;
}) {
  const m = useMessages(formMessages);
  const complete = needed > 0 && written >= needed;
  return (
    <div className="flex flex-col gap-2 rounded-[12px] bg-paper px-3 py-2.5 ring-1 ring-inset ring-line">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
        <span id="content-language" className="text-sm font-semibold text-ink">
          {m.writingIn}
        </span>
        <div role="group" aria-labelledby="content-language" className="flex gap-1 rounded-[10px] bg-white p-1 ring-1 ring-line">
          {(["en", "km"] as const).map((l) => (
            <button
              key={l}
              type="button"
              lang={l}
              aria-pressed={language === l}
              disabled={l === "km" && !translatable}
              onClick={() => onChange(l)}
              className={cn(
                "min-h-9 rounded-[8px] px-3 text-sm font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-tonle-600 disabled:opacity-50",
                language === l ? "bg-tonle-800 text-white" : "text-ink-soft hover:text-ink",
              )}
            >
              {l === "en" ? "English" : "ខ្មែរ"}
            </button>
          ))}
        </div>
      </div>
      <p className={cn("text-xs", translatable && !complete && written > 0 ? "font-semibold text-light-deep" : "text-ink-soft")}>
        {!translatable ? m.khmerNotForType : complete ? m.khmerComplete : written === 0 ? m.khmerNone : m.khmerPartly(written, needed)}
      </p>
    </div>
  );
}

/** A Khmer field with the English it translates shown under it, so both versions can be checked side by side. */
function Translated({ english, children }: { english: string; children: React.ReactNode }) {
  const m = useMessages(formMessages);
  return (
    <div className="flex flex-col gap-1">
      {children}
      {english.trim() && (
        <p className="text-xs text-ink-soft [overflow-wrap:anywhere]">
          <span className="font-semibold">{m.englishIs}</span> {english}
        </p>
      )}
    </div>
  );
}

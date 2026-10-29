import { useEffect, useId, useState, type FormEvent } from "react";
import { BookOpen } from "lucide-react";
import { studioApi, type ActivityDetail, type ActivityVersionDetail } from "@/lib/api/studio";
import type { MediaRef } from "@/lib/api/types";
import { Button } from "@/components/ui/Button";
import { FormField } from "@/features/auth/FormField";
import { useMessages } from "@/i18n/catalog";
import { MediaSlot } from "./MediaSlot";
import { errorText } from "./studioLogic";
import { introductionMessages } from "./introduction.messages";

/**
 * Teaching before asking: a draft activity's introduction - a short explanation or worked example, a picture (with
 * the description a screen reader reads) and a recording for children who don't read yet. The child sees it before
 * the first question and can open it again while answering. Saved with the draft; publishing freezes it.
 */
export function IntroductionEditor({
  version,
  onSaved,
  onDirtyChange,
}: {
  version: ActivityVersionDetail;
  onSaved: (detail: ActivityDetail) => void;
  /** Told whether what is on screen differs from the saved introduction (publishing would leave it out). */
  onDirtyChange?: (dirty: boolean) => void;
}) {
  const m = useMessages(introductionMessages);
  const intro = version.introduction;
  const textId = useId();
  const [text, setText] = useState(intro?.text ?? "");
  const [image, setImage] = useState<MediaRef | null>(intro?.image ?? null);
  const [description, setDescription] = useState(intro?.imageDescription ?? "");
  const [audio, setAudio] = useState<MediaRef | null>(intro?.audio ?? null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);
  // Unsaved: what saving would store, against the saved introduction (spaces around the words don't count).
  const onScreen = JSON.stringify([text.trim(), image?.id ?? null, image ? description.trim() : "", audio?.id ?? null]);
  const saved = JSON.stringify([
    (intro?.text ?? "").trim(),
    intro?.image?.id ?? null,
    intro?.image ? (intro?.imageDescription ?? "").trim() : "",
    intro?.audio?.id ?? null,
  ]);
  const dirty = onScreen !== saved;
  useEffect(() => {
    onDirtyChange?.(dirty);
  }, [dirty, onDirtyChange]);
  useEffect(() => {
    if (!dirty) return;
    const warn = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = "";
    };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);

  async function save(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setMessage(null);
    try {
      onSaved(
        await studioApi.setActivityIntroduction(version.id, {
          text: text.trim() || null,
          imageAssetId: image?.id ?? null,
          imageDescription: image ? description.trim() || null : null,
          audioAssetId: audio?.id ?? null,
        }),
      );
      setMessage({ ok: true, text: m.saved });
    } catch (err) {
      setMessage({ ok: false, text: errorText(err) });
    } finally {
      setBusy(false);
    }
  }

  return (
    <section
      aria-labelledby="build-introduction"
      className="flex flex-col gap-4 rounded-[20px] bg-white p-5 shadow-card sm:p-6"
    >
      <div className="flex items-start gap-3">
        <span
          aria-hidden
          className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-light-soft text-light-deep"
        >
          <BookOpen size={16} />
        </span>
        <div>
          <h2 id="build-introduction" className="font-display text-[1.25rem] font-bold leading-tight text-tonle-950">
            {m.title}
          </h2>
          <p className="mt-1 text-sm leading-relaxed text-ink-soft">{m.lead}</p>
        </div>
      </div>
      <form onSubmit={save} className="flex flex-col gap-4">
        <div className="flex flex-col gap-2">
          <label htmlFor={textId} className="text-sm font-semibold text-ink">
            {m.text}
          </label>
          <textarea
            id={textId}
            aria-describedby={`${textId}-hint`}
            value={text}
            maxLength={1200}
            rows={4}
            onChange={(e) => setText(e.target.value)}
            placeholder={m.placeholder}
            className="rounded-[12px] border border-line-strong bg-white px-3 py-2.5 text-[0.9375rem] leading-relaxed text-ink"
          />
          <span id={`${textId}-hint`} className="text-xs text-ink-faint">
            {m.textHint(text.length)}
          </span>
        </div>
        <div className="flex flex-col gap-2">
          <span className="text-sm font-semibold text-ink">{m.picture}</span>
          <MediaSlot kind="IMAGE" value={image} onChange={(value) => setImage(value)} name={m.pictureName} />
          {image && (
            <FormField
              label={m.describe}
              hint={m.describeHint}
              maxLength={300}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          )}
        </div>
        <div className="flex flex-col gap-2">
          <span className="text-sm font-semibold text-ink">{m.recording}</span>
          <MediaSlot kind="AUDIO" value={audio} onChange={(value) => setAudio(value)} name={m.recordingName} />
          <span className="text-xs text-ink-faint">{m.recordingHint}</span>
        </div>
        {message && (
          <p
            role={message.ok ? "status" : "alert"}
            className={message.ok ? "text-sm font-medium text-palm-deep" : "text-sm font-medium text-brick-deep"}
          >
            {message.text}
          </p>
        )}
        <div className="flex flex-wrap items-center gap-3">
          <Button type="submit" variant={dirty ? "solid" : "outline"} disabled={busy} className="w-fit">
            {busy ? m.saving : m.save}
          </Button>
          {dirty && !busy && <span className="text-sm font-semibold text-light-deep">{m.notSaved}</span>}
        </div>
      </form>
    </section>
  );
}

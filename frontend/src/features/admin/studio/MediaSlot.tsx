import { useEffect, useRef, useState, type DragEvent } from "react";
import { ImagePlus, Library, Mic, Play, Square, X } from "lucide-react";
import { cn } from "@/lib/utils/cn";
import type { MediaRef } from "@/lib/api/types";
import { studioApi } from "@/lib/api/studio";
import { speak, stopSpeaking, usePlaying } from "@/features/child/speaker";
import type { MediaItem } from "@/lib/api/studio";
import { fileSize, uploadError } from "./mediaText";
import { MediaPicker } from "./MediaLibrary";
import { useMessages } from "@/i18n/catalog";
import { mediaMessages } from "./media.messages";

const ACCEPT = {
  IMAGE: "image/png,image/jpeg,image/webp",
  AUDIO: "audio/mpeg,audio/mp4,audio/x-m4a,audio/ogg,audio/wav,audio/x-wav",
};

/** M5a: the upload under way - what was chosen, how much has been sent. */
interface Sending {
  name: string;
  size: number;
  preview: string | null;
  dimensions: { width: number; height: number } | null;
  fraction: number;
}

/**
 * A picture or recording on a question (X11): add one (uploaded and checked by the server) or choose
 * one already in the media library (X13), see or hear it, replace or remove it. `compact` fits in a
 * row of answer choices. `onChange` also hands over the library item, so a form can offer its
 * description (screen-reader text) where it is used.
 *
 * M5a: a file can also be dropped on the slot. While it uploads, the slot shows the file (a preview of a
 * picture, its size and pixel size) and how much has been sent, and the upload can be cancelled. The
 * server still decides what is accepted; a refused or cancelled upload changes nothing else in the form.
 */
export function MediaSlot({
  kind,
  value,
  onChange,
  name,
  compact = false,
}: {
  kind: "IMAGE" | "AUDIO";
  value: MediaRef | null;
  onChange: (value: MediaRef | null, item?: MediaItem) => void;
  /** What it belongs to, for its controls' names: "picture for choice 1". */
  name: string;
  compact?: boolean;
}) {
  const input = useRef<HTMLInputElement | null>(null);
  const cancel = useRef<AbortController | null>(null);
  const [sending, setSending] = useState<Sending | null>(null);
  const [dragging, setDragging] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [picking, setPicking] = useState(false);
  const playing = usePlaying(value?.url);
  const m = useMessages(mediaMessages).slot;
  const busy = sending !== null;

  // A preview made for an upload is let go once the upload ends (or the slot goes).
  useEffect(() => {
    const preview = sending?.preview;
    return () => {
      if (preview) URL.revokeObjectURL(preview);
    };
  }, [sending?.preview]);
  useEffect(() => () => cancel.current?.abort(), []);

  async function upload(file: File | undefined) {
    if (!file || busy) return;
    setError(null);
    // The browser's own idea of the type: an obvious mismatch is said at once; the server checks the bytes.
    const family = file.type.split("/")[0];
    if (family === (kind === "IMAGE" ? "audio" : "image")) {
      setError(kind === "IMAGE" ? m.notPicture : m.notRecording);
      return;
    }
    if (file.type && family !== "image" && family !== "audio") {
      setError(m.wrongKind(kind === "IMAGE"));
      return;
    }
    const preview = kind === "IMAGE" ? URL.createObjectURL(file) : null;
    setSending({ name: file.name, size: file.size, preview, dimensions: null, fraction: 0 });
    if (preview) {
      const probe = new Image();
      probe.onload = () =>
        setSending((now) => (now?.preview === preview ? { ...now, dimensions: { width: probe.naturalWidth, height: probe.naturalHeight } } : now));
      probe.src = preview;
    }
    const controller = new AbortController();
    cancel.current = controller;
    try {
      const media = await studioApi.uploadMedia(
        file,
        (fraction) => setSending((now) => (now ? { ...now, fraction } : now)),
        controller.signal,
      );
      if (media.type !== kind) {
        setError(kind === "IMAGE" ? m.notPicture : m.notRecording);
        return;
      }
      onChange(media, media);
    } catch (err) {
      setError(err instanceof DOMException && err.name === "AbortError" ? m.cancelled : `${uploadError(err)} ${m.keptDraft}`);
    } finally {
      cancel.current = null;
      setSending(null);
      if (input.current) input.current.value = "";
    }
  }

  function onDragOver(e: DragEvent) {
    if (busy || !e.dataTransfer.types.includes("Files")) return;
    e.preventDefault();
    e.dataTransfer.dropEffect = "copy";
    setDragging(true);
  }

  function onDrop(e: DragEvent) {
    e.preventDefault();
    setDragging(false);
    void upload(e.dataTransfer.files?.[0]);
  }

  const iconButton =
    "flex h-10 w-10 shrink-0 items-center justify-center rounded-[10px] text-ink-soft ring-1 ring-inset ring-line hover:bg-tonle-50 hover:text-ink disabled:opacity-50";
  const Icon = kind === "IMAGE" ? ImagePlus : Mic;
  const percent = sending ? Math.round(sending.fraction * 100) : 0;

  return (
    <div
      className={cn("flex flex-col gap-1", !compact && "items-start")}
      onDragOver={onDragOver}
      onDragLeave={(e) => {
        if (!e.currentTarget.contains(e.relatedTarget as Node | null)) setDragging(false);
      }}
      onDrop={onDrop}
    >
      <input
        ref={input}
        type="file"
        accept={ACCEPT[kind]}
        className="sr-only"
        tabIndex={-1}
        aria-hidden
        onChange={(e) => void upload(e.target.files?.[0])}
      />
      {sending && (
        <div
          className={cn(
            "flex items-center gap-3 rounded-[12px] bg-tonle-50 ring-1 ring-inset ring-line",
            compact ? "max-w-[16rem] p-1.5" : "w-full max-w-sm p-2.5",
          )}
        >
          {sending.preview ? (
            <img src={sending.preview} alt="" className={cn("shrink-0 rounded-[8px] object-cover", compact ? "h-9 w-9" : "h-14 w-14")} />
          ) : (
            <span className={cn("flex shrink-0 items-center justify-center rounded-[8px] bg-white text-tonle-700", compact ? "h-9 w-9" : "h-14 w-14")}>
              <Mic size={18} aria-hidden />
            </span>
          )}
          <div className="flex min-w-0 flex-1 flex-col gap-1">
            <p className="truncate text-sm font-semibold text-ink" title={sending.name}>
              {sending.name}
            </p>
            {!compact && (
              <p className="text-xs text-ink-soft">
                {fileSize(sending.size)}
                {sending.dimensions && ` · ${m.dimensions(sending.dimensions.width, sending.dimensions.height)}`}
              </p>
            )}
            <div
              role="progressbar"
              aria-label={m.sending(sending.name)}
              aria-valuemin={0}
              aria-valuemax={100}
              aria-valuenow={percent}
              aria-valuetext={percent >= 100 ? m.checking : m.percent(percent)}
              className="h-1.5 w-full overflow-hidden rounded-full bg-white"
            >
              <div
                className="h-full rounded-full bg-tonle-600 transition-[width] duration-200 ease-out motion-reduce:transition-none"
                style={{ width: `${percent}%` }}
              />
            </div>
            {!compact && <p className="text-xs text-ink-faint">{percent >= 100 ? m.checking : m.percent(percent)}</p>}
          </div>
          <button
            type="button"
            onClick={() => cancel.current?.abort()}
            aria-label={m.cancel}
            title={m.cancel}
            className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-ink-soft hover:bg-white hover:text-ink"
          >
            <X size={15} aria-hidden />
          </button>
        </div>
      )}
      {!value && !sending && (
        <div
          className={cn(
            "flex items-center gap-1.5 rounded-[12px] transition-colors duration-150",
            !compact && "flex-wrap border border-dashed border-line p-2",
            dragging && "bg-tonle-50 ring-2 ring-tonle-400",
            dragging && !compact && "border-transparent",
          )}
        >
          <button
            type="button"
            onClick={() => input.current?.click()}
            aria-label={compact ? m.add(name) : undefined}
            title={compact ? m.add(name) : undefined}
            className={
              compact
                ? iconButton
                : "flex items-center gap-2 rounded-[10px] px-3 py-2 text-sm font-semibold text-tonle-700 ring-1 ring-inset ring-line hover:bg-tonle-50 disabled:opacity-50"
            }
          >
            <Icon size={compact ? 18 : 16} aria-hidden />
            {!compact && (kind === "IMAGE" ? m.addPicture : m.addRecording)}
          </button>
          <button
            type="button"
            onClick={() => setPicking(true)}
            aria-label={m.choose(name)}
            title={compact ? m.choose(name) : undefined}
            className={
              compact
                ? iconButton
                : "flex items-center gap-2 rounded-[10px] px-3 py-2 text-sm font-semibold text-tonle-700 ring-1 ring-inset ring-line hover:bg-tonle-50 disabled:opacity-50"
            }
          >
            <Library size={compact ? 18 : 16} aria-hidden />
            {!compact && m.fromLibrary}
          </button>
          {!compact && <span className="px-1 text-sm text-ink-faint">{dragging ? m.dropHere(name) : m.dropHint}</span>}
        </div>
      )}
      {value && !sending && (
        <div className={cn("flex items-center gap-1.5 rounded-[12px]", dragging && "bg-tonle-50 ring-2 ring-tonle-400")}>
          {kind === "IMAGE" ? (
            <button
              type="button"
              onClick={() => input.current?.click()}
              aria-label={m.replace(name)}
              title={m.replace(name)}
              className="overflow-hidden rounded-[10px] ring-1 ring-line hover:ring-tonle-400"
            >
              <img src={value.url} alt="" className={cn("object-cover", compact ? "h-10 w-10" : "h-20 w-20")} />
            </button>
          ) : (
            <button
              type="button"
              onClick={() => (playing ? stopSpeaking() : void speak(value.url))}
              aria-label={playing ? m.stop(name) : m.play(name)}
              title={playing ? m.stop(name) : m.play(name)}
              className={cn(iconButton, "text-tonle-700", playing && "bg-tonle-50")}
            >
              {playing ? <Square size={15} aria-hidden /> : <Play size={16} aria-hidden />}
            </button>
          )}
          {kind === "AUDIO" && !compact && (
            <button
              type="button"
              onClick={() => input.current?.click()}
              className="rounded-[10px] px-2.5 py-2 text-sm font-semibold text-tonle-700 hover:bg-tonle-50"
            >
              {m.replaceButton}
            </button>
          )}
          <button
            type="button"
            onClick={() => onChange(null)}
            aria-label={m.remove(name)}
            title={m.remove(name)}
            className="flex h-8 w-8 items-center justify-center rounded-full text-ink-soft hover:bg-tonle-50 hover:text-ink"
          >
            <X size={15} aria-hidden />
          </button>
        </div>
      )}
      <MediaPicker
        kind={kind}
        open={picking}
        onClose={() => setPicking(false)}
        purpose={name}
        onPick={(item) => {
          setError(null);
          onChange(item, item);
        }}
      />
      {error && (
        <p role="alert" className={cn("text-xs font-medium text-brick-deep", compact && "max-w-[12rem]")}>
          {error}
        </p>
      )}
    </div>
  );
}

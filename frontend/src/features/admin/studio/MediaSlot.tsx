import { useRef, useState } from "react";
import { ImagePlus, Mic, Play, Square, X } from "lucide-react";
import { cn } from "@/lib/utils/cn";
import type { MediaRef } from "@/lib/api/types";
import { studioApi } from "@/lib/api/studio";
import { ApiError } from "@/lib/api/ApiError";
import { speak, stopSpeaking, usePlaying } from "@/features/child/speaker";

const ACCEPT = {
  IMAGE: "image/png,image/jpeg,image/webp",
  AUDIO: "audio/mpeg,audio/mp4,audio/x-m4a,audio/ogg,audio/wav,audio/x-wav",
};

/** The server's reason for refusing a file (its type, size or dimensions), in the author's words. */
function uploadError(err: unknown): string {
  if (err instanceof ApiError && (err.status === 422 || err.status === 413) && err.problem?.detail) return err.problem.detail;
  return "The upload didn't work. Check your connection and try again.";
}

/**
 * A picture or recording on a question (X11): add one (uploaded and checked by the server),
 * see or hear it, replace or remove it. `compact` fits in a row of answer choices.
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
  onChange: (value: MediaRef | null) => void;
  /** What it belongs to, for its controls' names: "picture for choice 1". */
  name: string;
  compact?: boolean;
}) {
  const input = useRef<HTMLInputElement | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const playing = usePlaying(value?.url);

  async function upload(file: File | undefined) {
    if (!file) return;
    setBusy(true);
    setError(null);
    try {
      const media = await studioApi.uploadMedia(file);
      if (media.type !== kind) {
        setError(kind === "IMAGE" ? "That file is a recording, not a picture." : "That file is a picture, not a recording.");
        return;
      }
      onChange(media);
    } catch (err) {
      setError(uploadError(err));
    } finally {
      setBusy(false);
      if (input.current) input.current.value = "";
    }
  }

  const iconButton =
    "flex h-10 w-10 shrink-0 items-center justify-center rounded-[10px] text-ink-soft ring-1 ring-inset ring-line hover:bg-tonle-50 hover:text-ink disabled:opacity-50";
  const Icon = kind === "IMAGE" ? ImagePlus : Mic;
  const noun = kind === "IMAGE" ? "picture" : "recording";

  return (
    <div className={cn("flex flex-col gap-1", !compact && "items-start")}>
      <input
        ref={input}
        type="file"
        accept={ACCEPT[kind]}
        className="sr-only"
        tabIndex={-1}
        aria-hidden
        onChange={(e) => void upload(e.target.files?.[0])}
      />
      {!value ? (
        <button
          type="button"
          disabled={busy}
          onClick={() => input.current?.click()}
          aria-label={compact ? `Add a ${name}` : undefined}
          title={compact ? `Add a ${name}` : undefined}
          className={
            compact
              ? cn(iconButton, busy && "animate-pulse")
              : "flex items-center gap-2 rounded-[10px] px-3 py-2 text-sm font-semibold text-tonle-700 ring-1 ring-inset ring-line hover:bg-tonle-50 disabled:opacity-50"
          }
        >
          <Icon size={compact ? 18 : 16} aria-hidden />
          {!compact && (busy ? "Uploading…" : `Add a ${noun}`)}
        </button>
      ) : (
        <div className="flex items-center gap-1.5">
          {kind === "IMAGE" ? (
            <button
              type="button"
              onClick={() => input.current?.click()}
              disabled={busy}
              aria-label={`Replace the ${name}`}
              title={`Replace the ${name}`}
              className="overflow-hidden rounded-[10px] ring-1 ring-line hover:ring-tonle-400"
            >
              <img src={value.url} alt="" className={cn("object-cover", compact ? "h-10 w-10" : "h-20 w-20")} />
            </button>
          ) : (
            <button
              type="button"
              onClick={() => (playing ? stopSpeaking() : void speak(value.url))}
              aria-label={`${playing ? "Stop" : "Play"} the ${name}`}
              title={`${playing ? "Stop" : "Play"} the ${name}`}
              className={cn(iconButton, "text-tonle-700", playing && "bg-tonle-50")}
            >
              {playing ? <Square size={15} aria-hidden /> : <Play size={16} aria-hidden />}
            </button>
          )}
          {kind === "AUDIO" && !compact && (
            <button
              type="button"
              onClick={() => input.current?.click()}
              disabled={busy}
              className="rounded-[10px] px-2.5 py-2 text-sm font-semibold text-tonle-700 hover:bg-tonle-50"
            >
              Replace
            </button>
          )}
          <button
            type="button"
            onClick={() => onChange(null)}
            aria-label={`Remove the ${name}`}
            title={`Remove the ${name}`}
            className="flex h-8 w-8 items-center justify-center rounded-full text-ink-soft hover:bg-tonle-50 hover:text-ink"
          >
            <X size={15} aria-hidden />
          </button>
        </div>
      )}
      {error && (
        <p role="alert" className={cn("text-xs font-medium text-brick-deep", compact && "max-w-[12rem]")}>
          {error}
        </p>
      )}
    </div>
  );
}

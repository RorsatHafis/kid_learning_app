import { useEffect, useRef, useState } from "react";
import { Mic, Play, RotateCcw, Square, Check, X } from "lucide-react";
import { cn } from "@/lib/utils/cn";
import { useMessages } from "@/i18n/catalog";
import { mediaMessages } from "./media.messages";
import { MAX_SECONDS, recordingSupported, startCapture, type Capture, type RecorderFailure } from "./recording";

type State =
  | { step: "idle" }
  | { step: "starting" }
  | { step: "recording"; seconds: number }
  | { step: "review"; file: File; url: string };

/**
 * Say it instead of uploading it: records from the microphone, lets the educator listen back, and hands over a WAV
 * file to the slot's ordinary upload (so the server checks it like any other file). Nothing leaves the browser until
 * "Use this recording". Not shown where the browser can't record.
 */
export function VoiceRecorder({
  name,
  compact,
  disabled,
  onRecorded,
}: {
  name: string;
  compact: boolean;
  disabled: boolean;
  onRecorded: (file: File) => void;
}) {
  const m = useMessages(mediaMessages).slot;
  const [state, setState] = useState<State>({ step: "idle" });
  const [error, setError] = useState<string | null>(null);
  const [listening, setListening] = useState(false);
  const capture = useRef<Capture | null>(null);
  const audio = useRef<HTMLAudioElement | null>(null);
  const stopButton = useRef<HTMLButtonElement | null>(null);
  const reviewFirst = useRef<HTMLButtonElement | null>(null);

  // The microphone is let go, and the recording's object URL freed, if the slot goes away mid-way.
  useEffect(() => () => capture.current?.cancel(), []);
  const reviewUrl = state.step === "review" ? state.url : null;
  useEffect(
    () => () => {
      if (reviewUrl) URL.revokeObjectURL(reviewUrl);
    },
    [reviewUrl],
  );

  // A running clock, and the stop at the limit.
  const recording = state.step === "recording";
  useEffect(() => {
    if (!recording) return;
    stopButton.current?.focus();
    const timer = window.setInterval(() => {
      const seconds = capture.current?.seconds() ?? 0;
      if (seconds >= MAX_SECONDS) void finish();
      else setState((now) => (now.step === "recording" ? { step: "recording", seconds: Math.floor(seconds) } : now));
    }, 250);
    return () => window.clearInterval(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [recording]);

  useEffect(() => {
    if (state.step === "review") reviewFirst.current?.focus();
  }, [state.step]);

  if (!recordingSupported()) return null;

  async function begin() {
    setError(null);
    setState({ step: "starting" });
    try {
      capture.current = await startCapture();
      setState({ step: "recording", seconds: 0 });
    } catch (failure) {
      setState({ step: "idle" });
      const known: RecorderFailure[] = ["denied", "noMicrophone", "unavailable"];
      setError(m[known.includes(failure as RecorderFailure) ? (failure as RecorderFailure) : "unavailable"]);
    }
  }

  async function finish() {
    const running = capture.current;
    capture.current = null;
    if (!running) return;
    const wav = await running.stop();
    if (!wav) {
      setState({ step: "idle" });
      setError(m.silent);
      return;
    }
    // The library titles a file after its name, so name it after what it is for and when: "recording of the question 27 Oct, 10:17".
    const when = new Intl.DateTimeFormat(undefined, { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" }).format(new Date());
    const file = new File([wav], `${name} ${when}.wav`.replace(/[\\/]/g, " "), { type: "audio/wav" });
    setState({ step: "review", file, url: URL.createObjectURL(file) });
  }

  function listen() {
    if (state.step !== "review") return;
    if (listening) {
      audio.current?.pause();
      setListening(false);
      return;
    }
    const player = new Audio(state.url);
    audio.current = player;
    player.onended = () => setListening(false);
    setListening(true);
    void player.play().catch(() => setListening(false));
  }

  function leaveReview() {
    audio.current?.pause();
    setListening(false);
    setState({ step: "idle" });
  }

  const pill =
    "flex items-center gap-2 rounded-[10px] px-3 py-2 text-sm font-semibold ring-1 ring-inset disabled:opacity-50";

  return (
    <div className="flex flex-col gap-1">
      {(state.step === "idle" || state.step === "starting") && (
        <button
          type="button"
          onClick={() => void begin()}
          disabled={disabled || state.step === "starting"}
          aria-label={compact ? m.recordName(name) : undefined}
          title={compact ? m.recordName(name) : m.maxLength(MAX_SECONDS)}
          className={
            compact
              ? "flex h-10 w-10 shrink-0 items-center justify-center rounded-[10px] text-ink-soft ring-1 ring-inset ring-line hover:bg-tonle-50 hover:text-ink disabled:opacity-50"
              : cn(pill, "text-tonle-700 ring-line hover:bg-tonle-50")
          }
        >
          <Mic size={compact ? 18 : 16} aria-hidden />
          {!compact && m.record}
        </button>
      )}
      {state.step === "recording" && (
        <div className="flex items-center gap-2" role="status" aria-live="polite">
          <span aria-hidden className="h-2.5 w-2.5 rounded-full bg-brick motion-safe:animate-pulse" />
          <span className="text-sm font-semibold tabular-nums text-ink">{m.recordingFor(state.seconds)}</span>
          <button ref={stopButton} type="button" onClick={() => void finish()} className={cn(pill, "text-brick-deep ring-brick/40 hover:bg-brick-soft")}>
            <Square size={14} aria-hidden /> {m.stopRecording}
          </button>
        </div>
      )}
      {state.step === "review" && (
        <div className={cn("flex flex-wrap items-center gap-1.5", compact && "max-w-[16rem]")}>
          <button ref={reviewFirst} type="button" onClick={listen} className={cn(pill, "text-tonle-700 ring-line hover:bg-tonle-50")}>
            {listening ? <Square size={14} aria-hidden /> : <Play size={14} aria-hidden />}
            {listening ? m.stopListening : m.listenBack}
          </button>
          <button
            type="button"
            onClick={() => {
              const { file } = state;
              leaveReview();
              onRecorded(file);
            }}
            className={cn(pill, "bg-tonle-900 text-white ring-tonle-900 hover:bg-tonle-800")}
          >
            <Check size={14} aria-hidden /> {m.useRecording}
          </button>
          <button
            type="button"
            onClick={() => {
              leaveReview();
              void begin();
            }}
            className={cn(pill, "text-tonle-700 ring-line hover:bg-tonle-50")}
          >
            <RotateCcw size={14} aria-hidden /> {m.recordAgain}
          </button>
          <button
            type="button"
            onClick={leaveReview}
            aria-label={m.discard}
            title={m.discard}
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

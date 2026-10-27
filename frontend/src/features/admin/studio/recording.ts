/**
 * Recording a voice in the Studio, so an educator can say a question or an introduction aloud without another app.
 * The microphone is read through the Web Audio API and written as a mono 16-bit WAV at 22.05 kHz: WAV is one of the
 * formats the server accepts (MediaInspector) and every browser plays, whatever the browser's own recorder would
 * produce (WebM on Chrome, which isn't accepted). A minute is about 2.6 MB, well inside the 5 MB a recording may be.
 */

export const OUTPUT_RATE = 22050;
/** Long enough for a question or a short explanation; it stops by itself after this. */
export const MAX_SECONDS = 60;

export type RecorderFailure = "denied" | "noMicrophone" | "unavailable";

export interface Capture {
  /** Ends the recording and hands back the WAV (null if nothing was heard at all). */
  stop(): Promise<Blob | null>;
  /** Ends it and throws the sound away. */
  cancel(): void;
  /** Seconds recorded so far. */
  seconds(): number;
}

/** Whether this browser, on this page, can record at all (a microphone needs a secure page). */
export function recordingSupported(): boolean {
  return (
    typeof window !== "undefined" &&
    window.isSecureContext &&
    !!navigator.mediaDevices?.getUserMedia &&
    typeof (window.AudioContext ?? (window as unknown as { webkitAudioContext?: unknown }).webkitAudioContext) === "function"
  );
}

/** Opens the microphone and starts keeping what it hears. Rejects with a {@link RecorderFailure}. */
export async function startCapture(): Promise<Capture> {
  let stream: MediaStream;
  try {
    stream = await navigator.mediaDevices.getUserMedia({ audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true } });
  } catch (err) {
    const name = err instanceof DOMException ? err.name : "";
    throw (name === "NotAllowedError" || name === "SecurityError"
      ? "denied"
      : name === "NotFoundError" || name === "OverconstrainedError"
        ? "noMicrophone"
        : "unavailable") satisfies RecorderFailure;
  }
  const Context = window.AudioContext ?? (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
  let context: AudioContext;
  let source: MediaStreamAudioSourceNode;
  let processor: ScriptProcessorNode;
  try {
    context = new Context();
    source = context.createMediaStreamSource(stream);
    // ScriptProcessor is deprecated in favour of AudioWorklet, but it runs everywhere without a separate module file;
    // it only copies samples here.
    processor = context.createScriptProcessor(4096, 1, 1);
  } catch {
    stream.getTracks().forEach((track) => track.stop());
    throw "unavailable" satisfies RecorderFailure;
  }
  const chunks: Float32Array[] = [];
  let samples = 0;
  processor.onaudioprocess = (event) => {
    const input = event.inputBuffer.getChannelData(0);
    chunks.push(new Float32Array(input));
    samples += input.length;
  };
  source.connect(processor);
  processor.connect(context.destination); // a processor only runs while connected; it writes nothing, so it's silent
  if (context.state === "suspended") await context.resume();

  let finished = false;
  function release() {
    if (finished) return;
    finished = true;
    processor.onaudioprocess = null;
    source.disconnect();
    processor.disconnect();
    stream.getTracks().forEach((track) => track.stop());
    void context.close();
  }

  return {
    async stop() {
      release();
      return samples === 0 ? null : encodeWav(chunks, context.sampleRate);
    },
    cancel: release,
    seconds: () => samples / context.sampleRate,
  };
}

/** The chunks as one mono 16-bit PCM WAV at {@link OUTPUT_RATE}, resampled by averaging (plenty for a voice). */
export function encodeWav(chunks: Float32Array[], inputRate: number, outputRate = OUTPUT_RATE): Blob {
  const total = chunks.reduce((n, chunk) => n + chunk.length, 0);
  const all = new Float32Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    all.set(chunk, offset);
    offset += chunk.length;
  }
  const ratio = Math.max(1, inputRate / outputRate);
  const length = Math.floor(total / ratio);
  const pcm = new Int16Array(length);
  for (let i = 0; i < length; i++) {
    const from = Math.floor(i * ratio);
    const to = Math.min(total, Math.floor((i + 1) * ratio));
    let sum = 0;
    for (let j = from; j < to; j++) sum += all[j];
    const value = Math.max(-1, Math.min(1, to > from ? sum / (to - from) : 0));
    pcm[i] = value < 0 ? value * 0x8000 : value * 0x7fff;
  }
  const rate = ratio === 1 ? inputRate : outputRate;
  const header = new DataView(new ArrayBuffer(44));
  const text = (at: number, s: string) => [...s].forEach((c, i) => header.setUint8(at + i, c.charCodeAt(0)));
  text(0, "RIFF");
  header.setUint32(4, 36 + pcm.byteLength, true);
  text(8, "WAVE");
  text(12, "fmt ");
  header.setUint32(16, 16, true); // PCM header size
  header.setUint16(20, 1, true); // PCM
  header.setUint16(22, 1, true); // mono
  header.setUint32(24, rate, true);
  header.setUint32(28, rate * 2, true); // bytes per second
  header.setUint16(32, 2, true); // bytes per frame
  header.setUint16(34, 16, true); // bits per sample
  text(36, "data");
  header.setUint32(40, pcm.byteLength, true);
  return new Blob([header.buffer, pcm.buffer], { type: "audio/wav" });
}

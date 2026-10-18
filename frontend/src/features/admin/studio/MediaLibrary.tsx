import { useRef, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { Archive, AudioLines, Play, RotateCcw, Square, Trash2, Upload } from "lucide-react";
import { studioApi, type MediaDetail, type MediaItem } from "@/lib/api/studio";
import { problemMessage } from "@/lib/api/problemMessage";
import { useAsync } from "@/lib/hooks/useAsync";
import { usePageInRange, useUrlFilters } from "@/lib/hooks/useUrlFilters";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { Pager } from "@/components/ui/Pager";
import { SearchField } from "@/components/ui/SearchField";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/States";
import { PageHeader } from "@/components/workspace/WorkspaceShell";
import { FormField } from "@/features/auth/FormField";
import { speak, stopSpeaking, usePlaying } from "@/features/child/speaker";
import { cn } from "@/lib/utils/cn";
import { StatusBadge, TextAreaField } from "./shared";
import { uploadError } from "./mediaText";
import { StorageMeter } from "./StorageMeter";
import { useStudioScope } from "./studioLogic";
import { messagesNow, useMessages } from "@/i18n/catalog";
import { formatDate, formatNumber } from "@/i18n/format";
import { mediaMessages } from "./media.messages";

const ACCEPT = "image/png,image/jpeg,image/webp,audio/mpeg,audio/mp4,audio/x-m4a,audio/ogg,audio/wav,audio/x-wav";

type Kind = "" | "IMAGE" | "AUDIO";
type Show = "" | "unused" | "archived";

const bytes = (n: number) => {
  const m = messagesNow(mediaMessages);
  return n >= 1024 * 1024 ? m.megabytes(formatNumber(n / (1024 * 1024), { maximumFractionDigits: 1, minimumFractionDigits: 1 })) : m.kilobytes(Math.max(1, Math.round(n / 1024)));
};
const usedText = (n: number) => {
  const m = messagesNow(mediaMessages);
  return n === 0 ? m.notUsed : m.usedIn(n);
};
const nameOf = (item: MediaItem) => {
  const m = messagesNow(mediaMessages);
  return item.title ?? (item.type === "IMAGE" ? m.untitledPicture : m.untitledRecording);
};

// ------------------------------------------------------------------ the library page

/**
 * The media library (X13): every picture and recording the author's questions can use - upload once,
 * use anywhere. Search and filters live in the address; a file in use can be archived (out of the
 * library, still shown where it is used) but not deleted.
 */
export function MediaLibraryPage() {
  const { params, page, update, setPage } = useUrlFilters();
  const kind = (params.get("type") ?? "") as Kind;
  const show = (params.get("show") ?? "") as Show;
  const text = params.get("q") ?? "";
  const list = useAsync(
    () =>
      studioApi.mediaLibrary({
        type: kind || undefined,
        text: text || undefined,
        archived: show === "archived",
        unused: show === "unused",
        page,
      }),
    [kind, show, text, page],
  );
  usePageInRange(list.data, setPage);
  const [openId, setOpenId] = useState<string | null>(null);
  // Uploading, deleting or archiving changes what the storage meter shows.
  const [storageVersion, setStorageVersion] = useState(0);
  const changed = () => {
    list.refetch();
    setStorageVersion((v) => v + 1);
  };
  const m = useMessages(mediaMessages);

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title={m.title}
        description={m.lead}
        actions={<UploadButton onUploaded={changed} />}
      />
      <StorageMeter version={storageVersion} />

      <div className="flex flex-col gap-3 rounded-[16px] bg-white p-4 ring-1 ring-line sm:flex-row sm:flex-wrap sm:items-center">
        <SearchField
          label={m.search}
          placeholder={m.searchHint}
          value={text}
          onSearch={(q) => update({ q: q || undefined })}
          className="sm:min-w-[16rem] sm:flex-1"
        />
        <Segmented
          label={m.kind}
          value={kind}
          options={[
            { value: "", label: m.all },
            { value: "IMAGE", label: m.pictures },
            { value: "AUDIO", label: m.recordings },
          ]}
          onChange={(value) => update({ type: value || undefined })}
        />
        <label className="flex items-center gap-2 text-sm text-ink">
          <span className="font-semibold">{m.show}</span>
          <select
            value={show}
            onChange={(e) => update({ show: e.target.value || undefined })}
            className="rounded-[10px] border border-line-strong bg-white px-3 py-2 text-sm focus:border-tonle-600 focus:outline-none focus:ring-2 focus:ring-tonle-600/15"
          >
            <option value="">{m.inLibrary}</option>
            <option value="unused">{m.unused}</option>
            <option value="archived">{m.archived}</option>
          </select>
        </label>
      </div>

      {list.isLoading && !list.data && <LoadingState label={m.loading} />}
      {list.error && <ErrorState error={list.error} onRetry={list.refetch} />}
      {list.data && list.data.items.length === 0 && (
        <EmptyState
          title={text || kind || show ? m.nothingMatches : m.none}
          description={text || kind || show ? m.tryAnother : m.firstHint}
        />
      )}
      {list.data && list.data.items.length > 0 && (
        <section aria-label={m.media} className={cn("flex flex-col gap-4", list.isLoading && "opacity-60")}>
          <MediaGrid items={list.data.items} onOpen={(item) => setOpenId(item.id)} />
          <Pager page={list.data.page} size={list.data.size} total={list.data.total} onPage={setPage} label={m.media} />
        </section>
      )}

      <MediaDetailDialog id={openId} onClose={() => setOpenId(null)} onChanged={changed} />
    </div>
  );
}

function UploadButton({ onUploaded }: { onUploaded: () => void }) {
  const input = useRef<HTMLInputElement | null>(null);
  const [status, setStatus] = useState<string | null>(null);
  const [errors, setErrors] = useState<string[]>([]);
  const m = useMessages(mediaMessages);

  async function upload(files: FileList | null) {
    if (!files || files.length === 0) return;
    const all = [...files];
    const failed: string[] = [];
    for (const [index, file] of all.entries()) {
      setStatus(all.length > 1 ? m.uploadingOf(index + 1, all.length) : m.uploading);
      try {
        await studioApi.uploadMedia(file);
      } catch (err) {
        failed.push(`${file.name}: ${uploadError(err)}`);
      }
    }
    setStatus(null);
    setErrors(failed);
    if (input.current) input.current.value = "";
    onUploaded();
  }

  return (
    <div className="flex flex-col items-end gap-1">
      <input
        ref={input}
        type="file"
        multiple
        accept={ACCEPT}
        className="sr-only"
        tabIndex={-1}
        aria-hidden
        onChange={(e) => void upload(e.target.files)}
      />
      <Button onClick={() => input.current?.click()} disabled={status !== null}>
        <Upload size={16} aria-hidden /> {status ?? m.upload}
      </Button>
      {errors.length > 0 && (
        <ul role="alert" className="max-w-sm text-right text-xs font-medium text-brick-deep">
          {errors.map((e) => (
            <li key={e}>{e}</li>
          ))}
        </ul>
      )}
    </div>
  );
}

function Segmented<T extends string>({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: T;
  options: { value: T; label: string }[];
  onChange: (value: T) => void;
}) {
  return (
    <div role="group" aria-label={label} className="inline-flex w-fit rounded-pill bg-paper p-1 ring-1 ring-line">
      {options.map((option) => (
        <button
          key={option.value || "all"}
          type="button"
          aria-pressed={value === option.value}
          onClick={() => onChange(option.value)}
          className={cn(
            "rounded-pill px-3.5 py-1.5 text-sm font-semibold transition-colors",
            value === option.value ? "bg-white text-tonle-950 shadow-tight" : "text-ink-soft hover:text-ink",
          )}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}

// ------------------------------------------------------------------ the grid (library and picker)

export function MediaGrid({
  items,
  onOpen,
  actionLabel,
}: {
  items: MediaItem[];
  onOpen: (item: MediaItem) => void;
  actionLabel?: string;
}) {
  const m = useMessages(mediaMessages);
  return (
    <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6">
      {items.map((item) => (
        <li key={item.id}>
          <button
            type="button"
            onClick={() => onOpen(item)}
            aria-label={m.itemLabel(actionLabel ?? m.open, nameOf(item), item.type === "IMAGE" ? m.picture : m.recording, usedText(item.usedBy).toLowerCase())}
            className="group flex h-full w-full flex-col overflow-hidden rounded-[14px] bg-white text-left ring-1 ring-line transition-[box-shadow,transform] duration-150 hover:-translate-y-0.5 hover:shadow-paper hover:ring-tonle-300 focus-visible:ring-2 focus-visible:ring-tonle-600"
          >
            <MediaThumb item={item} />
            <span className="flex min-w-0 flex-col gap-0.5 px-3 py-2.5">
              <span className="truncate text-sm font-semibold text-ink">{nameOf(item)}</span>
              <span className="truncate text-xs text-ink-soft">
                {usedText(item.usedBy)}
                {!item.editable && ` · ${m.platform}`}
              </span>
            </span>
          </button>
        </li>
      ))}
    </ul>
  );
}

function MediaThumb({ item }: { item: MediaItem }) {
  const m = useMessages(mediaMessages);
  return (
    <span className="relative flex aspect-[4/3] items-center justify-center bg-paper">
      {item.type === "IMAGE" ? (
        <img src={item.url} alt="" loading="lazy" className="h-full w-full object-contain p-2" />
      ) : (
        <span aria-hidden className="flex flex-col items-center gap-1.5 text-tonle-600">
          <AudioLines size={34} strokeWidth={1.75} />
          <span className="text-[11px] font-semibold uppercase tracking-wide text-ink-faint">{m.recordingTag}</span>
        </span>
      )}
      {item.archived && (
        <span className="absolute left-2 top-2 rounded-pill bg-tonle-950/80 px-2 py-0.5 text-[11px] font-semibold text-white">
          {m.archived}
        </span>
      )}
    </span>
  );
}

// ------------------------------------------------------------------ one item

function MediaDetailDialog({ id, onClose, onChanged }: { id: string | null; onClose: () => void; onChanged: () => void }) {
  const detail = useAsync(() => (id ? studioApi.mediaDetail(id) : Promise.resolve(null)), [id]);
  const data = detail.data;
  const m = useMessages(mediaMessages);
  return (
    <Dialog open={id !== null} onClose={onClose} title={data ? nameOf(data.item) : m.media} size="lg">
      {detail.isLoading && !data && <LoadingState label={m.loadingOne} />}
      {detail.error && <ErrorState error={detail.error} onRetry={detail.refetch} />}
      {data && (
        <MediaDetailBody
          key={data.item.id}
          detail={data}
          onChanged={() => {
            detail.refetch();
            onChanged();
          }}
          onDeleted={() => {
            onClose();
            onChanged();
          }}
        />
      )}
    </Dialog>
  );
}

function MediaDetailBody({
  detail,
  onChanged,
  onDeleted,
}: {
  detail: MediaDetail;
  onChanged: () => void;
  onDeleted: () => void;
}) {
  const { item, uses } = detail;
  const { base } = useStudioScope();
  const [title, setTitle] = useState(item.title ?? "");
  const [description, setDescription] = useState(item.description ?? "");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const m = useMessages(mediaMessages);

  async function act(run: () => Promise<unknown>, done: string, after = onChanged) {
    setBusy(true);
    setMessage(null);
    try {
      await run();
      setMessage({ ok: true, text: done });
      after();
    } catch (err) {
      setMessage({ ok: false, text: problemMessage(err, m.failed) });
    } finally {
      setBusy(false);
    }
  }

  function save(e: FormEvent) {
    e.preventDefault();
    void act(
      () => studioApi.describeMedia(item.id, { title: title.trim() || null, description: description.trim() || null }),
      m.saved,
    );
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
      <div className="flex flex-col gap-4">
        <div className="flex min-h-48 items-center justify-center rounded-[14px] bg-paper p-3 ring-1 ring-line">
          {item.type === "IMAGE" ? (
            <img src={item.url} alt={item.description ?? ""} className="h-64 w-full object-contain" />
          ) : (
            <AudioPreview item={item} />
          )}
        </div>
        <dl className="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
          <dt className="text-ink-soft">{m.kind}</dt>
          <dd className="text-ink">
            {item.type === "IMAGE" ? m.pictureKind : m.recordingKind} · {item.mimeType.split("/")[1]?.toUpperCase()}
          </dd>
          <dt className="text-ink-soft">{m.size}</dt>
          <dd className="text-ink">
            {bytes(item.sizeBytes)}
            {item.width && item.height ? ` · ${item.width} × ${item.height} px` : ""}
          </dd>
          {item.createdAt && (
            <>
              <dt className="text-ink-soft">{m.added}</dt>
              <dd className="text-ink">{formatDate(item.createdAt, { day: "numeric", month: "short", year: "numeric" })}</dd>
            </>
          )}
          <dt className="text-ink-soft">{m.owner}</dt>
          <dd className="text-ink">{item.editable ? m.yours : m.platformMedia}</dd>
        </dl>
      </div>

      <div className="flex flex-col gap-5">
        {item.editable ? (
          <form onSubmit={save} className="flex flex-col gap-3">
            <FormField
              label={m.titleField}
              value={title}
              maxLength={200}
              onChange={(e) => setTitle(e.target.value)}
              placeholder={m.titleHint}
            />
            <TextAreaField
              label={item.type === "IMAGE" ? m.showsField : m.saidField}
              value={description}
              maxLength={500}
              onChange={(e) => setDescription(e.target.value)}
            />
            <Button type="submit" variant="outline" disabled={busy} className="w-fit">
              {m.saveDetails}
            </Button>
          </form>
        ) : (
          item.description && <p className="text-sm text-ink">{item.description}</p>
        )}

        <section aria-labelledby="media-uses" className="flex flex-col gap-2">
          <h3 id="media-uses" className="text-sm font-semibold text-tonle-950">
            {uses.length === 0 ? m.noUses : m.uses(uses.length)}
          </h3>
          {uses.length > 0 && (
            <ul className="flex max-h-56 flex-col divide-y divide-line overflow-y-auto rounded-[12px] ring-1 ring-line">
              {uses.map((use) => (
                <li key={`${use.versionId}:${use.field}`}>
                  <Link
                    to={`${base}/studio/${use.subjectId}/questions/${use.questionId}`}
                    className="flex flex-col gap-1 px-3 py-2.5 text-sm hover:bg-paper"
                  >
                    <span className="line-clamp-2 font-medium text-ink">{use.prompt}</span>
                    <span className="flex flex-wrap items-center gap-2 text-xs text-ink-soft">
                      {m.useLine(m.fields[use.field] ?? use.field, use.versionNumber)} <StatusBadge status={use.status} />
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
          {uses.some((u) => u.status !== "DRAFT") && (
            <p className="text-xs text-ink-soft">{m.publishedKeep}</p>
          )}
        </section>

        {item.editable && (
          <div className="flex flex-wrap gap-2 border-t border-line pt-4">
            {item.archived ? (
              <Button
                variant="outline"
                disabled={busy}
                onClick={() => void act(() => studioApi.restoreMedia(item.id), m.backInLibrary)}
              >
                <RotateCcw size={15} aria-hidden /> {m.restore}
              </Button>
            ) : (
              <Button
                variant="outline"
                disabled={busy}
                onClick={() =>
                  void act(() => studioApi.archiveMedia(item.id), m.archivedDone)
                }
              >
                <Archive size={15} aria-hidden /> {m.archive}
              </Button>
            )}
            {uses.length === 0 &&
              (confirmDelete ? (
                <>
                  <Button
                    disabled={busy}
                    className="bg-brick-deep hover:bg-brick"
                    onClick={() => void act(() => studioApi.deleteMedia(item.id), m.deleted, onDeleted)}
                  >
                    <Trash2 size={15} aria-hidden /> {m.deleteForGood}
                  </Button>
                  <Button variant="ghost" onClick={() => setConfirmDelete(false)}>
                    {m.keep}
                  </Button>
                </>
              ) : (
                <Button variant="ghost" disabled={busy} onClick={() => setConfirmDelete(true)}>
                  <Trash2 size={15} aria-hidden /> {m.delete}
                </Button>
              ))}
          </div>
        )}
        {message && (
          <p
            role={message.ok ? "status" : "alert"}
            className={cn("text-sm font-medium", message.ok ? "text-palm-deep" : "text-brick-deep")}
          >
            {message.text}
          </p>
        )}
      </div>
    </div>
  );
}

function AudioPreview({ item }: { item: MediaItem }) {
  const playing = usePlaying(item.url);
  const m = useMessages(mediaMessages);
  return (
    <button
      type="button"
      onClick={() => (playing ? stopSpeaking() : void speak(item.url))}
      className="flex items-center gap-3 rounded-pill bg-white px-5 py-3 font-semibold text-tonle-700 shadow-tight ring-1 ring-line hover:bg-tonle-50"
    >
      {playing ? <Square size={16} aria-hidden /> : <Play size={16} aria-hidden />}
      {playing ? m.stop : m.playRecording}
    </button>
  );
}

// ------------------------------------------------------------------ choosing from the library

/** Pick a picture or recording already in the library (X13), or upload a new one from here. */
export function MediaPicker({
  kind,
  open,
  onClose,
  onPick,
  purpose,
}: {
  kind: "IMAGE" | "AUDIO";
  open: boolean;
  onClose: () => void;
  onPick: (item: MediaItem) => void;
  /** What it's for: "picture for choice 1". */
  purpose: string;
}) {
  const [text, setText] = useState("");
  const [page, setPage] = useState(0);
  const list = useAsync(
    () => (open ? studioApi.mediaLibrary({ type: kind, text: text || undefined, page, size: 12 }) : Promise.resolve(null)),
    [open, kind, text, page],
  );
  const m = useMessages(mediaMessages);
  const image = kind === "IMAGE";

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={image ? m.choosePicture : m.chooseRecording}
      description={m.forPurpose(purpose)}
      size="lg"
    >
      <div className="flex flex-col gap-4">
        <SearchField
          label={image ? m.searchPictures : m.searchRecordings}
          placeholder={m.searchHint}
          value={text}
          onSearch={(q) => {
            setText(q);
            setPage(0);
          }}
        />
        {list.isLoading && !list.data && <LoadingState label={m.loadingOne} />}
        {list.error && <ErrorState error={list.error} onRetry={list.refetch} />}
        {list.data && list.data.items.length === 0 && (
          <EmptyState
            title={text ? m.nothingMatches : image ? m.noPictures : m.noRecordings}
            description={text ? m.tryAnotherSearch : image ? m.uploadPictureHint : m.uploadRecordingHint}
          />
        )}
        {list.data && list.data.items.length > 0 && (
          <>
            <MediaGrid
              items={list.data.items}
              actionLabel={m.use}
              onOpen={(item) => {
                onPick(item);
                onClose();
              }}
            />
            <Pager page={list.data.page} size={list.data.size} total={list.data.total} onPage={setPage} label={image ? m.pictures : m.recordings} />
          </>
        )}
      </div>
    </Dialog>
  );
}

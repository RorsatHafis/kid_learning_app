import { useCallback, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ChevronRight } from "lucide-react";
import type { QuestionType } from "@/lib/api/types";
import { studioApi, type PublicationStatus } from "@/lib/api/studio";
import { useAsync } from "@/lib/hooks/useAsync";
import { usePageInRange, useUrlFilters } from "@/lib/hooks/useUrlFilters";
import { ListCount, Pager } from "@/components/ui/Pager";
import { SearchField } from "@/components/ui/SearchField";
import { cn } from "@/lib/utils/cn";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/States";
import { QuestionDraftForm } from "./QuestionDraftForm";
import { AUTHORABLE_TYPES, currentStatus, errorText, questionTypeLabel, useStudio, useComposer, useArchivedView } from "./studioLogic";
import { ComposerPanel, NewButton, Panel, SelectField, StatusBadge } from "./shared";
import { ArchivedSwitch } from "./Lifecycle";
import { useMessages } from "@/i18n/catalog";
import { editorMessages } from "./editor.messages";

const PAGE_SIZE = 25;
const STATUS_FILTERS: PublicationStatus[] = ["DRAFT", "PUBLISHED", "RETIRED"];

/**
 * The subject's question bank. It grows with every activity written, so the server searches,
 * filters and pages it; the filters live in the address, so going back to the list from a
 * question returns to the same page of the same search.
 */
export function QuestionsPage() {
  const { catalog } = useStudio();
  const { params, page, update, setPage } = useUrlFilters();
  const text = params.get("q") ?? "";
  const type = (params.get("type") as QuestionType | null) ?? undefined;
  const status = (params.get("status") as PublicationStatus | null) ?? undefined;
  const skillParam = params.get("skill");
  const skillId = skillParam && catalog.skills.some((s) => s.id === skillParam) ? skillParam : undefined;
  const search = useCallback((q: string) => update({ q: q || undefined }), [update]);

  const [archived, setArchived] = useArchivedView();
  const questions = useAsync(
    () => studioApi.questions(catalog.subject.id, { text, type, status, skillId, archived, page, size: PAGE_SIZE }),
    [catalog.subject.id, text, type, status, skillId, archived, page],
  );
  const filtered = Boolean(text || type || status || skillId);
  const composer = useComposer(!archived && questions.data?.total === 0 && !filtered);
  const data = questions.data;

  usePageInRange(data, setPage);
  const m = useMessages(editorMessages).questions;

  return (
    <div className={cn("grid grid-cols-1 gap-6", composer.open && "lg:grid-cols-[minmax(0,1fr)_400px]")}>
      <Panel
        title={archived ? m.archivedTitle : m.title}
        action={
          <span className="flex flex-wrap items-center gap-3">
            <ArchivedSwitch archived={archived} onChange={setArchived} list="questions" />
            {!composer.open && !archived && <NewButton label={m.newQuestion} onClick={composer.show} />}
          </span>
        }
      >
        <div role="search" aria-label={m.find} className="mb-4 flex flex-col gap-3">
          <SearchField label={m.search} value={text} onSearch={search} placeholder={m.searchHint} />
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
            <SelectField label={m.type} value={type ?? ""} onChange={(e) => update({ type: e.target.value || undefined })}>
              <option value="">{m.anyType}</option>
              {AUTHORABLE_TYPES.map((t) => (
                <option key={t} value={t}>
                  {questionTypeLabel(t)}
                </option>
              ))}
            </SelectField>
            <SelectField
              label={m.latestVersion}
              value={status ?? ""}
              onChange={(e) => update({ status: e.target.value || undefined })}
            >
              <option value="">{m.anyStatus}</option>
              {STATUS_FILTERS.map((f) => (
                <option key={f} value={f}>
                  {m.filterStatuses[f]}
                </option>
              ))}
            </SelectField>
            <div className="col-span-2 sm:col-span-1">
              <SelectField label={m.skill} value={skillId ?? ""} onChange={(e) => update({ skill: e.target.value || undefined })}>
                <option value="">{m.anySkill}</option>
                {catalog.skills.map((skill) => (
                  <option key={skill.id} value={skill.id}>
                    {skill.name}
                  </option>
                ))}
              </SelectField>
            </div>
          </div>
          {data && (
            <ListCount
              total={data.total}
              counted={m.counted(data.total)}
              filtered={filtered}
              onClear={() => update({ q: undefined, type: undefined, status: undefined, skill: undefined })}
            />
          )}
        </div>
        {questions.isLoading && !data && <LoadingState label={m.loading} />}
        {questions.error && <ErrorState error={questions.error} onRetry={questions.refetch} />}
        {data && data.total === 0 && !filtered && (
          <EmptyState
            title={m.none}
            description={m.firstHint}
            action={!composer.open && !archived ? <NewButton label={m.newQuestion} onClick={composer.show} /> : undefined}
          />
        )}
        {data && data.total === 0 && filtered && <p className="text-sm text-ink-soft">{m.noMatch}</p>}
        {data && data.items.length > 0 && (
          <div
            className={cn("-mx-5 -mb-5 transition-opacity sm:-mx-6 sm:-mb-6", questions.isLoading && "opacity-60")}
            aria-busy={questions.isLoading}
          >
            <div
              aria-hidden
              className="hidden grid-cols-[minmax(0,1fr)_9rem_7.5rem_1.25rem] gap-4 border-y border-line bg-paper px-6 py-2 text-[0.8125rem] text-ink-faint sm:grid"
            >
              <span>{m.question}</span>
              <span>{m.type}</span>
              <span>{m.status}</span>
              <span />
            </div>
            <ul aria-label={m.title} className="divide-y divide-line border-t border-line sm:border-t-0">
              {data.items.map((q) => {
                const current = currentStatus(q.versions);
                return (
                  <li key={q.id}>
                    <Link
                      to={q.id}
                      className="group grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-4 gap-y-1 px-6 py-3.5 transition-colors hover:bg-paper focus-visible:bg-paper sm:grid-cols-[minmax(0,1fr)_9rem_7.5rem_1.25rem]"
                    >
                      <span className="min-w-0">
                        <span className="block truncate font-medium text-tonle-950 group-hover:underline group-hover:underline-offset-4">
                          {q.latestPrompt}
                        </span>
                        <span className="block text-xs text-ink-faint">
                          {current.label}
                          {q.skillIds.length === 0 && <span className="text-light-deep"> · {m.noSkill}</span>}
                        </span>
                      </span>
                      <span className="hidden text-sm text-ink-soft sm:block">{questionTypeLabel(q.questionType)}</span>
                      <span>{current.status && <StatusBadge status={current.status} />}</span>
                      <span className="hidden sm:block">
                        <ChevronRight size={16} className="text-ink-faint" aria-hidden />
                      </span>
                    </Link>
                  </li>
                );
              })}
            </ul>
            <Pager
              label={m.title}
              page={data.page}
              size={data.size}
              total={data.total}
              onPage={setPage}
              className="border-t border-line px-6 py-3"
            />
          </div>
        )}
      </Panel>
      {composer.open && (
        <div className="order-first lg:order-none lg:sticky lg:top-6 lg:self-start">
          <NewQuestion onClose={composer.hide} focusOnOpen={composer.requested} />
        </div>
      )}
    </div>
  );
}

function NewQuestion({ onClose, focusOnOpen }: { onClose: () => void; focusOnOpen: boolean }) {
  const { catalog } = useStudio();
  const navigate = useNavigate();
  const [type, setType] = useState<QuestionType>("MULTIPLE_CHOICE");
  const [skillIds, setSkillIds] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const m = useMessages(editorMessages).questions;

  return (
    <ComposerPanel title={m.newQuestion} onClose={onClose} focusOnOpen={focusOnOpen}>
      {catalog.skills.length === 0 ? (
        <p className="text-sm text-ink-soft">
          {m.skillFirst}
        </p>
      ) : (
        <div className="flex flex-col gap-4">
          <SelectField label={m.type} value={type} onChange={(e) => setType(e.target.value as QuestionType)}>
            {AUTHORABLE_TYPES.map((t) => (
              <option key={t} value={t}>
                {questionTypeLabel(t)}
              </option>
            ))}
          </SelectField>
          <QuestionDraftForm
            key={type}
            questionType={type}
            initial={{ prompt: "", correctAnswer: null, difficultyLevel: null }}
            submitLabel={m.createDraft}
            busy={busy}
            onSubmit={async (draft) => {
              setBusy(true);
              setError(null);
              try {
                const created = await studioApi.createQuestion({
                  ...draft,
                  subjectId: catalog.subject.id,
                  questionType: type,
                  skillIds,
                });
                navigate(created.id);
              } catch (err) {
                setError(errorText(err));
                setBusy(false);
              }
            }}
          >
            <fieldset className="flex flex-col gap-2">
              <legend className="mb-1 text-sm font-semibold text-ink">{m.evidenceFor}</legend>
              {catalog.skills.map((skill) => (
                <label key={skill.id} className="flex items-center gap-2 text-sm text-ink">
                  <input
                    type="checkbox"
                    className="h-4 w-4 accent-tonle-700"
                    checked={skillIds.includes(skill.id)}
                    onChange={(e) =>
                      setSkillIds(e.target.checked ? [...skillIds, skill.id] : skillIds.filter((id) => id !== skill.id))
                    }
                  />
                  {skill.name}
                </label>
              ))}
            </fieldset>
            {error && (
              <p role="alert" className="text-sm font-medium text-brick-deep">
                {error}
              </p>
            )}
          </QuestionDraftForm>
        </div>
      )}
    </ComposerPanel>
  );
}

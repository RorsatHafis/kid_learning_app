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
import { AUTHORABLE_TYPES, currentStatus, errorText, QUESTION_TYPE_LABEL, useStudio, useComposer } from "./studioLogic";
import { ComposerPanel, NewButton, Panel, SelectField, StatusBadge } from "./shared";

const PAGE_SIZE = 25;
const STATUS_FILTERS: { value: PublicationStatus; label: string }[] = [
  { value: "DRAFT", label: "Draft" },
  { value: "PUBLISHED", label: "Published" },
  { value: "RETIRED", label: "Retired" },
];

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

  const questions = useAsync(
    () => studioApi.questions(catalog.subject.id, { text, type, status, skillId, page, size: PAGE_SIZE }),
    [catalog.subject.id, text, type, status, skillId, page],
  );
  const filtered = Boolean(text || type || status || skillId);
  const composer = useComposer(questions.data?.total === 0 && !filtered);
  const data = questions.data;

  usePageInRange(data, setPage);

  return (
    <div className={cn("grid grid-cols-1 gap-6", composer.open && "lg:grid-cols-[minmax(0,1fr)_400px]")}>
      <Panel title="Questions" action={!composer.open && <NewButton label="New question" onClick={composer.show} />}>
        <div role="search" aria-label="Find questions" className="mb-4 flex flex-col gap-3">
          <SearchField label="Search questions" value={text} onSearch={search} placeholder="Search by question text" />
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
            <SelectField label="Type" value={type ?? ""} onChange={(e) => update({ type: e.target.value || undefined })}>
              <option value="">Any type</option>
              {AUTHORABLE_TYPES.map((t) => (
                <option key={t} value={t}>
                  {QUESTION_TYPE_LABEL[t]}
                </option>
              ))}
            </SelectField>
            <SelectField
              label="Latest version"
              value={status ?? ""}
              onChange={(e) => update({ status: e.target.value || undefined })}
            >
              <option value="">Any status</option>
              {STATUS_FILTERS.map((f) => (
                <option key={f.value} value={f.value}>
                  {f.label}
                </option>
              ))}
            </SelectField>
            <div className="col-span-2 sm:col-span-1">
              <SelectField label="Skill" value={skillId ?? ""} onChange={(e) => update({ skill: e.target.value || undefined })}>
                <option value="">Any skill</option>
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
              noun={["question", "questions"]}
              filtered={filtered}
              onClear={() => update({ q: undefined, type: undefined, status: undefined, skill: undefined })}
            />
          )}
        </div>
        {questions.isLoading && !data && <LoadingState label="Loading questions…" />}
        {questions.error && <ErrorState error={questions.error} onRetry={questions.refetch} />}
        {data && data.total === 0 && !filtered && (
          <EmptyState title="No questions yet" description="Write the first one in the New question form." />
        )}
        {data && data.total === 0 && filtered && <p className="text-sm text-ink-soft">No question matches these filters.</p>}
        {data && data.items.length > 0 && (
          <div
            className={cn("-mx-5 -mb-5 transition-opacity sm:-mx-6 sm:-mb-6", questions.isLoading && "opacity-60")}
            aria-busy={questions.isLoading}
          >
            <div
              aria-hidden
              className="hidden grid-cols-[minmax(0,1fr)_9rem_7.5rem_1.25rem] gap-4 border-y border-line bg-paper px-6 py-2 text-[0.8125rem] text-ink-faint sm:grid"
            >
              <span>Question</span>
              <span>Type</span>
              <span>Status</span>
              <span />
            </div>
            <ul aria-label="Questions" className="divide-y divide-line border-t border-line sm:border-t-0">
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
                          {q.skillIds.length === 0 && <span className="text-light-deep"> · no skill tagged</span>}
                        </span>
                      </span>
                      <span className="hidden text-sm text-ink-soft sm:block">{QUESTION_TYPE_LABEL[q.questionType]}</span>
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
              label="Questions"
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

  return (
    <ComposerPanel title="New question" onClose={onClose} focusOnOpen={focusOnOpen}>
      {catalog.skills.length === 0 ? (
        <p className="text-sm text-ink-soft">
          Add a skill first (Skills &amp; objectives): every question records evidence for at least one skill.
        </p>
      ) : (
        <div className="flex flex-col gap-4">
          <SelectField label="Type" value={type} onChange={(e) => setType(e.target.value as QuestionType)}>
            {AUTHORABLE_TYPES.map((t) => (
              <option key={t} value={t}>
                {QUESTION_TYPE_LABEL[t]}
              </option>
            ))}
          </SelectField>
          <QuestionDraftForm
            key={type}
            questionType={type}
            initial={{ prompt: "", correctAnswer: null, difficultyLevel: null }}
            submitLabel="Create draft"
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
              <legend className="mb-1 text-sm font-semibold text-ink">Skills this question gives evidence for</legend>
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

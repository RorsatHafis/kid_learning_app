import { useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ChevronRight, Search } from "lucide-react";
import type { QuestionType } from "@/lib/api/types";
import { studioApi } from "@/lib/api/studio";
import { useAsync } from "@/lib/hooks/useAsync";
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/States";
import { QuestionDraftForm } from "./QuestionDraftForm";
import {
  AUTHORABLE_TYPES,
  currentStatus,
  errorText,
  QUESTION_TYPE_LABEL,
  useStudio,
} from "./studioLogic";
import { Panel, SelectField, StatusBadge } from "./shared";

export function QuestionsPage() {
  const { catalog } = useStudio();
  const questions = useAsync(
    () => studioApi.questions(catalog.subject.id),
    [catalog.subject.id],
  );
  const [query, setQuery] = useState("");

  const shown = useMemo(
    () =>
      (questions.data ?? []).filter(
        (q) =>
          !query.trim() ||
          (q.latestPrompt ?? "")
            .toLowerCase()
            .includes(query.trim().toLowerCase()),
      ),
    [questions.data, query],
  );

  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_400px]">
      <Panel
        title={`Questions${questions.data ? ` (${questions.data.length})` : ""}`}
      >
        <label className="mb-4 flex h-10 items-center gap-2 rounded-[10px] border border-line-strong bg-white px-3 focus-within:border-tonle-600 focus-within:ring-2 focus-within:ring-tonle-600/15">
          <Search size={16} className="text-ink-soft" aria-hidden />
          <span className="sr-only">Search questions</span>
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search by question text"
            className="w-full bg-transparent text-sm outline-none"
          />
        </label>
        {questions.isLoading && !questions.data && (
          <LoadingState label="Loading questions…" />
        )}
        {questions.error && (
          <ErrorState error={questions.error} onRetry={questions.refetch} />
        )}
        {questions.data && questions.data.length === 0 && (
          <EmptyState
            title="No questions yet"
            description="Write the first one with the form alongside."
          />
        )}
        {questions.data && questions.data.length > 0 && shown.length === 0 && (
          <p className="text-sm text-ink-soft">
            No question matches “{query}”.
          </p>
        )}
        {shown.length > 0 && (
          <div className="-mx-5 -mb-5 sm:-mx-6 sm:-mb-6">
            <div
              aria-hidden
              className="hidden grid-cols-[minmax(0,1fr)_9rem_7.5rem_1.25rem] gap-4 border-y border-line bg-paper px-6 py-2 text-[0.8125rem] text-ink-faint sm:grid"
            >
              <span>Question</span>
              <span>Type</span>
              <span>Status</span>
              <span />
            </div>
            <ul
              aria-label="Questions"
              className="divide-y divide-line border-t border-line sm:border-t-0"
            >
              {shown.map((q) => {
                const status = currentStatus(q.versions);
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
                          {status.label}
                          {q.skillIds.length === 0 && (
                            <span className="text-light-deep">
                              {" "}
                              · no skill tagged
                            </span>
                          )}
                        </span>
                      </span>
                      <span className="hidden text-sm text-ink-soft sm:block">
                        {QUESTION_TYPE_LABEL[q.questionType]}
                      </span>
                      <span>
                        {status.status && (
                          <StatusBadge status={status.status} />
                        )}
                      </span>
                      <span className="hidden sm:block">
                        <ChevronRight
                          size={16}
                          className="text-ink-faint"
                          aria-hidden
                        />
                      </span>
                    </Link>
                  </li>
                );
              })}
            </ul>
          </div>
        )}
      </Panel>
      <NewQuestion />
    </div>
  );
}

function NewQuestion() {
  const { catalog } = useStudio();
  const navigate = useNavigate();
  const [type, setType] = useState<QuestionType>("MULTIPLE_CHOICE");
  const [skillIds, setSkillIds] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  return (
    <Panel title="New question" className="self-start">
      {catalog.skills.length === 0 ? (
        <p className="text-sm text-ink-soft">
          Add a skill first (Skills &amp; objectives): every question records
          evidence for at least one skill.
        </p>
      ) : (
        <div className="flex flex-col gap-4">
          <SelectField
            label="Type"
            value={type}
            onChange={(e) => setType(e.target.value as QuestionType)}
          >
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
              <legend className="mb-1 text-sm font-semibold text-ink">
                Skills this question gives evidence for
              </legend>
              {catalog.skills.map((skill) => (
                <label
                  key={skill.id}
                  className="flex items-center gap-2 text-sm text-ink"
                >
                  <input
                    type="checkbox"
                    className="h-4 w-4 accent-tonle-700"
                    checked={skillIds.includes(skill.id)}
                    onChange={(e) =>
                      setSkillIds(
                        e.target.checked
                          ? [...skillIds, skill.id]
                          : skillIds.filter((id) => id !== skill.id),
                      )
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
    </Panel>
  );
}

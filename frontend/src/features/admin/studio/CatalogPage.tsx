import { useState, type FormEvent } from "react";
import { Plus } from "lucide-react";
import { studioApi, type CatalogObjective, type CatalogSkill } from "@/lib/api/studio";
import { Button } from "@/components/ui/Button";
import { FormField } from "@/features/auth/FormField";
import { Panel, SkillChips, TextAreaField } from "./shared";
import { codeFrom, errorText, useStudio } from "./studioLogic";

/**
 * The educational anchor: skills (what evidence is recorded against) and learning
 * objectives (the groups of skills a curriculum is built from). ADMIN-only on the
 * server; teachers never redefine official skill semantics.
 */
export function CatalogPage() {
  const { catalog } = useStudio();

  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
      <div className="flex flex-col gap-6">
        <Panel title={`Skills (${catalog.skills.length})`}>
          {catalog.skills.length === 0 ? (
            <p className="text-sm text-muted">No skills yet. Skills are what children&apos;s answers count as evidence for.</p>
          ) : (
            <ul className="flex flex-col divide-y divide-hairline">
              {catalog.skills.map((skill) => (
                <SkillRow key={skill.id} skill={skill} />
              ))}
            </ul>
          )}
        </Panel>
        <NewSkill />
      </div>
      <div className="flex flex-col gap-6">
        <Panel title={`Learning objectives (${catalog.objectives.length})`}>
          {catalog.objectives.length === 0 ? (
            <p className="text-sm text-muted">No objectives yet. A curriculum is a sequence of objectives.</p>
          ) : (
            <ul className="flex flex-col divide-y divide-hairline">
              {catalog.objectives.map((objective) => (
                <ObjectiveRow key={objective.id} objective={objective} />
              ))}
            </ul>
          )}
        </Panel>
        <NewObjective />
      </div>
    </div>
  );
}

function SkillRow({ skill }: { skill: CatalogSkill }) {
  const { catalog, refreshCatalog } = useStudio();
  const [prerequisite, setPrerequisite] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const candidates = catalog.skills.filter((s) => s.id !== skill.id && !skill.prerequisiteSkillIds.includes(s.id));

  async function add() {
    setBusy(true);
    setError(null);
    try {
      await studioApi.addPrerequisite(skill.id, prerequisite);
      setPrerequisite("");
      refreshCatalog();
    } catch (err) {
      setError(errorText(err, "That prerequisite would create a loop."));
    } finally {
      setBusy(false);
    }
  }

  return (
    <li className="flex flex-col gap-2 py-4 first:pt-0 last:pb-0">
      <div>
        <p className="font-semibold text-ink">{skill.name}</p>
        <p className="text-xs font-semibold tracking-wide text-muted">{skill.code}</p>
        {skill.description && <p className="mt-1 text-sm text-muted">{skill.description}</p>}
      </div>
      {skill.prerequisiteSkillIds.length > 0 && (
        <div className="flex flex-wrap items-center gap-2 text-sm text-muted">
          Builds on <SkillChips skillIds={skill.prerequisiteSkillIds} catalog={catalog} />
        </div>
      )}
      {candidates.length > 0 && (
        <div className="flex flex-wrap items-center gap-2">
          <label className="sr-only" htmlFor={`pre-${skill.id}`}>
            Add a prerequisite for {skill.name}
          </label>
          <select
            id={`pre-${skill.id}`}
            value={prerequisite}
            onChange={(e) => setPrerequisite(e.target.value)}
            className="rounded-2xl border-[1.5px] border-hairline bg-white px-3 py-2 text-sm"
          >
            <option value="">Add a prerequisite…</option>
            {candidates.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
          <Button variant="outline" size="md" className="px-4 py-2" disabled={!prerequisite || busy} onClick={add}>
            Add
          </Button>
        </div>
      )}
      {error && (
        <p role="alert" className="text-sm font-medium text-brand-redStrong">
          {error}
        </p>
      )}
    </li>
  );
}

function ObjectiveRow({ objective }: { objective: CatalogObjective }) {
  const { catalog, refreshCatalog } = useStudio();
  const [skill, setSkill] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const candidates = catalog.skills.filter((s) => !objective.skillIds.includes(s.id));

  async function add() {
    setBusy(true);
    setError(null);
    try {
      await studioApi.linkObjectiveSkill(objective.id, skill);
      setSkill("");
      refreshCatalog();
    } catch (err) {
      setError(errorText(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <li className="flex flex-col gap-2 py-4 first:pt-0 last:pb-0">
      <div>
        <p className="font-semibold text-ink">{objective.title}</p>
        <p className="text-xs font-semibold tracking-wide text-muted">{objective.code}</p>
        {objective.description && <p className="mt-1 text-sm text-muted">{objective.description}</p>}
      </div>
      <SkillChips skillIds={objective.skillIds} catalog={catalog} />
      {candidates.length > 0 && (
        <div className="flex flex-wrap items-center gap-2">
          <label className="sr-only" htmlFor={`obj-${objective.id}`}>
            Add a skill to {objective.title}
          </label>
          <select
            id={`obj-${objective.id}`}
            value={skill}
            onChange={(e) => setSkill(e.target.value)}
            className="rounded-2xl border-[1.5px] border-hairline bg-white px-3 py-2 text-sm"
          >
            <option value="">Add a skill…</option>
            {candidates.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </select>
          <Button variant="outline" size="md" className="px-4 py-2" disabled={!skill || busy} onClick={add}>
            Add
          </Button>
        </div>
      )}
      {error && (
        <p role="alert" className="text-sm font-medium text-brand-redStrong">
          {error}
        </p>
      )}
    </li>
  );
}

function useNamedCodeForm() {
  const [name, setName] = useState("");
  const [code, setCode] = useState("");
  const [codeTouched, setCodeTouched] = useState(false);
  const [description, setDescription] = useState("");
  return {
    name,
    code,
    description,
    setDescription,
    onName: (value: string) => {
      setName(value);
      if (!codeTouched) setCode(codeFrom(value));
    },
    onCode: (value: string) => {
      setCodeTouched(true);
      setCode(value.toUpperCase());
    },
    reset: () => {
      setName("");
      setCode("");
      setCodeTouched(false);
      setDescription("");
    },
  };
}

function NewSkill() {
  const { catalog, refreshCatalog } = useStudio();
  const form = useNamedCodeForm();
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      await studioApi.createSkill(catalog.subject.id, {
        name: form.name.trim(),
        code: form.code,
        description: form.description.trim() || undefined,
      });
      form.reset();
      refreshCatalog();
    } catch (err) {
      setError(errorText(err, "The skill couldn't be added. Its code may already be in use."));
    } finally {
      setSaving(false);
    }
  }

  return (
    <Panel title="Add a skill">
      <form onSubmit={onSubmit} className="flex flex-col gap-4">
        <FormField label="Skill name" value={form.name} maxLength={160} required onChange={(e) => form.onName(e.target.value)} />
        <FormField
          label="Code (unique on the platform)"
          value={form.code}
          maxLength={60}
          pattern="[A-Z0-9_]+"
          required
          onChange={(e) => form.onCode(e.target.value)}
        />
        <TextAreaField
          label="What a child can do when they have this skill"
          value={form.description}
          maxLength={2000}
          onChange={(e) => form.setDescription(e.target.value)}
        />
        {error && (
          <p role="alert" className="text-sm font-medium text-brand-redStrong">
            {error}
          </p>
        )}
        <Button type="submit" disabled={saving || !form.name.trim() || !form.code} className="w-fit">
          <Plus size={16} aria-hidden /> {saving ? "Adding…" : "Add skill"}
        </Button>
      </form>
    </Panel>
  );
}

function NewObjective() {
  const { catalog, refreshCatalog } = useStudio();
  const form = useNamedCodeForm();
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      await studioApi.createObjective(catalog.subject.id, {
        title: form.name.trim(),
        code: form.code,
        description: form.description.trim() || undefined,
      });
      form.reset();
      refreshCatalog();
    } catch (err) {
      setError(errorText(err, "The objective couldn't be added. Its code may already be in use."));
    } finally {
      setSaving(false);
    }
  }

  return (
    <Panel title="Add a learning objective">
      <form onSubmit={onSubmit} className="flex flex-col gap-4">
        <FormField label="Objective title" value={form.name} maxLength={200} required onChange={(e) => form.onName(e.target.value)} />
        <FormField
          label="Code (unique on the platform)"
          value={form.code}
          maxLength={60}
          pattern="[A-Z0-9_]+"
          required
          onChange={(e) => form.onCode(e.target.value)}
        />
        <TextAreaField
          label="Description (optional)"
          value={form.description}
          maxLength={2000}
          onChange={(e) => form.setDescription(e.target.value)}
        />
        {error && (
          <p role="alert" className="text-sm font-medium text-brand-redStrong">
            {error}
          </p>
        )}
        <Button type="submit" disabled={saving || !form.name.trim() || !form.code} className="w-fit">
          <Plus size={16} aria-hidden /> {saving ? "Adding…" : "Add objective"}
        </Button>
      </form>
    </Panel>
  );
}

import { useState, type FormEvent } from "react";
import { Plus } from "lucide-react";
import { studioApi, type CatalogObjective, type CatalogSkill } from "@/lib/api/studio";
import { Button } from "@/components/ui/Button";
import { FormField } from "@/features/auth/FormField";
import { ComposerPanel, NewButton, Panel, SkillChips, TextAreaField } from "./shared";
import { codeFrom, errorText, useStudio, useComposer } from "./studioLogic";

/**
 * The educational anchor: skills (what evidence is recorded against) and learning
 * objectives (the groups of skills a curriculum is built from). ADMIN-only on the
 * server; teachers never redefine official skill semantics.
 */
export function CatalogPage() {
  const { catalog } = useStudio();
  const skills = useComposer(catalog.skills.length === 0);
  const objectives = useComposer(catalog.objectives.length === 0);

  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
      <div className="flex flex-col gap-4">
        {skills.open && <NewSkill onDone={skills.hide} focusOnOpen={skills.requested} />}
        <Panel
          title={`Skills (${catalog.skills.length})`}
          action={!skills.open && <NewButton label="New skill" onClick={skills.show} />}
        >
          {catalog.skills.length === 0 ? (
            <p className="text-sm text-ink-soft">No skills yet. Skills are what children&apos;s answers count as evidence for.</p>
          ) : (
            <ul className="flex flex-col divide-y divide-line">
              {catalog.skills.map((skill) => (
                <SkillRow key={skill.id} skill={skill} />
              ))}
            </ul>
          )}
        </Panel>
      </div>
      <div className="flex flex-col gap-4">
        {objectives.open && <NewObjective onDone={objectives.hide} focusOnOpen={objectives.requested} />}
        <Panel
          title={`Learning objectives (${catalog.objectives.length})`}
          action={!objectives.open && <NewButton label="New objective" onClick={objectives.show} />}
        >
          {catalog.objectives.length === 0 ? (
            <p className="text-sm text-ink-soft">No objectives yet. A curriculum is a sequence of objectives.</p>
          ) : (
            <ul className="flex flex-col divide-y divide-line">
              {catalog.objectives.map((objective) => (
                <ObjectiveRow key={objective.id} objective={objective} />
              ))}
            </ul>
          )}
        </Panel>
      </div>
    </div>
  );
}

function SkillRow({ skill }: { skill: CatalogSkill }) {
  const { catalog, refreshCatalog } = useStudio();
  const [prerequisite, setPrerequisite] = useState("");
  const [picking, setPicking] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const candidates = catalog.skills.filter((s) => s.id !== skill.id && !skill.prerequisiteSkillIds.includes(s.id));

  async function add() {
    setBusy(true);
    setError(null);
    try {
      await studioApi.addPrerequisite(skill.id, prerequisite);
      setPrerequisite("");
      setPicking(false);
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
        <p className="font-mono text-xs text-ink-faint">{skill.code}</p>
        {skill.description && <p className="mt-1 text-sm text-ink-soft">{skill.description}</p>}
      </div>
      {skill.prerequisiteSkillIds.length > 0 && (
        <div className="flex flex-wrap items-center gap-2 text-sm text-ink-soft">
          Builds on <SkillChips skillIds={skill.prerequisiteSkillIds} catalog={catalog} />
        </div>
      )}
      {candidates.length > 0 && !picking && (
        <button
          type="button"
          onClick={() => setPicking(true)}
          className="flex w-fit items-center gap-1.5 rounded-[8px] px-1.5 py-1 text-sm font-medium text-tonle-700 transition-colors hover:bg-tonle-50"
        >
          <Plus size={14} aria-hidden /> Prerequisite
        </button>
      )}
      {candidates.length > 0 && picking && (
        <div className="flex flex-wrap items-center gap-2">
          <label className="sr-only" htmlFor={`pre-${skill.id}`}>
            Add a prerequisite for {skill.name}
          </label>
          <select
            autoFocus
            id={`pre-${skill.id}`}
            value={prerequisite}
            onChange={(e) => setPrerequisite(e.target.value)}
            className="rounded-[10px] border border-line-strong bg-white h-10 px-3 text-sm text-ink"
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
          <Button variant="ghost" size="sm" onClick={() => setPicking(false)} disabled={busy}>
            Cancel
          </Button>
        </div>
      )}
      {error && (
        <p role="alert" className="text-sm font-medium text-brick-deep">
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
        <p className="font-mono text-xs text-ink-faint">{objective.code}</p>
        {objective.description && <p className="mt-1 text-sm text-ink-soft">{objective.description}</p>}
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
            className="rounded-[10px] border border-line-strong bg-white h-10 px-3 text-sm text-ink"
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
        <p role="alert" className="text-sm font-medium text-brick-deep">
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

function NewSkill({ onDone, focusOnOpen }: { onDone: () => void; focusOnOpen: boolean }) {
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
      onDone();
    } catch (err) {
      setError(errorText(err, "The skill couldn't be added. Its code may already be in use."));
    } finally {
      setSaving(false);
    }
  }

  return (
    <ComposerPanel title="New skill" onClose={onDone} focusOnOpen={focusOnOpen}>
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
          <p role="alert" className="text-sm font-medium text-brick-deep">
            {error}
          </p>
        )}
        <Button type="submit" disabled={saving || !form.name.trim() || !form.code} className="w-fit">
          <Plus size={16} aria-hidden /> {saving ? "Adding…" : "Add skill"}
        </Button>
      </form>
    </ComposerPanel>
  );
}

function NewObjective({ onDone, focusOnOpen }: { onDone: () => void; focusOnOpen: boolean }) {
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
      onDone();
    } catch (err) {
      setError(errorText(err, "The objective couldn't be added. Its code may already be in use."));
    } finally {
      setSaving(false);
    }
  }

  return (
    <ComposerPanel title="New learning objective" onClose={onDone} focusOnOpen={focusOnOpen}>
      <form onSubmit={onSubmit} className="flex flex-col gap-4">
        <FormField
          label="Objective title"
          value={form.name}
          maxLength={200}
          required
          onChange={(e) => form.onName(e.target.value)}
        />
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
          <p role="alert" className="text-sm font-medium text-brick-deep">
            {error}
          </p>
        )}
        <Button type="submit" disabled={saving || !form.name.trim() || !form.code} className="w-fit">
          <Plus size={16} aria-hidden /> {saving ? "Adding…" : "Add objective"}
        </Button>
      </form>
    </ComposerPanel>
  );
}

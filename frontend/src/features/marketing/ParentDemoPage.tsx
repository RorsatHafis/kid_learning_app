import { Link } from "react-router-dom";
import { ArrowRight, Compass, Lightbulb } from "lucide-react";
import type { EvidenceLevel, SkillStanding } from "@/lib/api/types";
import { useDocumentTitle } from "@/lib/hooks/useDocumentTitle";
import { useMessages } from "@/i18n/catalog";
import { buttonClasses } from "@/components/ui/Button";
import { SkipLink } from "@/components/ui/SkipLink";
import { PaintedBackdrop } from "@/features/world/PaintedWorld";
import { Standing } from "@/features/learning/Standing";
import { EvidenceLine } from "@/features/learning/EvidenceLine";
import { HelpAtHome } from "@/features/parent/HelpAtHome";
import { Nav } from "./sections/Nav";
import { parentDemoMessages } from "./parentDemo.messages";
import { usePublicCopy } from "./publicCopy";

const goButton = buttonClasses("accent", "lg", "px-8 text-[1.0625rem]");
const quietButton = buttonClasses("outline", "lg", "bg-white/90");

type SkillKey = "skillRecognize" | "skillWithin5" | "skillWithin10" | "skillFluency";

/**
 * The sample's skills, each standing derived as the server's EvidenceStandard would: three answers before any judgement
 * (SUFFICIENT under ten, the saturation count), "secure" only across two days (D-17), hinted answers left out (D-16).
 */
const SKILLS: { key: SkillKey; standing: SkillStanding; evidence: EvidenceLevel }[] = [
  { key: "skillRecognize", standing: "SECURE", evidence: "SUFFICIENT" },
  { key: "skillWithin5", standing: "PROGRESSING", evidence: "SUFFICIENT" },
  { key: "skillWithin10", standing: "NEEDS_PRACTICE", evidence: "SUFFICIENT" },
  { key: "skillFluency", standing: "TOO_EARLY_TO_TELL", evidence: "PARTIAL" },
];

/**
 * F1c: what a parent sees, before registering - a fictional sample child, labelled as a sample on the page itself,
 * shown with the parent area's own pieces (standing, evidence line, help at home). Static: no request, nothing stored,
 * no real child's data anywhere near it.
 */
export function ParentDemoPage() {
  usePublicCopy();
  const m = useMessages(parentDemoMessages);
  useDocumentTitle(m.documentTitle);

  return (
    <div className="relative min-h-screen bg-paper">
      <PaintedBackdrop />
      <SkipLink />
      <div className="relative">
        <Nav />
        <main id="main" tabIndex={-1} className="mx-auto flex w-full max-w-3xl flex-col gap-6 px-4 pb-20 pt-8 sm:px-8 sm:pt-12">
          <header className="flex flex-col gap-3">
            <p className="text-sm font-bold uppercase tracking-[0.14em] text-palm-deep">{m.eyebrow}</p>
            <h1 className="font-display text-[clamp(2.1rem,1.6rem+2vw,3.2rem)] font-bold leading-[1.05] text-tonle-950">{m.title}</h1>
            <p className="max-w-2xl text-lg leading-relaxed text-ink-soft">{m.lead}</p>
          </header>

          {/* The sample, framed so it can't be mistaken for anyone's real child. */}
          <div className="flex flex-col gap-5 rounded-[28px] border-2 border-dashed border-tonle-200 bg-white/60 p-3 sm:p-5">
            <p className="flex flex-wrap items-center gap-3">
              <span className="rounded-full bg-light-soft px-3 py-1 text-xs font-bold uppercase tracking-[0.12em] text-light-deep">
                {m.sampleBadge}
              </span>
              <span className="font-display text-xl font-bold text-tonle-950">{m.childLine}</span>
            </p>

            <section aria-labelledby="demo-skills" className="overflow-hidden rounded-paper bg-white shadow-card">
              <header className="px-5 pb-3 pt-5 sm:px-7">
                <h2 id="demo-skills" className="font-display text-lg font-semibold text-tonle-950">
                  {m.skillsTitle}
                </h2>
                <p className="mt-1 text-sm text-ink-soft">{m.skillsLead}</p>
              </header>
              <ul className="divide-y divide-line border-t border-line">
                {SKILLS.map((skill) => (
                  <li key={skill.key} className="flex flex-col gap-2 px-5 py-4 sm:px-7">
                    <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2">
                      <span className="font-semibold text-ink">{m[skill.key]}</span>
                      <span className="flex items-center gap-4">
                        <Standing standing={skill.standing} />
                        <EvidenceLine level={skill.evidence} />
                      </span>
                    </div>
                    <p className="text-sm leading-relaxed text-ink-soft">{m[`${skill.key}Note`]}</p>
                  </li>
                ))}
              </ul>
            </section>

            <section aria-labelledby="demo-recent" className="flex flex-col gap-2 rounded-paper bg-white p-5 shadow-card sm:px-7">
              <h2 id="demo-recent" className="font-display text-lg font-semibold text-tonle-950">
                {m.recentTitle}
              </h2>
              <ul className="flex flex-col gap-1.5 text-sm text-ink">
                <li>{m.recent1}</li>
                <li>{m.recent2}</li>
              </ul>
            </section>

            <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
              <section aria-labelledby="demo-insight" className="flex flex-col gap-2 rounded-paper bg-white p-5 shadow-card">
                <h2 id="demo-insight" className="flex items-center gap-2 font-display text-lg font-semibold text-tonle-950">
                  <Lightbulb size={18} aria-hidden className="text-palm-deep" /> {m.insightTitle}
                </h2>
                <p className="leading-relaxed text-ink">{m.insight}</p>
                <p className="text-xs text-ink-faint">{m.insightBasis}</p>
              </section>
              <section aria-labelledby="demo-next" className="flex flex-col gap-2 rounded-paper bg-white p-5 shadow-card">
                <h2 id="demo-next" className="flex items-center gap-2 font-display text-lg font-semibold text-tonle-950">
                  <Compass size={18} aria-hidden className="text-palm-deep" /> {m.nextTitle}
                </h2>
                <p className="leading-relaxed text-ink">{m.next}</p>
              </section>
            </div>

            <HelpAtHome childName={m.childName} />
          </div>

          <section aria-labelledby="demo-cta" className="flex flex-col items-center gap-4 rounded-[28px] bg-[#FFFDF8] px-6 py-8 text-center shadow-paper ring-1 ring-tonle-950/5">
            <h2 id="demo-cta" className="font-display text-2xl font-bold text-tonle-950">
              {m.ctaTitle}
            </h2>
            <div className="flex flex-col gap-3 sm:flex-row">
              <Link to="/register" className={goButton}>
                {m.signUp} <ArrowRight size={18} aria-hidden />
              </Link>
              <Link to="/try" className={quietButton}>
                {m.tryLesson}
              </Link>
            </div>
            <Link to="/" className="inline-block py-3 text-sm font-semibold text-tonle-800 underline-offset-4 hover:underline">
              {m.notNow}
            </Link>
          </section>
        </main>
      </div>
    </div>
  );
}

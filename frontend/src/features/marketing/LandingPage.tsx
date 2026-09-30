import { useEffect, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { ArrowRight } from "lucide-react";
import { BlueHour } from "@/features/world/BlueHour";
import { PathOfBooks, khmerNumber, type PathStep } from "@/features/world/PathOfBooks";
import { LearningBuddy } from "@/features/buddy/LearningBuddy";
import { EvidenceLine } from "@/features/learning/EvidenceLine";
import { buttonClasses } from "@/components/ui/Button";
import { cn } from "@/lib/utils/cn";
import type { EvidenceLevel } from "@/lib/api/types";
import { Nav } from "./sections/Nav";
import { SkipLink } from "@/components/ui/SkipLink";
import { Footer } from "./sections/Footer";

/**
 * The public front door. It shows the product as it is — early maths today, Khmer and
 * English in preparation — using the same world, evidence language and decisions the
 * app itself uses, so nothing here promises more than a family will find inside.
 */
export function LandingPage() {
  // The front door keeps the full name and promise (other pages set "Page · Bookeyond Kid").
  useEffect(() => {
    document.title = "Bookeyond Kid — Learn Today, Build Tomorrow";
  }, []);
  return (
    <div className="overflow-x-clip bg-paper">
      <SkipLink />
      <Nav />
      <main id="main" tabIndex={-1}>
        <Hero />
        <HowItWorks />
        <ForParents />
        <ForChildren />
        <Subjects />
        <Schools />
        <Closing />
      </main>
      <Footer />
    </div>
  );
}

// An illustration of a path, not anyone's data: three books lit, the fourth glowing next.
const ILLUSTRATIVE_PATH: PathStep[] = Array.from({ length: 8 }, (_, i) => ({
  learningPathItemId: `illustration-${i}`,
  status: i < 3 ? "COMPLETED" : "PENDING",
}));

const lightButton =
  "inline-flex h-[3.25rem] items-center justify-center gap-2 rounded-[14px] bg-light px-7 text-base font-semibold text-tonle-950 shadow-glow transition-[background-color,transform] duration-150 ease-out hover:bg-light-glow active:scale-[0.98]";
const nightGhostButton =
  "inline-flex h-[3.25rem] items-center justify-center gap-2 rounded-[14px] px-6 text-base font-semibold text-white ring-1 ring-inset ring-white/30 transition-colors duration-150 hover:bg-white/10";

function Hero() {
  return (
    <section className="on-night relative bg-tonle-950 text-white">
      <div className="relative h-[56svh] min-h-[340px] lg:absolute lg:inset-0 lg:h-auto">
        <BlueHour time="dusk" crop="bottom" frame={false} className="absolute inset-0">
          <PathOfBooks steps={ILLUSTRATIVE_PATH} currentId="illustration-3" />
        </BlueHour>
        <div
          aria-hidden
          className="absolute inset-0 hidden lg:block"
          style={{ background: "radial-gradient(ellipse 60% 75% at 12% 55%, rgba(10,21,51,0.88) 0%, rgba(10,21,51,0.55) 45%, rgba(10,21,51,0) 75%)" }}
        />
        <div aria-hidden className="absolute inset-x-0 bottom-0 h-24 bg-gradient-to-t from-tonle-950 to-transparent lg:hidden" />
      </div>
      <div className="relative mx-auto flex max-w-[1240px] flex-col px-4 pb-14 sm:px-8 lg:min-h-[min(calc(100svh-4.5rem),860px)] lg:justify-center lg:py-24">
        <div className="flex max-w-[34rem] flex-col gap-6 animate-rise-in">
          <p lang="km" className="font-khmer text-xl font-medium leading-[1.6] text-tonle-200 sm:text-2xl">
            ជួយកូនរបស់អ្នករៀន។ ដឹងពីអ្វីដែលពួកគេពិតជាយល់។
          </p>
          <h1 className="font-display text-[2.5rem] font-semibold leading-[1.02] tracking-[-0.02em] sm:text-[3.5rem] lg:text-[4.25rem]" style={{ fontVariationSettings: '"opsz" 72' }}>
            Help your child learn. Know what they really understand.
          </h1>
          <p className="max-w-[30rem] text-lg leading-relaxed text-tonle-100">
            Short, friendly lessons for children aged 4 to 9 — and for you, an honest picture of the skills they are building and
            why each next step was chosen.
          </p>
          <div className="flex flex-col gap-3 sm:flex-row">
            <Link to="/register" className={lightButton}>
              Get started
            </Link>
            <a href="#how-it-works" className={nightGhostButton}>
              See how it works
            </a>
          </div>
          <p className="text-sm text-tonle-300">Early maths today · Khmer and English lessons in preparation</p>
        </div>
      </div>
    </section>
  );
}

function Section({ id, className, children }: { id?: string; className?: string; children: ReactNode }) {
  return (
    <section id={id} className={cn("scroll-mt-20 px-4 py-20 sm:px-8 sm:py-28", className)}>
      <div className="mx-auto max-w-[1240px]">{children}</div>
    </section>
  );
}

function Heading({ id, title, lead, tone = "ink" }: { id?: string; title: string; lead?: string; tone?: "ink" | "night" }) {
  return (
    <div className="flex max-w-2xl flex-col gap-4">
      <h2
        id={id}
        className={cn("font-display text-[2rem] font-semibold leading-[1.08] tracking-[-0.015em] sm:text-[2.75rem]", tone === "ink" ? "text-tonle-950" : "text-white")}
        style={{ fontVariationSettings: '"opsz" 60' }}
      >
        {title}
      </h2>
      {lead && <p className={cn("text-lg leading-relaxed", tone === "ink" ? "text-ink-soft" : "text-tonle-100")}>{lead}</p>}
    </div>
  );
}

const loop = [
  "Your child plays a short, friendly activity.",
  "Every answer is recorded as evidence — right or wrong.",
  "Evidence is tied to the foundational skill it shows.",
  "You see what the evidence says, and how strong it is.",
  "The next activity follows from your child's own answers.",
];

function HowItWorks() {
  return (
    <Section id="how-it-works">
      <Heading
        title="A finished lesson doesn't tell the whole story."
        lead="Bookeyond Kid looks at what children demonstrate while they learn, so progress becomes something you can understand — not a tally of lessons done."
      />

      <div className="mt-14 grid items-stretch gap-6 lg:grid-cols-2 lg:gap-8">
        <figure className="flex flex-col gap-3">
          <div className="flex flex-1 flex-col justify-center gap-7 rounded-[28px] bg-[#FFFBF3] px-7 py-9 shadow-lift ring-1 ring-light-soft sm:px-10">
            <p className="font-display text-[2.75rem] font-semibold leading-none text-tonle-950">4 + 3 = ?</p>
            <div className="grid grid-cols-3 gap-3" aria-hidden>
              {["6", "7", "8"].map((n) => (
                <span
                  key={n}
                  className={cn(
                    "flex h-16 items-center justify-center rounded-[18px] font-display text-2xl font-semibold",
                    n === "7" ? "border-2 border-light bg-light-soft text-tonle-950 shadow-glow" : "border-2 border-tonle-100 bg-white text-tonle-800",
                  )}
                >
                  {n}
                </span>
              ))}
            </div>
            <p className="text-base font-semibold text-light-deep">That's right!</p>
          </div>
          <figcaption className="px-2 text-sm text-ink-faint">What your child sees: one question at a time, with gentle feedback.</figcaption>
        </figure>

        <figure className="flex flex-col gap-3">
          <div className="flex flex-1 flex-col justify-center gap-6 rounded-[28px] bg-white px-7 py-9 shadow-paper ring-1 ring-line/70 sm:px-10">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <p className="font-semibold text-ink">Add within 10</p>
                <p className="tabular text-sm text-ink-soft">4 of 6 answers correct · last practised today</p>
              </div>
              <span className="inline-flex items-center gap-2 text-sm font-semibold text-tonle-700">
                <span aria-hidden className="h-3 w-3 rounded-[3px] bg-tonle-600" /> Progressing
              </span>
            </div>
            <div className="flex items-center gap-3 text-sm text-ink-soft">
              <EvidenceLine level="SUFFICIENT" width={120} /> Enough answers to judge
            </div>
            <p className="border-t border-line pt-5 text-[0.9375rem] leading-relaxed text-ink">
              <span className="font-semibold">Why this next? </span>
              Answers on Add within 10 were consistently correct, so the path moved on to a harder activity on the same skill.
            </p>
          </div>
          <figcaption className="px-2 text-sm text-ink-faint">What you see: the skill, the evidence behind it, and the reason for the next step.</figcaption>
        </figure>
      </div>

      <ol className="mt-16 grid gap-x-8 gap-y-8 border-t border-line pt-10 sm:grid-cols-2 lg:grid-cols-5">
        {loop.map((text, i) => (
          <li key={text} className="flex flex-col gap-3">
            <span className="flex items-baseline gap-2 font-display text-tonle-600">
              <span lang="km" className="font-khmer text-2xl">
                {khmerNumber(i + 1)}
              </span>
              <span className="text-sm text-ink-faint">{i + 1}</span>
            </span>
            <p className="text-[0.9375rem] leading-relaxed text-ink">{text}</p>
          </li>
        ))}
      </ol>
    </Section>
  );
}

const strengths: { level: EvidenceLevel; title: string; text: string }[] = [
  { level: "NONE", title: "Not started", text: "The skill is on the path; there are no answers yet." },
  { level: "PARTIAL", title: "Too early to tell", text: "A few answers — not enough to judge, so we don't." },
  { level: "SUFFICIENT", title: "Growing", text: "Enough answers to see how the skill is going." },
  { level: "STRONG", title: "Secure", text: "Consistently right answers across enough questions." },
];

const rules = [
  ["Several answers show a skill isn't secure yet", "an easier step, or the skill it builds on"],
  ["The evidence is mixed or still thin", "more practice on the same skill"],
  ["Answers are consistently strong", "a harder activity on the same skill"],
  ["A skill that looked secure starts slipping", "Smart Review brings it back into the path"],
];

function ForParents() {
  return (
    <Section id="for-parents" className="bg-white">
      <div className="grid gap-16 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)] lg:gap-20">
        <div className="flex flex-col gap-10">
          <Heading
            title="Evidence has a strength. We show it."
            lead="Three answers are not a verdict. Every skill shows how much evidence stands behind it, and we only describe how it is going once there is enough to say so."
          />
          <ul className="flex flex-col divide-y divide-line border-y border-line">
            {strengths.map((s) => (
              <li key={s.level} className="grid grid-cols-[132px_minmax(0,1fr)] items-center gap-5 py-5">
                <EvidenceLine level={s.level} width={120} />
                <div>
                  <p className="font-semibold text-ink">{s.title}</p>
                  <p className="text-sm text-ink-soft">{s.text}</p>
                </div>
              </li>
            ))}
          </ul>
        </div>

        <div className="flex flex-col gap-8 lg:pt-3">
          <div className="flex flex-col gap-3">
            <h3 className="font-display text-2xl font-semibold text-tonle-950">The next step should make sense.</h3>
            <p className="leading-relaxed text-ink-soft">
              What comes next is decided by clear rules from your child's own answers — never by guesswork, and never to keep
              them on a screen longer. Each decision is explained to you in plain words.
            </p>
          </div>
          <dl className="flex flex-col gap-3">
            {rules.map(([when, then]) => (
              <div key={when} className="grid gap-1 rounded-[16px] bg-paper px-5 py-4 ring-1 ring-line/70 sm:grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] sm:items-center sm:gap-4">
                <dt className="text-[0.9375rem] text-ink">{when}</dt>
                <ArrowRight size={16} className="hidden text-tonle-400 sm:block" aria-hidden />
                <dd className="text-[0.9375rem] font-semibold text-tonle-800">{then}</dd>
              </div>
            ))}
          </dl>
          <p className="text-sm leading-relaxed text-ink-faint">
            Your family's learning data belongs to your family: you can download all of it from your account at any time.
          </p>
        </div>
      </div>
    </Section>
  );
}

function ForChildren() {
  return (
    <section id="for-children" className="on-night relative scroll-mt-16 overflow-hidden bg-tonle-950 text-white">
      <div aria-hidden className="absolute inset-0">
        <BlueHour time="dusk" crop="center" frame={false} className="absolute inset-0 opacity-90" />
        <div className="absolute inset-0 bg-gradient-to-r from-tonle-950 via-tonle-950/80 to-tonle-950/10" />
      </div>
      <div className="relative mx-auto grid max-w-[1240px] items-center gap-12 px-4 py-24 sm:px-8 sm:py-32 lg:grid-cols-[minmax(0,1.1fr)_minmax(0,0.9fr)]">
        <div className="flex flex-col gap-8">
          <Heading
            tone="night"
            title="A world of their own."
            lead="A river at dusk, a path of books that light up as they learn, and a small firefly who keeps them company."
          />
          <ul className="grid gap-x-8 gap-y-4 text-[0.9375rem] text-tonle-100 sm:grid-cols-2">
            <li className="border-t border-white/15 pt-4">Short activities made for small hands, with sound they can switch off.</li>
            <li className="border-t border-white/15 pt-4">The firefly is a character we wrote — not a chatbot.</li>
            <li className="border-t border-white/15 pt-4">No points to chase, no streaks to lose, no leaderboards.</li>
            <li className="border-t border-white/15 pt-4">No ads, no chat, no strangers. Grown-ups hold a button to leave.</li>
          </ul>
        </div>
        <div aria-hidden className="flex justify-center lg:justify-end">
          <LearningBuddy mood="happy" size={220} />
        </div>
      </div>
    </section>
  );
}

const subjects = [
  { name: "Maths", km: "គណិតវិទ្យា", text: "Recognising sums, adding within 5 and within 10, then building fluency.", ready: true },
  { name: "Khmer", km: "ភាសាខ្មែរ", text: "Consonants, vowels and first words, with native-voice audio.", ready: false },
  { name: "English", km: "ភាសាអង់គ្លេស", text: "Everyday words and phrases, with audio.", ready: false },
];

function Subjects() {
  return (
    <Section id="subjects">
      <div className="grid gap-12 lg:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)] lg:gap-20">
        <Heading
          title="Starting with early maths."
          lead="Bookeyond Kid is being built for Cambodian families. Khmer lessons with native-voice audio are being prepared with care, not machine-translated — they appear here as soon as they are ready."
        />
        <ul className="flex flex-col divide-y divide-line overflow-hidden rounded-[24px] bg-white shadow-paper ring-1 ring-line/70">
          {subjects.map((s) => (
            <li key={s.name} className="grid gap-2 px-6 py-6 sm:grid-cols-[150px_minmax(0,1fr)_auto] sm:items-center sm:gap-6 sm:px-8">
              <div>
                <p className="font-display text-xl font-semibold text-tonle-950">{s.name}</p>
                <p lang="km" className="font-khmer text-sm text-ink-faint">
                  {s.km}
                </p>
              </div>
              <p className={cn("text-[0.9375rem] leading-relaxed", s.ready ? "text-ink" : "text-ink-soft")}>{s.text}</p>
              <span
                className={cn(
                  "w-fit rounded-[8px] px-2.5 py-1 text-[0.8125rem] font-semibold ring-1 ring-inset",
                  s.ready ? "bg-palm-soft text-palm-deep ring-palm/30" : "bg-paper text-ink-soft ring-line",
                )}
              >
                {s.ready ? "Available now" : "In preparation"}
              </span>
            </li>
          ))}
        </ul>
      </div>
    </Section>
  );
}

function Schools() {
  return (
    <Section id="schools" className="bg-white">
      <div className="grid gap-12 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)] lg:gap-20">
        <div className="flex flex-col gap-8">
          <Heading
            title="Schools, in early access."
            lead="The same learning platform, organised around your classes and teachers — grown for the classroom, not bolted on."
          />
          <Link to="/register" className={buttonClasses("solid", "lg", "w-fit")}>
            Register your school <ArrowRight size={18} aria-hidden />
          </Link>
        </div>
        <ol className="flex flex-col gap-0 border-l border-line">
          {[
            ["Set up your school and its classes.", true],
            ["Invite teachers by email and assign them to classes.", true],
            ["Families join a class with its code; you see their progress in what the class assigns.", true],
          ].map(([text, ready]) => (
            <li key={text as string} className="relative flex flex-col gap-1 pb-8 pl-8 last:pb-0">
              <span
                aria-hidden
                className={cn(
                  "absolute -left-[7px] top-1.5 h-3.5 w-3.5 rounded-full ring-4 ring-white",
                  ready ? "bg-tonle-700" : "border-2 border-tonle-300 bg-white",
                )}
              />
              <p className={cn("text-lg", ready ? "font-semibold text-ink" : "text-ink-soft")}>{text}</p>
              <p className="text-sm text-ink-faint">{ready ? "Available in early access" : "Not yet available"}</p>
            </li>
          ))}
        </ol>
      </div>
    </Section>
  );
}

const promises = [
  ["Learning first", "Every design decision starts with what serves the child's learning, not engagement metrics."],
  ["Evidence, not assumptions", "Progress is measured from what children actually demonstrate."],
  ["An honest window", "Parents always see clearly what and how their child is learning."],
  ["Your family's data", "Learning data belongs to the family, and we treat it with the care it deserves."],
];

function Closing() {
  return (
    <>
      <Section>
        <div className="grid gap-12 lg:grid-cols-[minmax(0,0.8fr)_minmax(0,1.2fr)] lg:gap-20">
          <Heading title="What we promise." />
          <dl className="grid gap-x-10 gap-y-8 sm:grid-cols-2">
            {promises.map(([title, text]) => (
              <div key={title} className="flex flex-col gap-2 border-t-2 border-tonle-900 pt-4">
                <dt className="font-display text-lg font-semibold text-tonle-950">{title}</dt>
                <dd className="leading-relaxed text-ink-soft">{text}</dd>
              </div>
            ))}
          </dl>
        </div>
      </Section>
      <section className="on-night relative overflow-hidden bg-tonle-950 text-white">
        <div aria-hidden className="absolute inset-0">
          <BlueHour time="dusk" crop="top" frame={false} className="absolute inset-0" />
          <div className="absolute inset-0 bg-tonle-950/55" />
        </div>
        <div className="relative mx-auto flex max-w-[1240px] flex-col items-start gap-6 px-4 py-24 sm:px-8 sm:py-28">
          <p className="font-display text-[2.25rem] font-semibold leading-[1.05] sm:text-[3.25rem]" style={{ fontVariationSettings: '"opsz" 72' }}>
            Learn Today, Build Tomorrow.
          </p>
          <p className="max-w-md text-lg text-tonle-100">Create a family account with just an email address, and add your first child.</p>
          <Link to="/register" className={lightButton}>
            Get started
          </Link>
        </div>
      </section>
    </>
  );
}

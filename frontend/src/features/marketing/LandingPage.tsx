import { useEffect, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { ArrowRight, BookOpenCheck, Check, Coffee, Download, Hand, Languages, ShieldCheck, Sprout } from "lucide-react";
import { PaintedScene } from "@/features/world/PaintedWorld";
import { khmerNumber } from "@/features/world/PathOfBooks";
import { LearningBuddy } from "@/features/buddy/LearningBuddy";
import { BUDDY_KINDS } from "@/features/buddy/kinds";
import { buddyMessages } from "@/features/buddy/buddy.messages";
import { EvidenceLine } from "@/features/learning/EvidenceLine";
import { buttonClasses } from "@/components/ui/Button";
import { cn } from "@/lib/utils/cn";
import type { EvidenceLevel } from "@/lib/api/types";
import { Nav } from "./sections/Nav";
import { SkipLink } from "@/components/ui/SkipLink";
import { Footer } from "./sections/Footer";
import { Place, type PlaceKind } from "./Places";
import { BrowserFrame, PhoneFrame } from "./Frames";
import childEntrance from "./shots/child-entrance.webp";
import childQuestion from "./shots/child-question.webp";
import parentHome from "./shots/parent-home.webp";
import parentChild from "./shots/parent-child.webp";
import teacherTrend from "./shots/teacher-trend.webp";
import childGarden from "./shots/child-garden.webp";
import { Plant } from "@/features/world/Plant";
import { messagesIn, useLocale, useMessages } from "@/i18n/catalog";
import { marketingMessages } from "./messages";
import { common } from "@/components/ui/messages";
import { trustMessages } from "./trust.messages";
import { siteApi, type PublicStatement } from "@/lib/api/site";
import { useAsync } from "@/lib/hooks/useAsync";
import { withoutClosingStop } from "./quote";
import { usePublicCopy } from "./publicCopy";

/**
 * The public front door. It shows the product as it is — early maths today, Khmer and English in
 * preparation — in the same River Garden world, evidence language and decisions the app itself
 * uses, so nothing here promises more than a family will find inside.
 */
export function LandingPage() {
  usePublicCopy();
  const m = useMessages(marketingMessages);
  // The front door keeps the full name and promise (other pages set "Page · Bookeyond Kid").
  useEffect(() => {
    document.title = m.documentTitle;
  }, [m.documentTitle]);
  return (
    <div className="overflow-x-clip bg-paper">
      <SkipLink />
      <Nav />
      <main id="main" tabIndex={-1}>
        <Hero />
        <Audiences />
        <HowItWorks />
        <ForParents />
        <ForChildren />
        <Grow />
        <Statements />
        <Safety />
        <Subjects />
        <Plans />
        <Schools />
        <Closing />
      </main>
      <Footer />
    </div>
  );
}

const goButton = buttonClasses("accent", "lg", "px-8 text-[1.0625rem]");
const quietButton = buttonClasses("outline", "lg", "bg-white/90");

/**
 * The first view: the promise in both languages, a way in for families, one for schools and a lesson to try, and the
 * product itself in the River Garden.
 *
 * M2, composed per width rather than shrunk: on a large screen the painted village fills the band behind the words and
 * the product stands beside them. Below that, the words sit on clean paper (the painting behind text left only a strip
 * of bamboo on a phone) and the village follows as a framed window on the bridge and the village, with Momo on the path
 * and the child's real home in a phone.
 */
function Hero() {
  const m = useMessages(marketingMessages);
  const t = useMessages(trustMessages);
  const l = m.landing;
  const locale = useLocale();
  const other = locale === "km" ? "en" : "km";
  const demo = useAsync(() => siteApi.demoActivities(), []);
  const canTry = (demo.data?.length ?? 0) > 0;
  return (
    <section className="relative overflow-hidden bg-paper lg:bg-[#DCEDEC]">
      <div aria-hidden className="absolute inset-0 hidden lg:block">
        <PaintedScene position="58% 62%" />
        {/* Momo and a friend on the stone path */}
        <div className="absolute bottom-[6%] left-[46%] flex items-end gap-1">
          <LearningBuddy kind="OTTER" mood="happy" size={150} />
          <LearningBuddy kind="FROG" mood="idle" size={96} className="mb-1" />
        </div>
        {/* a morning haze behind the words, so they read on the sky */}
        <div
          className="absolute inset-0"
          style={{ background: "radial-gradient(ellipse 70% 96% at 16% 50%, rgba(246,243,236,0.97) 0%, rgba(246,243,236,0.86) 50%, rgba(246,243,236,0) 82%)" }}
        />
      </div>
      <div className="relative mx-auto grid max-w-[1240px] items-center gap-10 px-4 pb-14 pt-8 sm:px-8 sm:pt-10 lg:min-h-[min(calc(100svh-4.5rem),900px)] lg:grid-cols-[minmax(0,1.05fr)_minmax(0,0.95fr)] lg:gap-12 lg:pb-24 lg:pt-16">
        <div className="flex max-w-[36rem] flex-col gap-6 motion-safe:animate-rise-in">
          {/* The promise in the other language too, as a quotation (its own quotation marks, no closing stop). */}
          <p lang={other} className={cn("text-lg font-semibold leading-[1.55] text-clay-deep sm:text-xl", other === "km" && "font-khmer")}>
            <span aria-hidden className="text-clay">{other === "km" ? "« " : "“"}</span>
            {withoutClosingStop(messagesIn(marketingMessages, other).hero.title)}
            <span aria-hidden className="text-clay">{other === "km" ? " »" : "”"}</span>
          </p>
          <h1 className="font-display text-[2.45rem] font-bold leading-[1.02] tracking-[-0.02em] text-tonle-950 sm:text-[3.6rem] lg:text-[4.5rem]">{m.hero.title}</h1>
          <p className="max-w-[31rem] text-[1.0625rem] leading-relaxed text-ink-soft sm:text-[1.1875rem]">{m.hero.lead}</p>
          <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap">
            <Link to="/register" className={goButton}>
              {l.forFamilies} <ArrowRight size={18} aria-hidden />
            </Link>
            <a href="#schools" className={quietButton}>
              {l.forSchools}
            </a>
          </div>
          {canTry && (
            <Link to="/try" className="inline-flex w-fit items-center gap-2 text-[1.0625rem] font-semibold text-tonle-800 underline decoration-light decoration-2 underline-offset-[6px] hover:text-tonle-950">
              <Hand size={18} aria-hidden /> {l.tryLesson}
            </Link>
          )}
          <ul aria-label={t.proof.label} className="flex flex-col gap-2 border-t border-tonle-950/10 pt-5 text-[0.9375rem] font-medium text-tonle-900 sm:flex-row sm:flex-wrap sm:gap-x-6">
            {[
              { icon: Sprout, text: t.proof.ages },
              { icon: BookOpenCheck, text: t.proof.evidence },
              { icon: ShieldCheck, text: t.proof.safe },
            ].map(({ icon: Icon, text }) => (
              <li key={text} className="flex items-center gap-2">
                <Icon size={18} strokeWidth={2} className="shrink-0 text-palm" aria-hidden />
                {text}
              </li>
            ))}
          </ul>
          <p className="text-sm font-medium text-ink-soft">{m.hero.status}</p>
        </div>

        {/* Below a large screen: the village as a framed window, Momo on the path, the child's home in a phone. */}
        <figure className="relative mx-auto w-full max-w-[640px] lg:hidden">
          <div aria-hidden className="relative aspect-[4/3] w-full overflow-hidden rounded-[28px] shadow-lift ring-1 ring-tonle-950/10 sm:aspect-[16/10]">
            <PaintedScene position="44% 58%" />
            <div className="absolute bottom-[5%] left-[6%] flex items-end gap-1">
              <LearningBuddy kind="OTTER" mood="happy" size={104} className="w-[clamp(64px,19vw,112px)]" />
              <LearningBuddy kind="FROG" mood="idle" size={66} className="mb-1 w-[clamp(40px,12vw,70px)]" />
            </div>
          </div>
          <PhoneFrame
            src={childEntrance}
            alt={l.shotChild}
            eager
            className="absolute -bottom-6 right-3 w-[36%] max-w-[190px] rotate-[3deg] sm:right-6"
          />
          <figcaption className="mt-3 pr-[42%] text-xs font-medium text-ink-soft sm:pr-[36%]">{l.sample}</figcaption>
        </figure>

        <div className="relative mx-auto hidden w-full max-w-[520px] lg:mx-0 lg:block lg:justify-self-end">
          <BrowserFrame
            src={parentHome}
            alt={l.shotParent}
            width={1600}
            height={1227}
            eager
            className="ml-auto w-[88%] rotate-[1.5deg] motion-safe:animate-rise-in"
          />
          <PhoneFrame
            src={childEntrance}
            alt={l.shotChild}
            eager
            className="absolute -bottom-8 left-0 w-[44%] -rotate-[4deg] motion-safe:animate-rise-in sm:-bottom-12"
          />
          <p className="mt-14 text-right text-xs font-medium text-ink-soft sm:mt-16">{l.sample}</p>
        </div>
      </div>
    </section>
  );
}

/**
 * M2: what Bookeyond says about itself, as administrators keep it (Operations → Landing page). Brand lines, or
 * testimonials with their attribution - never an invented endorsement. Shown in the page's language with the other
 * beneath; nothing is shown when there is nothing to show (or it can't be loaded).
 */
function Statements() {
  const l = useMessages(marketingMessages).landing;
  const locale = useLocale();
  const statements = useAsync(() => siteApi.statements(), []);
  const list = statements.data ?? [];
  if (list.length === 0) return null;
  const [first, ...rest] = list;
  const text = (s: PublicStatement, lang: "en" | "km") => withoutClosingStop(lang === "km" ? s.textKm : s.textEn);
  const by = (s: PublicStatement) => (locale === "km" ? s.attributionKm : s.attributionEn);
  const other = locale === "km" ? "en" : "km";
  return (
    <section aria-label={l.statementsLabel} className="bg-tonle-900 px-4 py-16 text-white sm:px-8 sm:py-20">
      <div className="mx-auto flex max-w-[1100px] flex-col gap-10">
        <figure className="flex flex-col gap-4">
          <blockquote className="font-display text-[clamp(2rem,1.5rem+2.4vw,3.4rem)] font-bold leading-[1.08] tracking-[-0.01em] text-balance">
            <span aria-hidden className="text-light">{locale === "km" ? "« " : "“"}</span>
            {text(first, locale)}
            <span aria-hidden className="text-light">{locale === "km" ? " »" : "”"}</span>
          </blockquote>
          <p lang={other} className={cn("text-lg font-semibold text-white/75", other === "km" && "font-khmer")}>
            {text(first, other)}
          </p>
          {first.kind === "TESTIMONIAL" && by(first) && <figcaption className="text-sm font-semibold text-light">{by(first)}</figcaption>}
        </figure>
        {rest.length > 0 && (
          <ul className="grid gap-6 border-t border-white/15 pt-8 sm:grid-cols-2">
            {rest.map((s) => (
              <li key={s.id}>
                <figure className="flex flex-col gap-2">
                  <blockquote className="font-display text-[1.5rem] font-bold leading-snug text-balance">{text(s, locale)}</blockquote>
                  <p lang={other} className={cn("text-[0.9375rem] text-white/70", other === "km" && "font-khmer")}>
                    {text(s, other)}
                  </p>
                  {s.kind === "TESTIMONIAL" && by(s) && <figcaption className="text-sm font-semibold text-light">{by(s)}</figcaption>}
                </figure>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}

/** Two ways in: what a family gets, and what a school gets - each with its own first step. */
function Audiences() {
  const l = useMessages(marketingMessages).landing;
  const cards = [
    {
      title: l.familiesTitle,
      lead: l.familiesLead,
      points: [l.familiesPoints.a, l.familiesPoints.b, l.familiesPoints.c],
      cta: l.familiesCta,
      to: "/register",
      buddy: "OTTER" as const,
    },
    {
      title: l.schoolsTitle,
      lead: l.schoolsLead,
      points: [l.schoolsPoints.a, l.schoolsPoints.b, l.schoolsPoints.c],
      cta: l.schoolsCta,
      to: "/register?as=principal",
      buddy: "ELEPHANT" as const,
    },
  ];
  return (
    <section aria-labelledby="audiences" className="relative px-4 pb-6 pt-16 sm:px-8 sm:pt-20">
      <h2 id="audiences" className="sr-only">
        {l.audiences}
      </h2>
      <div className="mx-auto grid max-w-[1240px] border-y border-line md:grid-cols-2 md:divide-x md:divide-line">
        {cards.map((c, i) => (
          <article key={c.title} className={cn("flex flex-col gap-5 py-10 md:py-12", i === 0 ? "md:pr-12" : "border-t border-line md:border-t-0 md:pl-12")}>
            <div className="flex items-center gap-4">
              <LearningBuddy kind={c.buddy} mood="idle" size={72} className="!animate-none shrink-0" />
              <h3 className="font-display text-[2rem] font-bold leading-tight text-tonle-950">{c.title}</h3>
            </div>
            <p className="max-w-[30rem] text-lg leading-relaxed text-ink">{c.lead}</p>
            <ul className="flex flex-col gap-2.5">
              {c.points.map((point) => (
                <li key={point} className="flex items-start gap-3 text-[0.9375rem] leading-relaxed text-ink">
                  <Check size={18} strokeWidth={2.5} className="mt-0.5 shrink-0 text-palm" aria-hidden />
                  {point}
                </li>
              ))}
            </ul>
            <Link to={c.to} className={buttonClasses(i === 0 ? "solid" : "outline", "lg", "mt-auto w-fit")}>
              {c.cta} <ArrowRight size={18} aria-hidden />
            </Link>
          </article>
        ))}
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

function Heading({ id, title, lead }: { id?: string; title: string; lead?: string }) {
  return (
    <div className="flex max-w-2xl flex-col gap-4">
      <h2 id={id} className="font-display text-[2.1rem] font-bold leading-[1.08] text-tonle-950 sm:text-[2.9rem]">
        {title}
      </h2>
      {lead && <p className="text-lg leading-relaxed text-ink-soft">{lead}</p>}
    </div>
  );
}

function HowItWorks() {
  const m = useMessages(marketingMessages).how;
  const l = useMessages(marketingMessages).landing;
  const loop = [m.loop1, m.loop2, m.loop3, m.loop4, m.loop5];
  return (
    <Section id="how-it-works">
      <Heading title={m.title} lead={m.lead} />

      <div className="mt-14 grid items-center gap-10 lg:grid-cols-[minmax(0,0.55fr)_minmax(0,1.45fr)] lg:gap-12">
        <figure className="flex flex-col items-center gap-4">
          <PhoneFrame src={childQuestion} alt={l.shotQuestion} className="w-full max-w-[280px]" />
          <figcaption className="max-w-[280px] text-center text-sm text-ink-soft">{m.childSees}</figcaption>
        </figure>
        <figure className="flex flex-col gap-4">
          <BrowserFrame src={parentChild} alt={l.shotParentChild} width={1600} height={1300} />
          <figcaption className="text-sm text-ink-soft">{m.parentSees}</figcaption>
        </figure>
      </div>

      <ol className="relative mt-16 grid gap-x-8 gap-y-8 sm:grid-cols-2 lg:grid-cols-5">
        {/* The loop is one line: each step hands on to the next. */}
        <span aria-hidden className="absolute left-[22px] right-[22px] top-[22px] hidden h-px bg-gradient-to-r from-light via-tonle-300 to-palm lg:block" />
        {loop.map((text, i) => (
          <li key={text} className="relative flex flex-col gap-4">
            <span className="flex items-center gap-3">
              <span className="relative flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-white text-clay-deep shadow-card">
                <span lang="km" className="font-khmer text-xl font-semibold leading-none">
                  {khmerNumber(i + 1)}
                </span>
              </span>
              <span className="relative bg-paper px-1.5 text-sm font-semibold text-ink-faint">{i + 1}</span>
            </span>
            <p className="text-[0.9375rem] leading-relaxed text-ink">{text}</p>
          </li>
        ))}
      </ol>
    </Section>
  );
}

const LEVELS: EvidenceLevel[] = ["NONE", "PARTIAL", "SUFFICIENT", "STRONG"];

function ForParents() {
  const m = useMessages(marketingMessages).parents;
  const strengths = LEVELS.map((level) => ({ level, ...m.strengths[level] }));
  const rules = [
    [m.rule1When, m.rule1Then],
    [m.rule2When, m.rule2Then],
    [m.rule3When, m.rule3Then],
    [m.rule4When, m.rule4Then],
  ];
  return (
    <Section id="for-parents" className="bg-white">
      <div className="grid gap-16 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)] lg:gap-20">
        <div className="flex flex-col gap-10">
          <Heading title={m.title} lead={m.lead} />
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
            <h3 className="font-display text-[1.75rem] font-bold text-tonle-950">{m.nextTitle}</h3>
            <p className="leading-relaxed text-ink-soft">{m.nextBody}</p>
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
          <p className="text-sm leading-relaxed text-ink-soft">{m.dataNote}</p>
        </div>
      </div>
    </Section>
  );
}

function ForChildren() {
  const m = useMessages(marketingMessages).children;
  const b = useMessages(buddyMessages);
  return (
    <section id="for-children" className="relative scroll-mt-16 overflow-hidden bg-[#E7F1EC]">
      <div className="relative mx-auto max-w-[1240px] px-4 pb-0 pt-20 sm:px-8 sm:pt-28">
        <div className="grid gap-12 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)] lg:items-end">
          <Heading title={m.title} lead={m.lead} />
          <ul className="grid gap-x-8 gap-y-4 text-[0.9375rem] text-ink sm:grid-cols-2">
            {[m.point1, m.point2, m.point3, m.point4].map((point) => (
              <li key={point} className="border-t border-tonle-950/15 pt-4">
                {point}
              </li>
            ))}
          </ul>
        </div>
        {/* The buddy family, standing on the field: the child chooses one. */}
        <ul className="relative mt-16 grid grid-cols-2 gap-x-4 gap-y-10 sm:grid-cols-3 lg:grid-cols-5">
          {BUDDY_KINDS.map((kind, i) => (
            <li key={kind} className="flex flex-col items-center text-center">
              <LearningBuddy kind={kind} mood={i === 0 ? "happy" : "idle"} size={150} />
              <p className="mt-1 font-display text-xl font-bold text-tonle-950">{b.names[kind]}</p>
              <p className="max-w-[14rem] text-sm leading-snug text-ink-soft">{b.characters[kind]}</p>
            </li>
          ))}
        </ul>
      </div>
      <div aria-hidden className="mt-6 h-40 overflow-hidden sm:h-56">
        <div className="relative h-full w-full">
          <PaintedScene scene="bend" position="50% 70%" />
        </div>
      </div>
    </section>
  );
}

const SUBJECTS: { key: "maths" | "khmer" | "english"; ready: boolean; place: PlaceKind }[] = [
  { key: "maths", ready: true, place: "bridge" },
  { key: "khmer", ready: false, place: "pavilion" },
  { key: "english", ready: false, place: "treehouse" },
];

function Subjects() {
  const m = useMessages(marketingMessages).subjects;
  const locale = useLocale();
  const other = locale === "km" ? "en" : "km";
  const inOther = messagesIn(marketingMessages, other).subjects;
  const subjects = SUBJECTS.map(({ key, ready, place }) => ({ key, ready, place, name: m[key], other: inOther[key], text: m[`${key}Text`] }));
  return (
    <Section id="subjects">
      <Heading title={m.title} lead={m.lead} />
      <ul className="mt-12 grid gap-6 md:grid-cols-3">
        {subjects.map((s) => (
          <li key={s.key} className={cn("flex flex-col overflow-hidden rounded-[24px] bg-white shadow-paper ring-1 ring-line/70", !s.ready && "bg-white/80")}>
            <div className={cn("flex h-44 items-end justify-center", s.ready ? "bg-[#EAF3E4]" : "bg-paper")}>
              <Place kind={s.place} className={cn("h-40 w-auto", !s.ready && "opacity-75 saturate-[0.7]")} />
            </div>
            <div className="flex flex-1 flex-col gap-2 p-6">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="font-display text-2xl font-bold text-tonle-950">{s.name}</p>
                  <p lang={other} className={cn("text-sm text-ink-soft", other === "km" && "font-khmer")}>
                    {s.other}
                  </p>
                </div>
                <span
                  className={cn(
                    "w-fit shrink-0 rounded-[8px] px-2.5 py-1 text-[0.8125rem] font-semibold ring-1 ring-inset",
                    s.ready ? "bg-palm-soft text-palm-deep ring-palm/30" : "bg-paper text-ink-soft ring-line",
                  )}
                >
                  {s.ready ? m.availableNow : m.inPreparation}
                </span>
              </div>
              <p className="text-[0.9375rem] leading-relaxed text-ink-soft">{s.text}</p>
            </div>
          </li>
        ))}
      </ul>
    </Section>
  );
}

function Schools() {
  const m = useMessages(marketingMessages).schools;
  const l = useMessages(marketingMessages).landing;
  const caps = [l.schoolCapabilities.a, l.schoolCapabilities.b, l.schoolCapabilities.c, l.schoolCapabilities.d, l.schoolCapabilities.e, l.schoolCapabilities.f];
  return (
    <section id="schools" className="scroll-mt-20 bg-tonle-900 px-4 py-20 text-white sm:px-8 sm:py-28">
      <div className="mx-auto grid max-w-[1240px] gap-14 lg:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)] lg:items-center lg:gap-16">
        <div className="flex flex-col gap-8">
          <div className="flex max-w-xl flex-col gap-4">
            <h2 className="font-display text-[2.1rem] font-bold leading-[1.08] sm:text-[2.9rem]">{m.title}</h2>
            <p className="text-lg leading-relaxed text-tonle-100">{m.lead}</p>
          </div>
          <ul className="grid gap-x-8 gap-y-4 sm:grid-cols-2">
            {caps.map((cap) => (
              <li key={cap} className="flex items-start gap-3 border-t border-white/15 pt-4 text-[0.9375rem] leading-relaxed text-tonle-50">
                <span aria-hidden className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-light" />
                {cap}
              </li>
            ))}
          </ul>
          <div className="flex flex-wrap items-center gap-4">
            <Link to="/register?as=principal" className={goButton}>
              {m.register} <ArrowRight size={18} aria-hidden />
            </Link>
            <span className="text-sm font-medium text-tonle-200">{m.earlyAccess}</span>
          </div>
        </div>
        <figure className="flex flex-col gap-4">
          <BrowserFrame src={teacherTrend} alt={l.shotTeacher} width={1400} height={1097} />
          <figcaption className="text-sm text-tonle-200">
            {l.shotTeacher} {l.sample}
          </figcaption>
        </figure>
      </div>
    </section>
  );
}

/**
 * The garden of skills (Y39), told to a parent: the one thing a child collects, grown only from their
 * answers - shown with the real component's stages and a screenshot of the product (sample data).
 */
function Grow() {
  const m = useMessages(trustMessages).grow;
  return (
    <Section id="grow">
      <div className="grid items-center gap-12 lg:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)] lg:gap-16">
        <div className="flex flex-col gap-7">
          <Heading title={m.title} lead={m.lead} />
          <div aria-hidden className="flex items-end gap-6">
            <Plant stage="sprout" size={56} />
            <Plant stage="bud" size={62} />
            <Plant stage="bloom" size={72} />
          </div>
          <ul className="flex flex-col gap-3">
            {[m.points.a, m.points.b, m.points.c].map((point) => (
              <li key={point} className="flex items-start gap-3 leading-relaxed text-ink">
                <Check size={18} strokeWidth={2.5} className="mt-0.5 shrink-0 text-palm" aria-hidden />
                {point}
              </li>
            ))}
          </ul>
        </div>
        <figure className="flex flex-col gap-3">
          <img src={childGarden} alt={m.shot} width={1472} height={652} loading="lazy" className="w-full rounded-[24px] shadow-lift" />
          <figcaption className="text-sm text-ink-soft">{m.caption}</figcaption>
        </figure>
      </div>
    </Section>
  );
}

/**
 * What a parent asks first: is it safe, and who decides how it's used. Only what the product does
 * today (Y37): each line names a real behaviour a parent will find inside.
 */
function Safety() {
  const m = useMessages(trustMessages).safety;
  const items = [
    { icon: ShieldCheck, ...m.items.nothingElse },
    { icon: Hand, ...m.items.gate },
    { icon: Coffee, ...m.items.breaks },
    { icon: Sprout, ...m.items.evidence },
    { icon: Download, ...m.items.data },
    { icon: Languages, ...m.items.language },
  ];
  return (
    <Section id="safety" className="bg-white">
      <div className="grid gap-12 lg:grid-cols-[minmax(0,0.75fr)_minmax(0,1.25fr)] lg:gap-20">
        <div className="flex flex-col gap-8">
          <Heading title={m.title} lead={m.lead} />
          <LearningBuddy kind="BUFFALO" mood="happy" size={150} className="!animate-none hidden lg:block" />
        </div>
        <ul className="grid gap-x-10 sm:grid-cols-2">
          {items.map(({ icon: Icon, title, text }) => (
            <li key={title} className="flex gap-4 border-t border-line py-6">
              <Icon size={22} strokeWidth={1.75} className="mt-1 shrink-0 text-tonle-600" aria-hidden />
              <div className="flex flex-col gap-1.5">
                <h3 className="font-display text-xl font-bold leading-snug text-tonle-950">{title}</h3>
                <p className="leading-relaxed text-ink-soft">{text}</p>
              </div>
            </li>
          ))}
        </ul>
      </div>
    </Section>
  );
}

/**
 * Plans as they really are (PRODUCT-MODEL-BRIEF.md): early access for families with nothing to pay,
 * and schools arranged with the team. No price appears until the owner sets one (PD-09).
 */
function Plans() {
  const m = useMessages(trustMessages).plans;
  const sheets = [
    { ...m.family, to: "/register", tone: "bg-white ring-1 ring-line", solid: true },
    { ...m.school, to: "/register?as=principal", tone: "bg-tonle-50 ring-1 ring-tonle-100", solid: false },
  ];
  return (
    <Section id="plans">
      <Heading title={m.title} lead={m.lead} />
      <div className="mt-12 grid gap-6 md:grid-cols-2">
        {sheets.map((s) => (
          <article key={s.name} aria-labelledby={`plan-${s.name}`} className={cn("flex flex-col gap-6 rounded-[24px] p-7 sm:p-9", s.tone)}>
            <div className="flex flex-col gap-1">
              <h3 id={`plan-${s.name}`} className="text-[0.9375rem] font-semibold text-tonle-700">
                {s.name}
              </h3>
              <p className="font-display text-[2.25rem] font-bold leading-tight text-tonle-950">{s.plan}</p>
              <p className="text-lg font-semibold text-ink">{s.price}</p>
            </div>
            <ul className="flex flex-col gap-3 border-t border-line pt-6">
              {[s.points.a, s.points.b, s.points.c].map((point) => (
                <li key={point} className="flex items-start gap-3 leading-relaxed text-ink">
                  <Check size={18} strokeWidth={2.5} className="mt-0.5 shrink-0 text-palm" aria-hidden />
                  {point}
                </li>
              ))}
            </ul>
            <p className="text-sm leading-relaxed text-ink-soft">{s.note}</p>
            <Link to={s.to} className={buttonClasses(s.solid ? "solid" : "outline", "lg", "mt-auto w-fit")}>
              {s.cta} <ArrowRight size={18} aria-hidden />
            </Link>
          </article>
        ))}
      </div>
    </Section>
  );
}

function Closing() {
  const all = useMessages(marketingMessages);
  const m = all.promises;
  const c = useMessages(common);
  return (
    <>
      <section className="relative overflow-hidden bg-[#DCEDEC]">
        <div aria-hidden className="absolute inset-0">
          <PaintedScene scene="bend" position="60% 55%" />
          <div className="absolute inset-0 bg-gradient-to-r from-[#F4F6F1]/95 via-[#F4F6F1]/70 to-transparent" />
        </div>
        <div className="relative mx-auto flex max-w-[1240px] flex-col items-start gap-6 px-4 py-24 sm:px-8 sm:py-32">
          <p className="max-w-xl font-display text-[2.4rem] font-bold leading-[1.05] text-tonle-950 sm:text-[3.4rem]">{c.tagline}</p>
          <p className="max-w-md text-lg text-ink-soft">{m.closingLead}</p>
          <Link to="/register" className={goButton}>
            {all.nav.getStarted}
          </Link>
        </div>
      </section>
    </>
  );
}

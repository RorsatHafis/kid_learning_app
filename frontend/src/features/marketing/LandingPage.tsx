import { useEffect, useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { ArrowRight, BookOpenCheck, Check, Coffee, Download, Hand, Languages, Plus, ShieldCheck, Sprout } from "lucide-react";
import { PaintedScene } from "@/features/world/PaintedWorld";
import { LearningBuddy } from "@/features/buddy/LearningBuddy";
import { BUDDY_KINDS, type BuddyKind } from "@/features/buddy/kinds";
import { buddyMessages } from "@/features/buddy/buddy.messages";
import { EvidenceLine } from "@/features/learning/EvidenceLine";
import { evidenceLabel } from "@/features/learning/explain";
import { buttonClasses } from "@/components/ui/Button";
import { cn } from "@/lib/utils/cn";
import type { EvidenceLevel } from "@/lib/api/types";
import { Nav } from "./sections/Nav";
import { SkipLink } from "@/components/ui/SkipLink";
import { Footer } from "./sections/Footer";
import { Place, type PlaceKind } from "./Places";
import { BrowserFrame, PhoneFrame } from "./Frames";
import childEntrance from "./shots/child-entrance.webp";
import parentHome from "./shots/parent-home.webp";
import teacherTrend from "./shots/teacher-trend.webp";
import childGarden from "./shots/child-garden.webp";
import { Plant } from "@/features/world/Plant";
import { messagesIn, useLocale, useMessages } from "@/i18n/catalog";
import { marketingMessages } from "./messages";
import { trustMessages } from "./trust.messages";
import { siteApi, type PublicPlan, type PublicStatement } from "@/lib/api/site";
import { entitlementLabel, priceText } from "@/lib/api/subscriptions";
import { useAsync } from "@/lib/hooks/useAsync";
import { withoutClosingStop } from "./quote";
import { usePublicCopy } from "./publicCopy";
import { LoopDemo } from "./LoopDemo";

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
        <HowItWorks />
        <ForParents />
        <ForChildren />
        <Grow />
        <Statements />
        <Safety />
        <Subjects />
        <Plans />
        <Questions />
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
      <div aria-hidden className="pointer-events-none absolute inset-x-0 bottom-0 z-[1] hidden h-28 bg-gradient-to-b from-transparent to-paper lg:block" />
      <div aria-hidden className="absolute inset-0 hidden lg:block">
        <PaintedScene position="58% 62%" />
        {/* a morning haze behind the words, so they read on the sky */}
        <div
          className="absolute inset-0"
          style={{ background: "radial-gradient(ellipse 70% 96% at 16% 50%, rgba(246,243,236,0.97) 0%, rgba(246,243,236,0.86) 50%, rgba(246,243,236,0) 82%)" }}
        />
      </div>
      <div className="relative mx-auto grid grid-cols-1 max-w-[1240px] items-center gap-10 px-4 pb-14 pt-8 sm:px-8 sm:pt-10 lg:min-h-[min(calc(100svh-4.5rem),900px)] lg:grid-cols-[minmax(0,1.05fr)_minmax(0,0.95fr)] lg:gap-12 lg:pb-32 lg:pt-[clamp(2rem,6svh,4rem)]">
        <div className="flex max-w-[36rem] flex-col gap-6 motion-safe:animate-rise-in">
          {/* The promise in the other language too, as a quotation (its own quotation marks, no closing stop). */}
          <p lang={other} className={cn("text-lg font-semibold leading-[1.55] text-clay-deep sm:text-xl", other === "km" && "font-khmer")}>
            <span aria-hidden className="text-clay">{other === "km" ? "« " : "“"}</span>
            {withoutClosingStop(messagesIn(marketingMessages, other).hero.title)}
            <span aria-hidden className="text-clay">{other === "km" ? " »" : "”"}</span>
          </p>
          <h1 className="font-display text-[2.45rem] font-bold leading-[1.02] tracking-[-0.02em] text-tonle-950 sm:text-[3.6rem] lg:text-[clamp(3rem,9.4svh,4.5rem)]">{m.hero.title}</h1>
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

        {/* Large screens only. Lazy, not eager: a hidden lazy image is never fetched, so a phone doesn't download these
            (about 90 KB); on a large screen they are in the first view and load at once. */}
        <div className="mx-auto hidden w-full max-w-[520px] lg:mx-0 lg:block lg:justify-self-end">
          <div className="relative">
          <BrowserFrame
            src={parentHome}
            alt={l.shotParent}
            width={1600}
            height={1227}
            className="ml-auto w-[90%] rotate-[1.5deg] motion-safe:animate-rise-in"
          />
          <PhoneFrame
            src={childEntrance}
            alt={l.shotChild}
            className="absolute -bottom-12 -left-[4%] w-[36%] -rotate-[4deg] motion-safe:animate-rise-in"
          />
          {/* Momo and a friend at the foot of the screens, presenting what the child and the parent see. */}
          <div aria-hidden className="absolute -bottom-16 right-[4%] flex items-end gap-1">
            <LearningBuddy kind="OTTER" mood="happy" size={118} />
            <LearningBuddy kind="FROG" mood="idle" size={76} className="mb-1" />
          </div>
          </div>
          <p className="mt-16 max-w-[38%] text-left text-xs font-medium text-ink-soft">{l.sample}</p>
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
    <section aria-label={l.statementsLabel} className="relative bg-tonle-900 px-4 py-24 text-white sm:px-8 sm:py-28">
      <RiverBank edge="top" />
      <RiverBank edge="bottom" />
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
          <ul className="grid grid-cols-1 gap-6 border-t border-white/15 pt-8 sm:grid-cols-2">
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

/**
 * The page's sections share one paper ground; a section with its own tone fades in from it and back out to it, so the
 * page reads as one place rather than blocks stacked on each other.
 */
const TONES = {
  paper: "",
  white: "bg-[linear-gradient(to_bottom,#F6F3EC_0,#FFFFFF_9rem,#FFFFFF_calc(100%-9rem),#F6F3EC_100%)]",
  mint: "bg-[linear-gradient(to_bottom,#F6F3EC_0,#E7F1EC_10rem,#E7F1EC_calc(100%-10rem),#F6F3EC_100%)]",
} as const;

function Section({ id, className, tone = "paper", children }: { id?: string; className?: string; tone?: keyof typeof TONES; children: ReactNode }) {
  return (
    <section id={id} className={cn("scroll-mt-20 px-4 py-20 sm:px-8 sm:py-28", TONES[tone], className)}>
      <div className="mx-auto max-w-[1240px]">{children}</div>
    </section>
  );
}

/** Where a dark band meets the paper ground: a soft, uneven bank, like the river's edge - not a ruled line. */
function RiverBank({ edge }: { edge: "top" | "bottom" }) {
  return (
    <svg
      aria-hidden
      viewBox="0 0 1440 48"
      preserveAspectRatio="none"
      className={cn("pointer-events-none absolute inset-x-0 h-8 w-full text-paper sm:h-12", edge === "top" ? "top-0" : "bottom-0 rotate-180")}
    >
      <path
        fill="currentColor"
        d="M0 0h1440v18c-96 14-190 22-300 16-118-6-196-26-318-24-128 2-206 30-336 28C368 36 292 14 180 12 108 11 52 18 0 26Z"
      />
    </svg>
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
  return (
    <Section id="how-it-works">
      <Heading title={m.title} lead={m.lead} />

      {/* F1d: the loop played through once, from the child's answer to the parent's reason for the next step. */}
      <LoopDemo />
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
    <Section id="for-parents" tone="white">
      <div className="grid grid-cols-1 gap-16 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)] lg:gap-20">
        <div className="flex flex-col gap-10">
          <Heading title={m.title} lead={m.lead} />
          <ul className="flex flex-col divide-y divide-line border-y border-line">
            {strengths.map((s) => (
              <li key={s.level} className="grid grid-cols-[132px_minmax(0,1fr)] items-center gap-5 py-5">
                <EvidenceLine level={s.level} width={120} />
                <div>
                  {/* The app's own words for the line, so a parent reads it the same way inside. */}
                  <p className="font-semibold text-ink">{evidenceLabel(s.level)}</p>
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
              <div key={when} className="grid grid-cols-1 gap-1 rounded-[16px] bg-paper px-5 py-4 ring-1 ring-line/70 sm:grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] sm:items-center sm:gap-4">
                <dt className="text-[0.9375rem] text-ink">{when}</dt>
                <ArrowRight size={16} className="hidden text-tonle-400 sm:block" aria-hidden />
                <dd className="text-[0.9375rem] font-semibold text-tonle-800">{then}</dd>
              </div>
            ))}
          </dl>
          <p className="text-[0.9375rem] leading-relaxed text-ink">{m.mistakes}</p>
          <p className="text-sm leading-relaxed text-ink-soft">{m.dataNote}</p>
          {/* F1c: the parent view itself, with a sample child - before any account. */}
          <Link to="/try/parent" className="inline-flex w-fit items-center gap-2 font-semibold text-tonle-800 underline decoration-light decoration-2 underline-offset-[6px] hover:text-tonle-950">
            {m.seeSample} <ArrowRight size={16} aria-hidden />
          </Link>
        </div>
      </div>
    </Section>
  );
}

function ForChildren() {
  const m = useMessages(marketingMessages).children;
  const b = useMessages(buddyMessages);
  const [chosen, setChosen] = useState<BuddyKind | null>(null);
  return (
    <section id="for-children" className={cn("relative scroll-mt-16 overflow-hidden", TONES.mint)}>
      <div className="relative mx-auto max-w-[1240px] px-4 pb-0 pt-20 sm:px-8 sm:pt-28">
        <div className="grid grid-cols-1 gap-12 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)] lg:items-end">
          <Heading title={m.title} lead={m.lead} />
          <ul className="grid grid-cols-1 gap-x-8 gap-y-4 text-[0.9375rem] text-ink sm:grid-cols-2">
            {[m.point1, m.point2, m.point3, m.point4].map((point) => (
              <li key={point} className="border-t border-tonle-950/15 pt-4">
                {point}
              </li>
            ))}
          </ul>
        </div>
        {/* F1d: the buddy family, standing on the field - tap one and it answers as it does when a child chooses it
            in their own space (the same cheer and words). Nothing is saved. */}
        <p className="mt-14 text-[0.9375rem] font-medium text-tonle-900">{m.tryChoose}</p>
        <ul className="relative mt-6 grid grid-cols-2 gap-x-4 gap-y-6 sm:grid-cols-3 lg:grid-cols-5">
          {BUDDY_KINDS.map((kind) => (
            <li key={kind}>
              <button
                type="button"
                aria-pressed={chosen === kind}
                onClick={() => setChosen(kind)}
                className={cn(
                  "flex w-full flex-col items-center rounded-[24px] px-2 pb-4 pt-2 text-center transition-colors duration-300",
                  chosen === kind ? "bg-white/80 ring-2 ring-palm" : "hover:bg-white/50",
                )}
              >
                <LearningBuddy kind={kind} mood={chosen === kind ? "cheer" : "idle"} size={150} />
                <span className="mt-1 font-display text-xl font-bold text-tonle-950">{b.names[kind]}</span>
                <span className="max-w-[14rem] text-sm leading-snug text-ink-soft">{b.characters[kind]}</span>
              </button>
            </li>
          ))}
        </ul>
        <p role="status" className="mt-4 min-h-[1.75rem] text-center font-display text-xl font-bold text-palm-deep">
          {chosen && b.chosen(b.names[chosen])}
        </p>
      </div>
      <div aria-hidden className="mt-6 h-40 overflow-hidden sm:h-56">
        <div className="relative h-full w-full">
          <PaintedScene scene="bend" position="50% 70%" />
          {/* The river runs on into the page below: the painting fades into the paper, and in from the mint above. */}
          <div className="absolute inset-x-0 top-0 h-12 bg-gradient-to-b from-[#E7F1EC] to-transparent" />
          <div className="absolute inset-x-0 bottom-0 h-20 bg-gradient-to-t from-paper to-transparent" />
        </div>
      </div>
    </section>
  );
}

/** A subject's painted place where one was drawn for it; any other subject takes the places in turn. */
const PLACES: Record<string, PlaceKind> = { MATHS: "bridge", KHMER: "pavilion", ENGLISH: "treehouse" };
const PLACE_CYCLE: PlaceKind[] = ["bridge", "pavilion", "treehouse"];
/** The copy's own short description for the first subjects, when the catalog has none. */
const COPY_TEXT: Record<string, "mathsText" | "khmerText" | "englishText"> = { MATHS: "mathsText", KHMER: "khmerText", ENGLISH: "englishText" };

function Subjects() {
  const m = useMessages(marketingMessages).subjects;
  const locale = useLocale();
  // SUB-1 / FLEX-1: as many subjects as the catalog has - available ones with their ages, the rest in preparation.
  const catalog = useAsync(() => siteApi.subjects(), [locale]);
  // At most six on the page - available ones first (the server orders them) - and how many more the app has.
  const SHOWN = 6;
  const hidden = Math.max(0, (catalog.data?.length ?? 0) - SHOWN);
  const subjects = (catalog.data ?? []).slice(0, SHOWN).map((s, i) => ({
    key: s.code,
    ready: s.available,
    place: PLACES[s.code] ?? PLACE_CYCLE[i % PLACE_CYCLE.length],
    name: s.name,
    ages: s.available && s.minAge !== null ? m.ages(s.minAge, s.maxAge) : null,
    text: s.description ?? (COPY_TEXT[s.code] ? m[COPY_TEXT[s.code]] : null),
  }));
  return (
    <Section id="subjects">
      <Heading title={m.title} lead={m.lead} />
      {catalog.error && <p className="mt-10 text-ink-soft">{m.failed}</p>}
      <ul className="mt-12 grid grid-cols-1 gap-6 md:grid-cols-3">
        {subjects.map((s) => (
          <li key={s.key} className={cn("flex flex-col overflow-hidden rounded-[24px] bg-white shadow-paper ring-1 ring-line/70", !s.ready && "bg-white/80")}>
            <div className={cn("flex h-32 items-end justify-center sm:h-44", s.ready ? "bg-[#EAF3E4]" : "bg-paper")}>
              <Place kind={s.place} className={cn("h-28 w-auto sm:h-40", !s.ready && "opacity-75 saturate-[0.7]")} />
            </div>
            <div className="flex flex-1 flex-col gap-2 p-6">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="font-display text-2xl font-bold text-tonle-950 [overflow-wrap:anywhere]">{s.name}</p>
                  {s.ages && <p className="text-sm text-ink-soft">{s.ages}</p>}
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
              {s.text && <p className="text-[0.9375rem] leading-relaxed text-ink-soft">{s.text}</p>}
            </div>
          </li>
        ))}
      </ul>
      {hidden > 0 && <p className="mt-6 text-ink-soft">{m.more(hidden)}</p>}
    </Section>
  );
}

function Schools() {
  const m = useMessages(marketingMessages).schools;
  const l = useMessages(marketingMessages).landing;
  const caps = [
    l.schoolCapabilities.a,
    l.schoolCapabilities.b,
    l.schoolCapabilities.c,
    l.schoolCapabilities.g,
    l.schoolCapabilities.d,
    l.schoolCapabilities.e,
    l.schoolCapabilities.f,
    l.schoolCapabilities.h,
  ];
  return (
    <section id="schools" className="relative scroll-mt-20 bg-tonle-900 px-4 py-24 text-white sm:px-8 sm:py-32">
      <RiverBank edge="top" />
      <RiverBank edge="bottom" />
      <div className="mx-auto grid grid-cols-1 max-w-[1240px] gap-14 lg:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)] lg:items-center lg:gap-16">
        <div className="flex flex-col gap-8">
          <div className="flex max-w-xl flex-col gap-4">
            <h2 className="font-display text-[2.1rem] font-bold leading-[1.08] sm:text-[2.9rem]">{m.title}</h2>
            <p className="text-lg leading-relaxed text-tonle-100">{m.lead}</p>
          </div>
          <ul className="grid grid-cols-1 gap-x-8 gap-y-4 sm:grid-cols-2">
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
      <div className="grid grid-cols-1 items-center gap-12 lg:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)] lg:gap-16">
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
    <Section id="safety" tone="white">
      <div className="grid grid-cols-1 gap-12 lg:grid-cols-[minmax(0,0.75fr)_minmax(0,1.25fr)] lg:gap-20">
        <div className="flex flex-col gap-8">
          <Heading title={m.title} lead={m.lead} />
          <LearningBuddy kind="BUFFALO" mood="happy" size={150} className="!animate-none hidden lg:block" />
        </div>
        <ul className="grid grid-cols-1 gap-x-10 sm:grid-cols-2">
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
  const locale = useLocale();
  // P-1: names, prices and what each plan includes come from the plan records - the words around them are copy.
  const offered = useAsync(() => siteApi.plans(), [locale]);
  const sheets = [
    { ...m.family, audience: "FAMILY", to: "/register", tone: "bg-white ring-1 ring-line", solid: true },
    { ...m.school, audience: "SCHOOL", to: "/register?as=principal", tone: "bg-tonle-50 ring-1 ring-tonle-100", solid: false },
  ] as const;
  return (
    <Section id="plans">
      <Heading title={m.title} lead={m.lead} />
      <div className="mt-12 grid grid-cols-1 gap-6 md:grid-cols-2">
        {sheets.map((s) => (
          <article key={s.name} aria-labelledby={`plan-${s.name}`} className={cn("flex flex-col gap-6 rounded-[24px] p-7 sm:p-9", s.tone)}>
            <div className="flex flex-col gap-1">
              <h3 id={`plan-${s.name}`} className="text-[0.9375rem] font-semibold text-tonle-700">
                {s.name}
              </h3>
              <PlanRecords
                plans={offered.data?.filter((p) => p.audience === s.audience).slice(0, 3) ?? null}
                failed={offered.error !== null}
                loading={offered.isLoading && !offered.data}
                fallback={{ plan: s.plan, price: s.price }}
              />
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

/**
 * One audience's plans as the records hold them: name, price (or nothing to pay), what it includes. None on offer
 * (schools arranged with the team): the copy's own words. Not loaded: no price is guessed - it says so.
 */
function PlanRecords({
  plans,
  failed,
  loading,
  fallback,
}: {
  plans: PublicPlan[] | null;
  failed: boolean;
  loading: boolean;
  fallback: { plan: string; price: string };
}) {
  const m = useMessages(trustMessages).plans;
  if (loading) return <p className="h-12 w-2/3 animate-pulse rounded-[10px] bg-tonle-950/5" aria-hidden />;
  if (failed || !plans) return <p className="text-[0.9375rem] text-ink-soft">{m.pricesFailed}</p>;
  if (plans.length === 0) {
    return (
      <>
        <p className="font-display text-[2.25rem] font-bold leading-tight text-tonle-950">{fallback.plan}</p>
        <p className="text-lg font-semibold text-ink">{fallback.price}</p>
      </>
    );
  }
  return (
    <ul className="flex flex-col gap-4">
      {plans.map((plan) => {
        const price =
          plan.amountMinor === null || !plan.currency || !plan.billingInterval
            ? fallback.price
            : priceText({ amountMinor: plan.amountMinor, currency: plan.currency, interval: plan.billingInterval });
        return (
          <li key={plan.code} className="flex flex-col gap-0.5">
            <p className="font-display text-[2rem] font-bold leading-tight text-tonle-950 [overflow-wrap:anywhere]">{plan.name}</p>
            <p className="text-lg font-semibold text-ink">{price}</p>
            {plan.limits.length > 0 && (
              <p className="text-sm text-ink-soft">
                {plan.limits.map((l) => `${entitlementLabel(l.key)}: ${l.limit === null ? m.noLimit : l.limit}`).join(" · ")}
              </p>
            )}
          </li>
        );
      })}
    </ul>
  );
}

/**
 * LAND-1: the questions administrators keep (Operations -> Landing page), in the reader's language - each opens in
 * place. Nothing shows when there are none, or they can't be read (the page loses nothing it needs).
 */
function Questions() {
  const m = useMessages(marketingMessages).faq;
  const locale = useLocale();
  const faq = useAsync(() => siteApi.faq(), []);
  if (!faq.data || faq.data.length === 0) return null;
  return (
    <Section id="questions" tone="white">
      <div className="grid grid-cols-1 gap-10 lg:grid-cols-[minmax(0,0.8fr)_minmax(0,1.2fr)] lg:gap-16">
        <Heading title={m.title} lead={m.lead} />
        <ul className="flex flex-col divide-y divide-line border-y border-line">
          {faq.data.map((item) => (
            <li key={item.id}>
              <details className="group py-5">
                <summary className="flex cursor-pointer list-none items-start justify-between gap-6 rounded-[8px] text-left font-display text-xl font-bold text-tonle-950 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-tonle-600 focus-visible:ring-offset-4 [&::-webkit-details-marker]:hidden">
                  <span className="[overflow-wrap:anywhere]">{locale === "km" ? item.questionKm : item.questionEn}</span>
                  <Plus size={22} aria-hidden className="mt-1 shrink-0 text-tonle-600 transition-transform duration-200 group-open:rotate-45" />
                </summary>
                <p className="mt-3 max-w-prose leading-relaxed text-ink-soft motion-safe:animate-rise-in">
                  {locale === "km" ? item.answerKm : item.answerEn}
                </p>
              </details>
            </li>
          ))}
        </ul>
      </div>
    </Section>
  );
}

function Closing() {
  const all = useMessages(marketingMessages);
  const m = all.promises;
  return (
    <>
      <section className="relative overflow-hidden bg-[#DCEDEC]">
        <div aria-hidden className="absolute inset-0">
          <PaintedScene scene="bend" position="60% 55%" />
          {/* The closing scene emerges from the page above rather than starting at a line. */}
          <div className="absolute inset-x-0 top-0 h-32 bg-gradient-to-b from-paper to-transparent" />
          <div className="absolute inset-0 bg-gradient-to-r from-[#F4F6F1]/95 via-[#F4F6F1]/70 to-transparent" />
        </div>
        <div className="relative mx-auto flex max-w-[1240px] flex-col items-start gap-6 px-4 py-24 sm:px-8 sm:py-32">
          <p className="max-w-xl font-display text-[2.4rem] font-bold leading-[1.05] text-tonle-950 sm:text-[3.4rem]">{m.closingTitle}</p>
          <p className="max-w-md text-lg text-ink-soft">{m.closingLead}</p>
          <Link to="/register" className={goButton}>
            {all.nav.getStarted}
          </Link>
        </div>
      </section>
    </>
  );
}

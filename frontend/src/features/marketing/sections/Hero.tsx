import { Link } from "react-router-dom";
import { Star, AudioLines, Smile, Sparkles } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Pill } from "@/components/ui/Pill";

export function Hero() {
  return (
    <section className="bg-hero px-4 pb-20 pt-16 sm:px-8 sm:pt-24">
      <div className="mx-auto grid max-w-[1280px] items-center gap-12 lg:grid-cols-2">
        <div className="flex flex-col items-start gap-8">
          <div className="flex gap-2">
            <Pill tone="green">Khmer-First</Pill>
            <Pill tone="purple">Ages 4-10</Pill>
          </div>

          <div className="flex flex-col gap-4">
            <p lang="km" className="text-3xl font-bold leading-[1.4] text-ink sm:text-[48px]">
              ជួយកូនរបស់អ្នករៀន។ ដឹងពីអ្វីដែលពួកគេពិតជាយល់។
            </p>
            <h1 className="text-3xl font-extrabold leading-[1.15] text-brand-blue sm:text-[44px]">
              Help your child learn. Know what they really understand.
            </h1>
          </div>

          <p className="max-w-[560px] text-lg leading-relaxed text-muted">
            Bookeyond Kid turns everyday learning into meaningful progress—helping children learn while giving
            parents a clearer picture of the skills they are building and what to explore next.
          </p>

          <div className="flex flex-wrap items-center gap-4">
            <Link to="/register">
              <Button size="lg">Get Started for Free</Button>
            </Link>
            <a href="#how-it-works">
              <Button variant="outline" size="lg">
                See How It Works
              </Button>
            </a>
          </div>

          <div className="flex items-center gap-3 text-sm font-semibold text-muted">
            <Star size={16} className="fill-brand-amber text-brand-amber" />
            Designed for children. Built for parents.
          </div>
        </div>

        <div className="relative mx-auto flex h-[420px] w-full max-w-[560px] items-center justify-center sm:h-[500px]">
          <div className="absolute inset-0 rounded-full bg-gradient-to-br from-brand-blueTint via-brand-purpleTint to-brand-greenTint blur-2xl" />

          <div className="absolute left-0 top-4 flex h-32 w-28 -rotate-3 flex-col items-center justify-center gap-2 rounded-3xl border-2 border-brand-blue bg-brand-blueTint p-4 text-center shadow-tight sm:h-[140px] sm:w-[120px]">
            <span lang="km" className="text-3xl font-bold text-brand-blue sm:text-4xl">
              ក
            </span>
            <span className="text-xs font-medium text-muted">Kor (Chicken)</span>
          </div>

          <div className="absolute bottom-4 right-0 flex h-32 w-28 rotate-3 flex-col items-center justify-center gap-2 rounded-3xl border-2 border-brand-green bg-brand-greenTint p-4 text-center shadow-tight sm:h-[140px] sm:w-[120px]">
            <span lang="km" className="text-3xl font-bold text-brand-green sm:text-4xl">
              ខ
            </span>
            <span className="text-xs font-medium text-muted">Khor (Egg)</span>
          </div>

          <div className="relative flex w-[85%] max-w-[420px] flex-col gap-4 rounded-3xl border-[6px] border-ink bg-white p-4 shadow-soft">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <span className="h-3 w-3 rounded-full bg-brand-blue" />
                <p className="text-sm font-bold text-ink">Khmer Alphabet Level 1</p>
              </div>
              <Pill tone="green" className="px-2.5 py-1 text-xs">
                15 XP
              </Pill>
            </div>
            <div className="flex items-center gap-4">
              <div className="flex h-[110px] w-[110px] shrink-0 items-center justify-center rounded-2xl bg-brand-amberTint">
                <span lang="km" className="text-6xl font-bold text-brand-amber">
                  ក
                </span>
              </div>
              <div className="flex flex-col gap-2">
                <p lang="km" className="text-lg font-semibold text-ink">
                  តើនេះជាអក្សរអ្វី?
                </p>
                <p className="text-sm text-muted">Which letter is this?</p>
                <div className="flex items-center gap-2 rounded-xl bg-brand-blueTint px-3 py-2 text-xs font-semibold text-brand-blue">
                  <AudioLines size={16} />
                  Listen to Pronunciation
                </div>
              </div>
            </div>
          </div>

          <div className="absolute right-8 top-0 flex h-14 w-14 items-center justify-center rounded-full bg-brand-amber shadow-soft">
            <Smile size={28} className="text-white" />
          </div>
          <Sparkles size={28} className="absolute bottom-16 left-1/2 text-brand-purple" />
        </div>
      </div>
    </section>
  );
}

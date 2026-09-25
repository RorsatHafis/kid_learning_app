import { useState } from "react";
import { Link } from "react-router-dom";
import { Check } from "lucide-react";
import { SectionHeading } from "@/components/ui/SectionHeading";
import { Pill } from "@/components/ui/Pill";
import { cn } from "@/lib/utils/cn";

const YEARLY_DISCOUNT = 0.8;

const tiers = [
  {
    name: "Free",
    price: 0,
    tagline: "Core introductory experience",
    features: ["Access to Level 1 Khmer", "Interactive math up to 5", "Basic sound guiding"],
    cta: "Start Free",
    featured: false,
  },
  {
    name: "Premium",
    price: 2.99,
    tagline: "Deeper learning and advanced reports",
    features: ["Unlimited access to all levels", "Custom adaptive progression path", "Full Parent Diagnostic Dashboard", "Detailed audio guidance tips"],
    cta: "Choose Premium",
    featured: true,
  },
  {
    name: "Family Plus",
    price: 4.99,
    tagline: "Perfect for households with multiple kids",
    features: ["Connect up to 4 child profiles", "Shared cooperative activity path", "Individual progress maps"],
    cta: "Choose Family Plus",
    featured: false,
  },
];

export function Pricing() {
  const [yearly, setYearly] = useState(false);

  return (
    <section id="pricing" className="px-4 py-20 sm:px-8 sm:py-28">
      <div className="mx-auto flex max-w-[1280px] flex-col items-center gap-12">
        <SectionHeading
          eyebrow="Simple Plans"
          khmerTitle="ជ្រើសរើសរបៀបដែលក្រុមគ្រួសាររបស់អ្នកចង់សិក្សា"
          title="Choose the way your family learns"
          description="Start free, then unlock deeper progress metrics and personalized learning paths as your child grows."
        />

        <div className="flex rounded-pill border border-hairline bg-white p-1">
          <button
            onClick={() => setYearly(false)}
            className={cn("rounded-pill px-6 py-2.5 text-sm font-semibold transition-colors", !yearly ? "bg-brand-blue text-white" : "text-muted")}
          >
            Monthly
          </button>
          <button
            onClick={() => setYearly(true)}
            className={cn("rounded-pill px-6 py-2.5 text-sm font-semibold transition-colors", yearly ? "bg-brand-blue text-white" : "text-muted")}
          >
            Yearly (Save 20%)
          </button>
        </div>

        <div className="grid w-full items-start gap-6 lg:grid-cols-3">
          {tiers.map((tier) => {
            const price = tier.price === 0 ? 0 : yearly ? tier.price * YEARLY_DISCOUNT : tier.price;
            return (
              <div
                key={tier.name}
                className={cn(
                  "flex flex-col gap-6 rounded-3xl border-[1.5px] p-8",
                  tier.featured ? "border-brand-blue bg-brand-blueTint/40 shadow-soft lg:-translate-y-4" : "border-hairline bg-white",
                )}
              >
                {tier.featured && <Pill tone="blue" className="w-fit">Recommended</Pill>}
                <div>
                  <p className="text-sm font-bold uppercase tracking-wide text-muted">{tier.name}</p>
                  <p className="mt-2 text-5xl font-extrabold text-ink">
                    {tier.price === 0 ? "$0" : `$${price.toFixed(2)}`}
                    {tier.price !== 0 && <span className="text-base font-semibold text-muted"> / month</span>}
                  </p>
                  <p className="mt-2 text-sm text-muted">{tier.tagline}</p>
                </div>
                <hr className="border-hairline" />
                <ul className="flex flex-col gap-3">
                  {tier.features.map((feature) => (
                    <li key={feature} className="flex items-start gap-2 text-sm text-ink">
                      <Check size={18} className="mt-0.5 shrink-0 text-brand-green" />
                      {feature}
                    </li>
                  ))}
                </ul>
                <Link
                  to="/register"
                  className={cn(
                    "mt-auto rounded-pill py-3 text-center text-sm font-semibold transition-colors",
                    tier.featured ? "bg-brand-blue text-white hover:bg-blue-600" : "border-[1.5px] border-ink text-ink hover:bg-ink hover:text-white",
                  )}
                >
                  {tier.cta}
                </Link>
              </div>
            );
          })}
        </div>

        <div className="text-center text-sm text-muted">
          <p className="font-semibold text-ink">Cancel anytime. No commitment.</p>
          <p>No pressure. Start with what works for your family.</p>
        </div>
      </div>
    </section>
  );
}

import { Link } from "react-router-dom";
import { GraduationCap, Check } from "lucide-react";
import { Card } from "@/components/ui/Card";

// What school accounts can do today; class learning views wait on families granting access (PD-01).
const schoolFeatures = [
  "Set up your school and its classes",
  "Invite teachers by email and assign them to classes",
  "Class learning views arrive once families can link their child to your school",
];

export function Schools() {
  return (
    <section id="schools" className="px-4 py-20 sm:px-8 sm:py-28">
      <div className="mx-auto grid max-w-[1280px] items-center gap-10 lg:grid-cols-2">
        <div className="flex flex-col gap-6">
          <div className="flex items-center gap-3">
            <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-brand-purpleTint text-brand-purple">
              <GraduationCap size={24} />
            </div>
            <p className="text-sm font-bold uppercase tracking-wide text-brand-purpleStrong">For Schools</p>
          </div>
          <h2 className="text-3xl font-extrabold text-ink sm:text-4xl">Grown for the classroom, not bolted on</h2>
          <p className="leading-relaxed text-muted">
            School accounts are in early access. Principals can set up their school, classes and teachers today.
            Seeing children's learning in class will come once families can choose to link their child to a school -
            a school never sees a family's learning without the family's say.
          </p>
          <Link
            to="/register"
            className="w-fit rounded-pill border-[1.5px] border-ink px-6 py-3 text-sm font-semibold text-ink hover:bg-ink hover:text-white"
          >
            Apply for a school account
          </Link>
        </div>

        <Card className="flex flex-col gap-6 p-8">
          <p className="text-sm font-bold uppercase tracking-wide text-muted">Early access</p>
          <p className="text-lg font-semibold text-ink">Built around your classes and teachers</p>
          <ul className="flex flex-col gap-3">
            {schoolFeatures.map((feature) => (
              <li key={feature} className="flex items-start gap-2 text-sm text-ink">
                <Check size={18} className="mt-0.5 shrink-0 text-brand-green" />
                {feature}
              </li>
            ))}
          </ul>
        </Card>
      </div>
    </section>
  );
}

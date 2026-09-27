import { Link } from "react-router-dom";
import { Button } from "@/components/ui/Button";

export function EmotionalBrand() {
  return (
    <section className="bg-ink px-4 py-24 text-center sm:px-8">
      <div className="mx-auto flex max-w-2xl flex-col items-center gap-4">
        <p lang="km" className="text-2xl font-bold text-white/90 sm:text-3xl">
          ច្រើនជាងការសិក្សា គឺអនាគតដ៏ភ្លឺស្វាង។
        </p>
        <p className="text-2xl font-extrabold text-white sm:text-3xl">More than learning, a brighter future.</p>
      </div>
    </section>
  );
}

export function FinalCta() {
  return (
    <section className="bg-hero px-4 py-24 text-center sm:px-8">
      <div className="mx-auto flex max-w-2xl flex-col items-center gap-6">
        <h2 className="text-3xl font-extrabold text-ink sm:text-4xl">Ready to see what your child really understands?</h2>
        <p className="text-lg text-muted">Create a family account with just an email address.</p>
        <Link to="/register">
          <Button size="lg">Get started</Button>
        </Link>
      </div>
    </section>
  );
}

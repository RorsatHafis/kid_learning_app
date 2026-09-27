import { Nav } from "./sections/Nav";
import { Hero } from "./sections/Hero";
import { CorePromise } from "./sections/CorePromise";
import { ChildrensWorld } from "./sections/ChildrensWorld";
import { ChildExperience } from "./sections/ChildExperience";
import { AdaptiveLearningFlow } from "./sections/AdaptiveLearningFlow";
import { ParentExperiencePreview } from "./sections/ParentExperiencePreview";
import { ParentTrust } from "./sections/ParentTrust";
import { KhmerFirst } from "./sections/KhmerFirst";
import { Subjects } from "./sections/Subjects";
import { Progression } from "./sections/Progression";
import { SmartReview } from "./sections/SmartReview";
import { LearningBuddy } from "./sections/LearningBuddy";
import { Schools } from "./sections/Schools";
import { TrustPrinciples } from "./sections/TrustPrinciples";
import { EmotionalBrand, FinalCta } from "./sections/ClosingSections";
import { Footer } from "./sections/Footer";

export function LandingPage() {
  return (
    <div className="overflow-x-hidden">
      <Nav />
      <main>
        <Hero />
        <CorePromise />
        <ChildrensWorld />
        <ChildExperience />
        <AdaptiveLearningFlow />
        <ParentExperiencePreview />
        <ParentTrust />
        <KhmerFirst />
        <Subjects />
        <Progression />
        <SmartReview />
        <LearningBuddy />
        <Schools />
        <TrustPrinciples />
        <EmotionalBrand />
        <FinalCta />
      </main>
      <Footer />
    </div>
  );
}

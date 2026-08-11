import { MarketingHero } from "@/components/marketing-hero";
import { SectionShell } from "@/components/section-shell";
import { StatusGrid } from "@/components/status-grid";

export default function HomePage() {
  return (
    <SectionShell
      eyebrow="Frontend Bootstrap"
      title="Dudecide web is now scaffolded."
      description="This first web unit establishes the application shell, design system primitives, session bootstrap path, and the initial routes required to turn the existing backend into a usable product."
    >
      <div className="space-y-5">
        <MarketingHero />
        <StatusGrid />
      </div>
    </SectionShell>
  );
}

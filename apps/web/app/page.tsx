import { MarketingHero } from "@/components/marketing-hero";
import { SectionShell } from "@/components/section-shell";
import { StatusGrid } from "@/components/status-grid";

export default function HomePage() {
  return (
    <SectionShell
      eyebrow="Meeting Operations"
      title="Stop losing decisions inside the group chat."
      description="Dudecide turns loose planning chatter into a live meeting surface with threads, votes, evidence trails, and a report the whole group can trust."
    >
      <div className="space-y-5">
        <MarketingHero />
        <StatusGrid />
      </div>
    </SectionShell>
  );
}

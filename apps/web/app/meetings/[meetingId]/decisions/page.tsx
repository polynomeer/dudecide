import { PlaceholderRoute } from "@/components/placeholder-route";
import { SectionShell } from "@/components/section-shell";

export default async function DecisionsPage({
  params,
}: {
  params: Promise<{ meetingId: string }>;
}) {
  const { meetingId } = await params;

  return (
    <SectionShell
      eyebrow="Decision Board"
      title="Decision board scaffold"
      description="This route will become the primary surface for draft decisions, evidence inspection, confirmation, and reconsideration."
    >
      <PlaceholderRoute
        meetingId={meetingId}
        title="Decision board route is ready"
        nextStep="Implement decision list, detail drawer, evidence linking, and host-only decision actions."
      />
    </SectionShell>
  );
}

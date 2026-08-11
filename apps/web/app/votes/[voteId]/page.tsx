import { PlaceholderRoute } from "@/components/placeholder-route";
import { SectionShell } from "@/components/section-shell";

export default async function VotePage({
  params,
}: {
  params: Promise<{ voteId: string }>;
}) {
  const { voteId } = await params;

  return (
    <SectionShell
      eyebrow="Vote Detail"
      title={`Vote ${voteId.slice(0, 8)}`}
      description="This route will host option rendering, participant submission, aggregate views, and host-only close actions for all supported vote types."
    >
      <PlaceholderRoute
        title="Vote detail route is ready"
        nextStep="Implement vote detail fetching, response submission forms, and aggregate result visualization."
      />
    </SectionShell>
  );
}

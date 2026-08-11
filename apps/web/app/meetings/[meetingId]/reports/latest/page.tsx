import { PlaceholderRoute } from "@/components/placeholder-route";
import { SectionShell } from "@/components/section-shell";

export default async function LatestReportPage({
  params,
}: {
  params: Promise<{ meetingId: string }>;
}) {
  const { meetingId } = await params;

  return (
    <SectionShell
      eyebrow="Meeting Report"
      title="Latest report scaffold"
      description="This route will render the latest materialized meeting report and expose refresh actions for hosts."
    >
      <PlaceholderRoute
        meetingId={meetingId}
        title="Report route is ready"
        nextStep="Bind latest report retrieval and host-side refresh actions to the report endpoints."
      />
    </SectionShell>
  );
}

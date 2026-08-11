import { MeetingOverview } from "@/components/meeting-overview";
import { SectionShell } from "@/components/section-shell";

export default async function MeetingPage({
  params,
}: {
  params: Promise<{ meetingId: string }>;
}) {
  const { meetingId } = await params;

  return (
    <SectionShell
      eyebrow="Meeting Workspace"
      title="Meeting overview"
      description="This page is the starting shell for the full workspace. It already reads the backend meeting detail endpoint and exposes thread-level navigation for the next feature units."
    >
      <MeetingOverview meetingId={meetingId} />
    </SectionShell>
  );
}

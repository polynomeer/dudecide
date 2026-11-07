import { MeetingOverview } from "@/components/meeting-overview";

export default async function MeetingPage({
  params,
}: {
  params: Promise<{ meetingId: string }>;
}) {
  const { meetingId } = await params;

  return <MeetingOverview meetingId={meetingId} />;
}

import { SpeakerClaimCard } from "@/components/import/speaker-claim-card";
import { MeetingShell } from "@/components/meeting/meeting-shell";
import { MeetingRealtimeBridge } from "@/components/meeting-realtime-bridge";
import { SessionGate } from "@/components/session-gate";

export default async function MeetingScopedLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ meetingId: string }>;
}) {
  const { meetingId } = await params;

  return (
    <SessionGate>
      <MeetingShell meetingId={meetingId}>
        <MeetingRealtimeBridge meetingId={meetingId} />
        <SpeakerClaimCard meetingId={meetingId} />
        {children}
      </MeetingShell>
    </SessionGate>
  );
}

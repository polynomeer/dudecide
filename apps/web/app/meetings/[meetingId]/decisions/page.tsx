import { MeetingPageHeader } from "@/components/meeting/meeting-page-header";
import { DecisionBoard } from "@/components/decision-board";

export default async function DecisionsPage({
  params,
}: {
  params: Promise<{ meetingId: string }>;
}) {
  const { meetingId } = await params;

  return (
    <>
      <MeetingPageHeader
        kicker="결정"
        title="결정 보드"
        description="결정마다 근거 메시지와 변경 이력이 함께 남아요."
      />
      <DecisionBoard meetingId={meetingId} />
    </>
  );
}

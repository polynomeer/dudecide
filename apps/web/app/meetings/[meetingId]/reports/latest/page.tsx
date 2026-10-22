import { MeetingPageHeader } from "@/components/meeting/meeting-page-header";
import { ReportWorkspace } from "@/components/report-workspace";

export default async function LatestReportPage({
  params,
}: {
  params: Promise<{ meetingId: string }>;
}) {
  const { meetingId } = await params;

  return (
    <>
      <MeetingPageHeader
        kicker="리포트"
        title="지금 여기까지 정했어요"
        description="확정된 것, 남은 것, 할 일을 한 장으로 공유해요."
      />
      <ReportWorkspace meetingId={meetingId} />
    </>
  );
}

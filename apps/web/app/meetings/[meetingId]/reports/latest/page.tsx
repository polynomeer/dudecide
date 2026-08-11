import { ReportWorkspace } from "@/components/report-workspace";
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
      title="Latest report"
      description="최신 materialized 회의 리포트를 확인하고 필요 시 수동 새로고침을 실행할 수 있습니다."
    >
      <ReportWorkspace meetingId={meetingId} />
    </SectionShell>
  );
}

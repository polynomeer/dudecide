import { ReportWorkspace } from "@/components/report-workspace";

export default async function LatestReportPage({
  params,
}: {
  params: Promise<{ meetingId: string }>;
}) {
  const { meetingId } = await params;

  return <ReportWorkspace meetingId={meetingId} />;
}

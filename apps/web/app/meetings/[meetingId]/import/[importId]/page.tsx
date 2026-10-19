import { SectionShell } from "@/components/section-shell";
import { ImportReview } from "@/components/import/import-review";

export default async function MeetingImportReviewPage({
  params,
}: {
  params: Promise<{ meetingId: string; importId: string }>;
}) {
  const { meetingId, importId } = await params;

  return (
    <SectionShell
      eyebrow="Import"
      title="대화 분석 결과"
      description="결정 후보와 할 일을 확인하고 보드에 반영할 항목을 고르세요. 모든 항목은 원래 메시지를 근거로 보여 줍니다."
      breadcrumbs={[
        { label: "Home", href: "/" },
        { label: "Meeting", href: `/meetings/${meetingId}` },
        { label: "Import", href: `/meetings/${meetingId}/import` },
        { label: "Result" },
      ]}
    >
      <ImportReview meetingId={meetingId} importId={importId} />
    </SectionShell>
  );
}

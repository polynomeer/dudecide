import { DecisionBoard } from "@/components/decision-board";
import { SectionShell } from "@/components/section-shell";

export default async function DecisionsPage({
  params,
}: {
  params: Promise<{ meetingId: string }>;
}) {
  const { meetingId } = await params;

  return (
    <SectionShell
      eyebrow="Decision Board"
      title="Decision board"
      description="회의별 결정 목록과 선택된 결정의 상세, evidence, 상태 이력을 확인하고, 새 결정을 생성하거나 확정·재검토 상태 전이를 실행할 수 있습니다."
    >
      <DecisionBoard meetingId={meetingId} />
    </SectionShell>
  );
}

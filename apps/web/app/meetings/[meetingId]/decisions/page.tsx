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
      description="회의별 결정 목록과 선택된 결정의 상세, evidence, 상태 이력을 확인할 수 있습니다. 생성과 확정 액션은 다음 단위에서 이어서 구현하겠습니다."
    >
      <DecisionBoard meetingId={meetingId} />
    </SectionShell>
  );
}

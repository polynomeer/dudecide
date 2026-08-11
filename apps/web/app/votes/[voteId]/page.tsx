import { SectionShell } from "@/components/section-shell";
import { VoteWorkspace } from "@/components/vote-workspace";

export default async function VotePage({
  params,
}: {
  params: Promise<{ voteId: string }>;
}) {
  const { voteId } = await params;

  return (
    <SectionShell
      eyebrow="Vote Detail"
      title={`Vote ${voteId.slice(0, 8)}`}
      description="투표 상세, 내 응답 제출, 집계 결과 확인, 호스트 마감 액션을 지원합니다."
    >
      <VoteWorkspace voteId={voteId} />
    </SectionShell>
  );
}

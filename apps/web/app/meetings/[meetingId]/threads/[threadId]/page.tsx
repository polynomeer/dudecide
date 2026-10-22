import { SectionShell } from "@/components/section-shell";
import { ThreadWorkspace } from "@/components/thread-workspace";

export default async function ThreadPage({
  params,
}: {
  params: Promise<{ meetingId: string; threadId: string }>;
}) {
  const { meetingId, threadId } = await params;

  return (
    <SectionShell
      eyebrow="Thread Workspace"
      title={`Thread ${threadId.slice(0, 8)}`}
      description="이 화면은 실제 백엔드 메시지 조회와 등록에 연결되어 있습니다. 현재는 스레드 대화 흐름과 기본 pagination을 우선 구현한 상태입니다."
      breadcrumbs={[
        { label: "Home", href: "/" },
        { label: "Meeting", href: `/meetings/${meetingId}` },
        { label: "Thread" },
      ]}
    >
      <ThreadWorkspace meetingId={meetingId} threadId={threadId} />
    </SectionShell>
  );
}

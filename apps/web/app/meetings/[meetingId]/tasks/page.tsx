import { MeetingPageHeader } from "@/components/meeting/meeting-page-header";
import { TasksWorkspace } from "@/components/tasks-workspace";

export default async function TasksPage({
  params,
}: {
  params: Promise<{ meetingId: string }>;
}) {
  const { meetingId } = await params;

  return (
    <>
      <MeetingPageHeader
        kicker="할 일"
        title="누가 뭘 하나"
        description="결정 뒤에 남은 할 일을 나누고 상태를 바꿔요."
      />
      <TasksWorkspace meetingId={meetingId} />
    </>
  );
}

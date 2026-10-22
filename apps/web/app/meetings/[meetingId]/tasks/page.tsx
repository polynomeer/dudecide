import { SectionShell } from "@/components/section-shell";
import { TasksWorkspace } from "@/components/tasks-workspace";

export default async function TasksPage({
  params,
}: {
  params: Promise<{ meetingId: string }>;
}) {
  const { meetingId } = await params;

  return (
    <SectionShell
      eyebrow="Tasks"
      title="Task management"
      description="회의 후속 액션을 생성하고 상태를 업데이트할 수 있습니다."
      breadcrumbs={[
        { label: "Home", href: "/" },
        { label: "Meeting", href: `/meetings/${meetingId}` },
        { label: "Tasks" },
      ]}
    >
      <TasksWorkspace meetingId={meetingId} />
    </SectionShell>
  );
}

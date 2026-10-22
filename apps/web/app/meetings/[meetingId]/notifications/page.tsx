import { NotificationsWorkspace } from "@/components/notifications-workspace";
import { SectionShell } from "@/components/section-shell";

export default async function NotificationsPage({
  params,
}: {
  params: Promise<{ meetingId: string }>;
}) {
  const { meetingId } = await params;

  return (
    <SectionShell
      eyebrow="Notifications"
      title="Notification feed"
      description="현재 회의 사용자 기준의 저장형 알림을 확인할 수 있습니다."
      breadcrumbs={[
        { label: "Home", href: "/" },
        { label: "Meeting", href: `/meetings/${meetingId}` },
        { label: "Notifications" },
      ]}
    >
      <NotificationsWorkspace meetingId={meetingId} />
    </SectionShell>
  );
}

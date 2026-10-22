import { MeetingPageHeader } from "@/components/meeting/meeting-page-header";
import { NotificationsWorkspace } from "@/components/notifications-workspace";

export default async function NotificationsPage({
  params,
}: {
  params: Promise<{ meetingId: string }>;
}) {
  const { meetingId } = await params;

  return (
    <>
      <MeetingPageHeader
        kicker="알림"
        title="알림"
        description="이 모임에서 나에게 온 알림이에요."
      />
      <NotificationsWorkspace meetingId={meetingId} />
    </>
  );
}

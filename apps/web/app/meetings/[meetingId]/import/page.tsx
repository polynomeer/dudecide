import { MeetingPageHeader } from "@/components/meeting/meeting-page-header";
import { ImportWizard } from "@/components/import/import-wizard";

export default async function MeetingImportPage({
  params,
}: {
  params: Promise<{ meetingId: string }>;
}) {
  const { meetingId } = await params;

  return (
    <>
      <MeetingPageHeader
        kicker="가져오기"
        title="대화 가져오기"
        description="같은 채팅방은 지난번 이후의 대화만 기본으로 고릅니다. 이미 가져온 메시지는 자동으로 건너뜁니다."
      />
      <ImportWizard meetingId={meetingId} />
    </>
  );
}

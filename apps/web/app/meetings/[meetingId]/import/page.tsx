import { ImportWizard } from "@/components/import/import-wizard";

export default async function MeetingImportPage({
  params,
}: {
  params: Promise<{ meetingId: string }>;
}) {
  const { meetingId } = await params;

  return <ImportWizard meetingId={meetingId} />;
}

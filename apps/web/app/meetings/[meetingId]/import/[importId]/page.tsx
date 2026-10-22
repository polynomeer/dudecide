import { ImportReview } from "@/components/import/import-review";

export default async function MeetingImportReviewPage({
  params,
}: {
  params: Promise<{ meetingId: string; importId: string }>;
}) {
  const { meetingId, importId } = await params;

  return <ImportReview meetingId={meetingId} importId={importId} />;
}

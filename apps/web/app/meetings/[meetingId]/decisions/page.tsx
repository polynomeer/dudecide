import { DecisionBoard } from "@/components/decision-board";

export default async function DecisionsPage({
  params,
}: {
  params: Promise<{ meetingId: string }>;
}) {
  const { meetingId } = await params;

  return <DecisionBoard meetingId={meetingId} />;
}

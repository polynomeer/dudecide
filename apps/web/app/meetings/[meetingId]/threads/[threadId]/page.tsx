import { ThreadWorkspace } from "@/components/thread-workspace";

export default async function ThreadPage({
  params,
}: {
  params: Promise<{ meetingId: string; threadId: string }>;
}) {
  const { meetingId, threadId } = await params;

  return (
    <ThreadWorkspace meetingId={meetingId} threadId={threadId} />
  );
}

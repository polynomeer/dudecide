import { PlaceholderRoute } from "@/components/placeholder-route";
import { SectionShell } from "@/components/section-shell";

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
      description="This route will host paginated message history, composer actions, edit flows, and realtime updates for one planning thread."
    >
      <PlaceholderRoute
        meetingId={meetingId}
        title="Thread detail is scaffolded"
        nextStep="Bind thread message listing, posting, editing, and SSE refresh to the backend thread APIs."
      />
    </SectionShell>
  );
}

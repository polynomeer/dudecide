import { SessionGate } from "@/components/session-gate";
import { StandaloneShell } from "@/components/standalone-shell";
import { VoteWorkspace } from "@/components/vote-workspace";

export default async function VotePage({
  params,
}: {
  params: Promise<{ voteId: string }>;
}) {
  const { voteId } = await params;

  return (
    <StandaloneShell width="max-w-[1100px]">
      <SessionGate>
        <VoteWorkspace voteId={voteId} />
      </SessionGate>
    </StandaloneShell>
  );
}

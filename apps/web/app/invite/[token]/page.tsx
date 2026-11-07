import { JoinInviteForm } from "@/components/forms/join-invite-form";
import { InvitePreview } from "@/components/import/invite-preview";
import { StandaloneShell } from "@/components/standalone-shell";

export default async function InvitePage({
  params,
}: {
  params: Promise<{ token: string }>;
}) {
  const { token } = await params;

  return (
    <StandaloneShell>
      <InvitePreview token={token} />
      <JoinInviteForm token={token} />
    </StandaloneShell>
  );
}

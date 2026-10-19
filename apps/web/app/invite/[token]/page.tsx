import { JoinInviteForm } from "@/components/forms/join-invite-form";
import { InvitePreview } from "@/components/import/invite-preview";
import { SectionShell } from "@/components/section-shell";

export default async function InvitePage({
  params,
}: {
  params: Promise<{ token: string }>;
}) {
  const { token } = await params;

  return (
    <SectionShell
      eyebrow="Invite Entry"
      title="Join an existing planning room"
      description="Invite joins create a meeting membership and bind the joined actor into the current session. After this step, the user is redirected into the meeting workspace."
    >
      <InvitePreview token={token} />
      <JoinInviteForm token={token} />
    </SectionShell>
  );
}

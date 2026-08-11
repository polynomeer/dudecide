import { CreateGuestSessionForm } from "@/components/forms/create-guest-session-form";
import { SectionShell } from "@/components/section-shell";

export default function StartPage() {
  return (
    <SectionShell
      eyebrow="Session Entry"
      title="Start a local guest session"
      description="Use the current backend guest-session endpoint to establish a browser session before creating or joining a meeting. This is the MVP entry flow until production-grade signed sessions are added."
    >
      <CreateGuestSessionForm />
    </SectionShell>
  );
}

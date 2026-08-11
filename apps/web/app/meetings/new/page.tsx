import { CreateMeetingForm } from "@/components/forms/create-meeting-form";
import { SectionShell } from "@/components/section-shell";

export default function CreateMeetingPage() {
  return (
    <SectionShell
      eyebrow="Meeting Setup"
      title="Create the planning room"
      description="This route is wired to the existing backend create-meeting API and establishes the starting point for invite generation, threaded discussion, voting, and decision confirmation."
    >
      <CreateMeetingForm />
    </SectionShell>
  );
}

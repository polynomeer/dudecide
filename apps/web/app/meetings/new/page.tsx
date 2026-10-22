import { CreateMeetingForm } from "@/components/forms/create-meeting-form";
import { StandaloneShell } from "@/components/standalone-shell";

export default function CreateMeetingPage() {
  return (
    <StandaloneShell width="max-w-[720px]">
      <span className="num text-xs font-semibold text-muted">새 모임</span>
      <h1 className="font-display m-0 text-[clamp(1.8rem,4vw,2.4rem)]">어떤 모임이에요?</h1>
      <CreateMeetingForm />
    </StandaloneShell>
  );
}

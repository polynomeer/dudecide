import { CreateGuestSessionForm } from "@/components/forms/create-guest-session-form";
import { StandaloneShell } from "@/components/standalone-shell";

export default function StartPage() {
  return (
    <StandaloneShell width="max-w-[720px]">
      <span className="num text-xs font-semibold text-muted">시작하기</span>
      <h1 className="font-display m-0 text-[clamp(1.8rem,4vw,2.4rem)]">어떻게 불러 드릴까요?</h1>
      <CreateGuestSessionForm />
    </StandaloneShell>
  );
}

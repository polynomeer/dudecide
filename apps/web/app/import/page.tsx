import { ImportWizard } from "@/components/import/import-wizard";
import { SessionGate } from "@/components/session-gate";
import { StandaloneShell } from "@/components/standalone-shell";

export default function ImportPage() {
  return (
    <StandaloneShell width="max-w-[1280px]">
      <SessionGate>
        <ImportWizard />
      </SessionGate>
    </StandaloneShell>
  );
}

import { ImportWizard } from "@/components/import/import-wizard";
import { StandaloneShell } from "@/components/standalone-shell";

export default function ImportPage() {
  return (
    <StandaloneShell width="max-w-[1280px]">
      <ImportWizard />
    </StandaloneShell>
  );
}

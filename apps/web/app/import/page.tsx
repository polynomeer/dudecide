import { SectionShell } from "@/components/section-shell";
import { ImportWizard } from "@/components/import/import-wizard";

export default function ImportPage() {
  return (
    <SectionShell
      eyebrow="Import"
      title="카카오톡 대화 가져오기"
      description="이미 나눈 대화를 올리면 결정된 것, 아직 남은 것, 누가 할 일인지를 근거와 함께 정리합니다. 가입 없이 바로 시작할 수 있습니다."
      breadcrumbs={[{ label: "Home", href: "/" }, { label: "Import" }]}
    >
      <ImportWizard />
    </SectionShell>
  );
}

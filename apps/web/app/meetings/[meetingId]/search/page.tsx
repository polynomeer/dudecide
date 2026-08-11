import { SearchWorkspace } from "@/components/search-workspace";
import { SectionShell } from "@/components/section-shell";

export default async function SearchPage({
  params,
}: {
  params: Promise<{ meetingId: string }>;
}) {
  const { meetingId } = await params;

  return (
    <SectionShell
      eyebrow="Search"
      title="Keyword search"
      description="회의 범위의 메시지, 의사결정, 태스크를 하나의 키워드 검색 흐름에서 확인할 수 있습니다."
    >
      <SearchWorkspace meetingId={meetingId} />
    </SectionShell>
  );
}

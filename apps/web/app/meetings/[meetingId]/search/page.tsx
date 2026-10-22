import { MeetingPageHeader } from "@/components/meeting/meeting-page-header";
import { SearchWorkspace } from "@/components/search-workspace";

export default async function SearchPage({
  params,
  searchParams,
}: {
  params: Promise<{ meetingId: string }>;
  searchParams: Promise<{ q?: string }>;
}) {
  const { meetingId } = await params;
  const { q } = await searchParams;

  return (
    <>
      <MeetingPageHeader kicker="검색" title="모임에서 검색" description="메시지, 결정, 할 일을 한 번에 찾아요." />
      <SearchWorkspace meetingId={meetingId} initialQuery={q ?? ""} />
    </>
  );
}

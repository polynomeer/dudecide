import { PlaceholderRoute } from "@/components/placeholder-route";
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
      title="Keyword search scaffold"
      description="The backend currently exposes keyword search only. This route will present messages, decisions, and tasks with a meeting-scoped query workflow."
    >
      <PlaceholderRoute
        meetingId={meetingId}
        title="Search route is ready"
        nextStep="Add query form, result sections, and empty/error states against the meeting search endpoint."
      />
    </SectionShell>
  );
}

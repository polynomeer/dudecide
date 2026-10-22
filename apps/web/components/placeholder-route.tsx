import Link from "next/link";

export function PlaceholderRoute({
  title,
  meetingId,
  nextStep,
}: {
  title: string;
  meetingId?: string;
  nextStep: string;
}) {
  return (
    <div className="panel max-w-4xl px-6 py-6">
      <div className="info-chip">Scaffolded route</div>
      <h2 className="mt-4 font-[var(--font-display)] text-4xl font-semibold tracking-[-0.05em]">
        {title}
      </h2>
      <p className="mt-3 max-w-2xl text-base leading-7 text-[color:rgba(13,21,38,0.72)]">
        This route exists so the final information architecture is already stable. The next focused
        feature unit will turn this placeholder into a live product surface.
      </p>
      <div className="soft-card mt-5 text-sm leading-6 text-[color:rgba(13,21,38,0.7)]">
        Next implementation step: {nextStep}
      </div>
      {meetingId ? (
        <div className="mt-5">
          <Link href={`/meetings/${meetingId}`} className="action-secondary">
            Back to meeting overview
          </Link>
        </div>
      ) : null}
    </div>
  );
}

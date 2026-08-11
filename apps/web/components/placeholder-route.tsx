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
      <div className="eyebrow">Scaffolded Route</div>
      <h2 className="mt-2 font-[var(--font-display)] text-3xl font-semibold tracking-[-0.05em]">
        {title}
      </h2>
      <p className="mt-3 max-w-2xl text-base leading-7 text-ink/72">
        This route exists so the final information architecture is already stable. The next focused
        feature unit will turn this placeholder into a live product surface.
      </p>
      <div className="mt-5 rounded-[1.5rem] border border-[color:var(--color-line)] bg-white/72 p-4 text-sm leading-6 text-ink/70">
        Next implementation step: {nextStep}
      </div>
      {meetingId ? (
        <div className="mt-5">
          <Link
            href={`/meetings/${meetingId}`}
            className="rounded-full border border-ink px-4 py-2 text-sm font-semibold transition hover:bg-white/80"
          >
            Back to meeting overview
          </Link>
        </div>
      ) : null}
    </div>
  );
}

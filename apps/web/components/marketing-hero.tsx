import Link from "next/link";

export function MarketingHero() {
  return (
    <section className="panel grid gap-8 overflow-hidden px-6 py-8 sm:px-8 lg:grid-cols-[1.1fr_0.9fr] lg:px-10 lg:py-10">
      <div className="space-y-6">
        <div className="eyebrow">Decision Workspace</div>
        <div className="max-w-3xl space-y-4">
          <h1 className="font-[var(--font-display)] text-4xl font-semibold leading-none tracking-[-0.06em] sm:text-5xl lg:text-6xl">
            Group travel planning that surfaces the signal before the chat noise.
          </h1>
          <p className="max-w-2xl text-base leading-7 text-ink/74 sm:text-lg">
            Dudecide turns date votes, location packages, evidence links, and reports into one
            planning workspace. The backend exists. This web app scaffold is the first step toward
            the full participant-facing product.
          </p>
        </div>
        <div className="flex flex-wrap gap-3">
          <Link
            href="/start"
            className="rounded-full bg-ink px-5 py-3 text-sm font-semibold text-mist transition hover:bg-pine"
          >
            Start guest session
          </Link>
          <Link
            href="/meetings/new"
            className="rounded-full border border-ink px-5 py-3 text-sm font-semibold transition hover:bg-white/80"
          >
            Draft a meeting
          </Link>
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-1">
        <div className="rounded-[1.75rem] bg-ink p-5 text-mist">
          <div className="eyebrow text-sky">Built first</div>
          <h2 className="mt-2 font-[var(--font-display)] text-2xl font-semibold tracking-[-0.04em]">
            Domain rules
          </h2>
          <p className="mt-3 text-sm leading-6 text-mist/78">
            Decision states, vote scoring, evidence links, idempotency, session scaffolding, and
            meeting-role access control are already in the Kotlin backend.
          </p>
        </div>
        <div className="rounded-[1.75rem] border border-[color:var(--color-line)] bg-white/70 p-5">
          <div className="eyebrow text-pine">Scaffolded now</div>
          <h2 className="mt-2 font-[var(--font-display)] text-2xl font-semibold tracking-[-0.04em]">
            Web entry
          </h2>
          <p className="mt-3 text-sm leading-6 text-ink/72">
            App Router, Tailwind theme, React Query provider, session bootstrap hook, and initial
            landing / start / invite / create-meeting routes are now ready for real feature work.
          </p>
        </div>
      </div>
    </section>
  );
}

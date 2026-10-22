import Image from "next/image";
import Link from "next/link";

export function MarketingHero() {
  return (
    <section className="panel overflow-hidden px-6 py-6 sm:px-8 lg:px-10 lg:py-9">
      <div className="grid gap-8 lg:grid-cols-[0.78fr_1.22fr] lg:items-end">
        <div className="space-y-7">
          <div className="info-chip">Threads, votes, decisions, reports</div>
          <div className="max-w-xl space-y-4">
            <h2 className="font-display text-[3.2rem] leading-[0.92] sm:text-[4.2rem]">
              Plan the trip.
              <br />
              Keep the rationale.
            </h2>
            <p className="max-w-md text-base leading-7 text-ink/72 sm:text-lg">
              One workspace for availability, options, blockers, and the evidence behind every final call.
            </p>
          </div>
          <div className="flex flex-wrap gap-3">
            <Link href="/start" className="action-primary">
              Start guest session
            </Link>
            <Link href="/meetings/new" className="action-secondary">
              Create a meeting
            </Link>
          </div>
          <div className="grid gap-3 sm:grid-cols-3">
            <SignalCard value="09m" label="to align on a date" />
            <SignalCard value="03" label="location packages max" />
            <SignalCard value="100%" label="decision trail captured" />
          </div>
        </div>

        <div className="grid gap-4">
          <div className="relative overflow-hidden rounded-[2rem] border border-line bg-surface p-2 shadow-[0_24px_90px_rgba(19,29,53,0.08)]">
            <Image
              src="/hero-editorial.png"
              alt="Editorial preview of layered meeting planning artifacts"
              width={1600}
              height={1000}
              className="h-auto w-full rounded-[1.55rem] object-cover"
              priority
            />
            <div className="pointer-events-none absolute inset-x-8 bottom-8 hidden rounded-[1.6rem] border border-white/60 bg-[rgba(9,19,39,0.82)] p-5 text-white shadow-[0_18px_50px_rgba(9,19,39,0.34)] md:block">
              <div className="eyebrow !text-white/56">Live room snapshot</div>
              <div className="mt-2 grid gap-3 lg:grid-cols-[1fr_auto] lg:items-end">
                <div>
                  <div className="font-display text-3xl">
                    Autumn cabin shortlist
                  </div>
                  <p className="mt-2 max-w-md text-sm leading-6 text-white/72">
                    Three candidate packages, one open date matrix, and the reasons behind each tradeoff.
                  </p>
                </div>
                <div className="info-chip border-white/14 bg-surface !text-white/76">
                  17 messages summarized
                </div>
              </div>
            </div>
          </div>

          <div className="grid gap-4 lg:grid-cols-[0.94fr_1.06fr]">
            <div className="panel-strong p-5">
              <div className="eyebrow">Why it feels different</div>
              <p className="mt-3 text-sm leading-6 text-ink/72">
                Casual chat stays casual. Dudecide extracts the parts that actually decide the trip.
              </p>
            </div>
            <div className="rounded-[2rem] border border-line bg-[linear-gradient(135deg,rgba(36,80,255,0.92),rgba(13,21,38,0.94))] p-5 text-white shadow-[0_20px_60px_rgba(36,80,255,0.22)]">
              <div className="eyebrow !text-white/56">Decision signal</div>
              <div className="mt-3 grid gap-3 sm:grid-cols-2">
                <div>
                  <div className="text-3xl font-semibold tracking-[-0.05em]">Available</div>
                  <div className="mt-1 text-sm text-white/70">8 of 10 responded for Sep 14</div>
                </div>
                <div>
                  <div className="text-3xl font-semibold tracking-[-0.05em]">Low friction</div>
                  <div className="mt-1 text-sm text-white/70">Package B has the least resistance</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}

function SignalCard({ value, label }: { value: string; label: string }) {
  return (
    <div className="metric-card">
      <div className="font-display text-3xl">{value}</div>
      <div className="mt-1 text-sm text-ink/66">{label}</div>
    </div>
  );
}

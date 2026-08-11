"use client";

import { useCurrentActor } from "@/lib/session";

export function SessionBadge() {
  const { data, isLoading } = useCurrentActor();

  if (isLoading) {
    return (
      <div className="rounded-full border border-[color:var(--color-line)] px-4 py-2 text-sm text-ink/60">
        Resolving session...
      </div>
    );
  }

  return (
    <div className="rounded-full border border-[color:var(--color-line)] bg-white/70 px-4 py-2 text-sm">
      <span className="mr-2 text-ink/55">Actor</span>
      <span className="font-semibold">{data?.displayName ?? "Local Host"}</span>
    </div>
  );
}

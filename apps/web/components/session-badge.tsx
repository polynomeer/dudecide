"use client";

import { useCurrentActor } from "@/lib/session";

export function SessionBadge() {
  const { data, isLoading } = useCurrentActor();

  if (isLoading) {
    return (
      <div className="info-chip text-[color:var(--color-steel)]">
        Resolving session...
      </div>
    );
  }

  return (
    <div className="info-chip gap-2 text-[color:var(--color-ink)] normal-case tracking-[0.02em]">
      <span className="text-[color:var(--color-steel)]">Actor</span>
      <span className="font-semibold">{data?.displayName ?? "Local Host"}</span>
    </div>
  );
}

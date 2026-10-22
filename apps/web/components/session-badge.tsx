"use client";

import { useCurrentActor } from "@/lib/session";

export function SessionBadge() {
  const { data, isLoading } = useCurrentActor();

  if (isLoading) {
    return (
      <div className="info-chip text-muted">
        Resolving session...
      </div>
    );
  }

  return (
    <div className="info-chip gap-2 text-ink normal-case tracking-[0.02em]">
      <span className="text-muted">Actor</span>
      <span className="font-semibold">{data?.displayName ?? "Local Host"}</span>
    </div>
  );
}

"use client";

import type { Route } from "next";
import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";
import { ApiError } from "@/lib/api/client";
import { useCurrentActor } from "@/lib/session";

/**
 * Sends visitors without a session to guest start and brings them back afterwards.
 * Other failures, such as the API being down, stay with the page's own error handling.
 */
export function SessionGate({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const { error } = useCurrentActor();
  const missingSession = error instanceof ApiError && error.status === 401;

  useEffect(() => {
    if (missingSession) {
      const next = `${pathname ?? "/"}${window.location.search}`;
      router.replace(`/start?next=${encodeURIComponent(next)}` as Route);
    }
  }, [missingSession, pathname, router]);

  if (missingSession) {
    return <div className="p-6 text-sm text-muted">시작 화면으로 이동하고 있어요…</div>;
  }
  return <>{children}</>;
}

"use client";

import { getApiErrorMessage } from "@/lib/api/error-display";

export function ApiErrorPanel({
  error,
  resourceName,
  conflictMessage,
  fallbackMessage,
}: {
  error: unknown;
  resourceName?: string;
  conflictMessage?: string;
  fallbackMessage?: string;
}) {
  return (
    <div className="panel px-6 py-6 text-sm text-ember">
      {getApiErrorMessage(error, { resourceName, conflictMessage, fallbackMessage })}
    </div>
  );
}

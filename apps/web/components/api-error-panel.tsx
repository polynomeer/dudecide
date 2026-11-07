"use client";

import { getApiErrorMessage } from "@/lib/api/error-display";

export function ApiErrorPanel({
  error,
  resourceName,
  conflictMessage,
  fallbackMessage,
  onRetry,
}: {
  error: unknown;
  resourceName?: string;
  conflictMessage?: string;
  fallbackMessage?: string;
  onRetry?: () => void;
}) {
  return (
    <div role="alert" className="flex items-start gap-3 rounded-2xl bg-risk-soft px-5 py-4 text-sm text-risk-ink">
      <span
        aria-hidden="true"
        className="flex h-7 w-7 flex-none items-center justify-center rounded-full bg-risk-ink/10 text-sm font-extrabold"
      >
        !
      </span>
      <div className="flex min-w-0 flex-1 flex-col gap-2">
        <b className="text-[15px]">문제가 생겼어요</b>
        <p className="m-0 leading-relaxed">{getApiErrorMessage(error, { resourceName, conflictMessage, fallbackMessage })}</p>
        {onRetry ? (
          <button type="button" onClick={onRetry} className="self-start font-bold underline underline-offset-2">
            다시 시도
          </button>
        ) : null}
      </div>
    </div>
  );
}

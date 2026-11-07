"use client";

import Link from "next/link";
import { useEffect } from "react";

/** Shown when a page throws while rendering. Data errors stay inside the page as ApiErrorPanel. */
export default function ErrorPage({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <main role="alert" className="mx-auto flex min-h-screen max-w-[720px] flex-col justify-center gap-4 px-5 text-ink">
      <span className="num text-xs font-semibold text-muted">오류</span>
      <h1 className="font-display m-0 text-[clamp(1.8rem,4vw,2.4rem)]">화면을 보여 주지 못했어요</h1>
      <p className="section-copy m-0">잠시 후 다시 시도해 주세요. 입력한 내용은 저장되지 않았을 수 있어요.</p>
      {error.digest ? <p className="num m-0 text-xs text-muted">오류 코드 {error.digest}</p> : null}
      <div className="flex flex-wrap gap-2">
        <button type="button" onClick={reset} className="action-primary">
          다시 시도
        </button>
        <Link href="/" className="action-secondary">
          처음으로
        </Link>
      </div>
    </main>
  );
}

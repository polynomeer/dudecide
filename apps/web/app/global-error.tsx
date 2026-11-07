"use client";

import { useEffect } from "react";

/** Last resort when the root layout itself fails: its fonts and global styles are not available here. */
export default function GlobalError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <html lang="ko">
      <body style={{ margin: 0, fontFamily: "system-ui, sans-serif", background: "#F3F4F1", color: "#15171C" }}>
        <main role="alert" style={{ maxWidth: 560, margin: "20vh auto", padding: "0 20px" }}>
          <h1 style={{ fontSize: 28 }}>화면을 보여 주지 못했어요</h1>
          <p>잠시 후 다시 시도해 주세요.</p>
          {error.digest ? <p style={{ fontSize: 12 }}>오류 코드 {error.digest}</p> : null}
          <button type="button" onClick={reset} style={{ minHeight: 44, padding: "0 20px", fontSize: 14 }}>
            다시 시도
          </button>
        </main>
      </body>
    </html>
  );
}

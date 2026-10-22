"use client";

import Link from "next/link";
import type { Route } from "next";
import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { ApiErrorPanel } from "@/components/api-error-panel";
import { apiClient } from "@/lib/api/client";
import { decisionStatusChip, decisionStatusLabel } from "@/lib/display";

export function SearchWorkspace({ meetingId, initialQuery = "" }: { meetingId: string; initialQuery?: string }) {
  const [query, setQuery] = useState(initialQuery);
  const [submittedQuery, setSubmittedQuery] = useState(initialQuery.trim());

  // The top bar search navigates here with ?q=; follow it when it changes.
  useEffect(() => {
    setQuery(initialQuery);
    setSubmittedQuery(initialQuery.trim());
  }, [initialQuery]);

  const searchQuery = useQuery({
    queryKey: ["meeting-search", meetingId, submittedQuery],
    queryFn: () => apiClient.searchMeeting(meetingId, submittedQuery),
    enabled: submittedQuery.trim().length > 0,
  });

  return (
    <div className="flex flex-col gap-4">
      <form
        role="search"
        className="panel flex flex-wrap items-center gap-2.5 p-4"
        onSubmit={(event) => {
          event.preventDefault();
          setSubmittedQuery(query.trim());
        }}
      >
        <label htmlFor="search-keyword" className="sr-only">
          검색어
        </label>
        <input
          id="search-keyword"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          className="field h-11 min-w-0 flex-[1_1_280px]"
          placeholder="예: 가평, 일정, 확정"
        />
        <button type="submit" disabled={query.trim().length === 0} className="action-primary">
          검색 실행
        </button>
      </form>

      {submittedQuery.trim().length === 0 ? (
        <div className="empty-state">메시지, 결정, 할 일에서 찾을 단어를 입력해 주세요.</div>
      ) : searchQuery.isLoading ? (
        <div className="panel px-6 py-6 text-sm text-muted">검색 중이에요…</div>
      ) : searchQuery.error || !searchQuery.data ? (
        <ApiErrorPanel error={searchQuery.error} resourceName="검색 결과" />
      ) : (
        <>
          <p className="m-0 text-sm text-muted">
            <b className="text-ink">‘{searchQuery.data.query}’</b> 검색 결과 ·{" "}
            {searchQuery.data.messages.length + searchQuery.data.decisions.length + searchQuery.data.tasks.length}건
          </p>

          <ResultSection title="결정" count={searchQuery.data.decisions.length} emptyMessage="일치하는 결정이 없어요.">
            {searchQuery.data.decisions.map((decision) => (
              <Link
                key={decision.id}
                href={`/meetings/${meetingId}/decisions?decision=${decision.id}` as Route}
                className="flex items-center justify-between gap-3 border-t border-line py-3.5 first:border-t-0 hover:text-accent-text"
              >
                <b className="text-[15px]">{decision.title}</b>
                <span className={decisionStatusChip(decision.status)}>{decisionStatusLabel(decision.status)}</span>
              </Link>
            ))}
          </ResultSection>

          <ResultSection title="메시지" count={searchQuery.data.messages.length} emptyMessage="일치하는 메시지가 없어요.">
            {searchQuery.data.messages.map((message) => (
              <Link
                key={message.id}
                href={`/meetings/${meetingId}/threads/${message.threadId}` as Route}
                className="flex gap-3 border-t border-line py-3.5 first:border-t-0 hover:bg-surface-2"
              >
                <span className="num w-[84px] flex-none text-xs text-muted">{formatDateTime(message.createdAt)}</span>
                <span className="min-w-0 text-sm leading-normal">{message.body}</span>
              </Link>
            ))}
          </ResultSection>

          <ResultSection title="할 일" count={searchQuery.data.tasks.length} emptyMessage="일치하는 할 일이 없어요.">
            {searchQuery.data.tasks.map((task) => (
              <div key={task.id} className="flex items-center justify-between gap-3 border-t border-line py-3.5 first:border-t-0">
                <span>
                  <b className="text-[15px]">{task.title}</b>
                  <span className="num block text-xs text-muted">{task.dueAt ? `기한 ${formatDateTime(task.dueAt)}` : "기한 없음"}</span>
                </span>
                <span className="chip">{task.status}</span>
              </div>
            ))}
          </ResultSection>
        </>
      )}
    </div>
  );
}

function ResultSection({
  title,
  count,
  emptyMessage,
  children,
}: {
  title: string;
  count: number;
  emptyMessage: string;
  children: React.ReactNode;
}) {
  return (
    <section aria-label={title} className="rounded-[22px] bg-surface px-6 pb-2 pt-2">
      <div className="flex items-center justify-between pb-1 pt-4">
        <h2 className="m-0 text-[17px] font-bold">{title}</h2>
        <span className="chip">{count}</span>
      </div>
      {count ? <div className="flex flex-col">{children}</div> : <p className="section-copy m-0 py-3">{emptyMessage}</p>}
    </section>
  );
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    month: "numeric",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date(value));
}

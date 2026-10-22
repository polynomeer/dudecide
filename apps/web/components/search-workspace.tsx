"use client";

import Link from "next/link";
import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { ApiErrorPanel } from "@/components/api-error-panel";
import { apiClient } from "@/lib/api/client";

export function SearchWorkspace({ meetingId }: { meetingId: string }) {
  const [query, setQuery] = useState("");
  const [submittedQuery, setSubmittedQuery] = useState("");

  const searchQuery = useQuery({
    queryKey: ["meeting-search", meetingId, submittedQuery],
    queryFn: () => apiClient.searchMeeting(meetingId, submittedQuery),
    enabled: submittedQuery.trim().length > 0,
  });

  return (
    <div className="grid gap-5 xl:grid-cols-[0.82fr_1.18fr]">
      <section className="panel px-6 py-6">
        <div className="info-chip">Search</div>
        <h2 className="mt-4 font-[var(--font-display)] text-4xl font-semibold leading-[0.96] tracking-[-0.055em]">
          회의 키워드 검색
        </h2>
        <p className="section-copy mt-3">
          메시지, 의사결정, 태스크를 회의 범위 안에서 동시에 검색합니다.
        </p>

        <form
          className="mt-5 space-y-4"
          onSubmit={(event) => {
            event.preventDefault();
            setSubmittedQuery(query.trim());
          }}
        >
          <label className="block">
            <span className="text-xs font-semibold uppercase tracking-[0.16em] text-ink/45">
              Keyword
            </span>
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              className="field mt-2"
              placeholder="예: 가평, 일정, 확정"
            />
          </label>
          <div className="soft-card flex flex-wrap items-center justify-between gap-3">
            <div className="text-sm text-[color:rgba(13,21,38,0.6)]">
              공백 입력은 실행하지 않습니다.
            </div>
            <button
              type="submit"
              disabled={query.trim().length === 0}
              className="action-primary disabled:cursor-not-allowed disabled:bg-[rgba(13,21,38,0.4)]"
            >
              검색 실행
            </button>
          </div>
        </form>

        <div className="soft-card mt-5 text-sm text-[color:rgba(13,21,38,0.68)]">
          검색 결과에서 메시지 스레드, 의사결정 보드, 태스크 상태를 바로 확인할 수 있습니다.
        </div>
      </section>

      <section className="panel px-6 py-6">
        <div className="info-chip">Results</div>
        {submittedQuery.trim().length === 0 ? (
          <div className="empty-state mt-4">왼쪽에서 키워드를 입력해 검색을 시작해 주세요.</div>
        ) : searchQuery.isLoading ? (
          <div className="mt-4 text-sm text-ink/60">검색 결과를 불러오는 중입니다.</div>
        ) : searchQuery.error || !searchQuery.data ? (
          <ApiErrorPanel error={searchQuery.error} resourceName="검색 결과" />
        ) : (
          <div className="mt-4 space-y-5">
            <div className="soft-card text-sm text-[color:rgba(13,21,38,0.66)]">
              <strong>{searchQuery.data.query}</strong> 검색 결과
            </div>

            <ResultSection
              title="Messages"
              count={searchQuery.data.messages.length}
              emptyMessage="일치하는 메시지가 없습니다."
            >
              {searchQuery.data.messages.map((message) => (
                <article key={message.id} className="soft-card">
                  <div className="flex items-center justify-between gap-4">
                    <div className="text-xs font-semibold uppercase tracking-[0.16em] text-ink/45">
                      Message
                    </div>
                    <span className="text-xs text-ink/45">{formatDateTime(message.createdAt)}</span>
                  </div>
                  <p className="mt-3 text-sm leading-6 text-ink/84">{message.body}</p>
                  <Link
                    href={`/meetings/${meetingId}/threads/${message.threadId}`}
                    className="action-secondary mt-3 px-3 py-1 text-xs"
                  >
                    Open thread
                  </Link>
                </article>
              ))}
            </ResultSection>

            <ResultSection
              title="Decisions"
              count={searchQuery.data.decisions.length}
              emptyMessage="일치하는 의사결정이 없습니다."
            >
              {searchQuery.data.decisions.map((decision) => (
                <article key={decision.id} className="soft-card">
                  <div className="flex items-center justify-between gap-4">
                    <div className="text-lg font-semibold text-ink">{decision.title}</div>
                    <span className="info-chip">
                      {decision.status}
                    </span>
                  </div>
                  <Link
                    href={`/meetings/${meetingId}/decisions`}
                    className="action-secondary mt-3 px-3 py-1 text-xs"
                  >
                    Open board
                  </Link>
                </article>
              ))}
            </ResultSection>

            <ResultSection
              title="Tasks"
              count={searchQuery.data.tasks.length}
              emptyMessage="일치하는 태스크가 없습니다."
            >
              {searchQuery.data.tasks.map((task) => (
                <article key={task.id} className="soft-card">
                  <div className="flex items-center justify-between gap-4">
                    <div className="text-lg font-semibold text-ink">{task.title}</div>
                    <span className="info-chip">
                      {task.status}
                    </span>
                  </div>
                  <div className="mt-3 text-sm text-ink/64">
                    {task.dueAt ? `Due ${formatDateTime(task.dueAt)}` : "마감일 미설정"}
                  </div>
                </article>
              ))}
            </ResultSection>
          </div>
        )}
      </section>
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
    <section>
      <div className="flex items-center justify-between gap-4">
        <h3 className="font-[var(--font-display)] text-2xl font-semibold tracking-[-0.04em]">
          {title}
        </h3>
        <span className="text-sm text-ink/55">{count}건</span>
      </div>
      <div className="mt-3 space-y-3">
        {count ? children : <div className="empty-state">{emptyMessage}</div>}
      </div>
    </section>
  );
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

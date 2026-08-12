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
        <div className="eyebrow">Query</div>
        <h2 className="mt-2 font-[var(--font-display)] text-3xl font-semibold tracking-[-0.05em]">
          회의 키워드 검색
        </h2>
        <p className="mt-3 text-sm leading-6 text-ink/66">
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
              className="mt-2 w-full rounded-[1.2rem] border border-[color:var(--color-line)] bg-white/85 px-4 py-3 text-sm outline-none transition focus:border-pine"
              placeholder="예: 가평, 일정, 확정"
            />
          </label>
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-[1.25rem] border border-[color:var(--color-line)] bg-white/60 px-4 py-3">
            <div className="text-sm text-ink/60">
              공백 입력은 실행하지 않습니다.
            </div>
            <button
              type="submit"
              disabled={query.trim().length === 0}
              className="rounded-full bg-ink px-5 py-3 text-sm font-semibold text-mist transition hover:bg-pine disabled:cursor-not-allowed disabled:bg-ink/40"
            >
              검색 실행
            </button>
          </div>
        </form>

        <div className="mt-5 rounded-[1.5rem] border border-[color:var(--color-line)] bg-white/70 p-4 text-sm text-ink/68">
          검색 결과에서 메시지 스레드, 의사결정 보드, 태스크 상태를 바로 확인할 수 있습니다.
        </div>
      </section>

      <section className="panel px-6 py-6">
        <div className="eyebrow">Results</div>
        {submittedQuery.trim().length === 0 ? (
          <div className="mt-4 rounded-[1.5rem] border border-dashed border-[color:var(--color-line)] bg-white/55 px-5 py-6 text-sm text-ink/58">
            왼쪽에서 키워드를 입력해 검색을 시작해 주세요.
          </div>
        ) : searchQuery.isLoading ? (
          <div className="mt-4 text-sm text-ink/60">검색 결과를 불러오는 중입니다.</div>
        ) : searchQuery.error || !searchQuery.data ? (
          <ApiErrorPanel error={searchQuery.error} resourceName="검색 결과" />
        ) : (
          <div className="mt-4 space-y-5">
            <div className="rounded-[1.5rem] border border-[color:var(--color-line)] bg-white/70 px-4 py-4 text-sm text-ink/66">
              <strong>{searchQuery.data.query}</strong> 검색 결과
            </div>

            <ResultSection
              title="Messages"
              count={searchQuery.data.messages.length}
              emptyMessage="일치하는 메시지가 없습니다."
            >
              {searchQuery.data.messages.map((message) => (
                <article
                  key={message.id}
                  className="rounded-[1.5rem] border border-[color:var(--color-line)] bg-white/78 p-4"
                >
                  <div className="flex items-center justify-between gap-4">
                    <div className="text-xs font-semibold uppercase tracking-[0.16em] text-ink/45">
                      Message
                    </div>
                    <span className="text-xs text-ink/45">{formatDateTime(message.createdAt)}</span>
                  </div>
                  <p className="mt-3 text-sm leading-6 text-ink/84">{message.body}</p>
                  <Link
                    href={`/meetings/${meetingId}/threads/${message.threadId}`}
                    className="mt-3 inline-flex rounded-full border border-[color:var(--color-line)] px-3 py-1 text-xs font-semibold uppercase tracking-[0.14em] text-ink/68 transition hover:bg-white/90"
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
                <article
                  key={decision.id}
                  className="rounded-[1.5rem] border border-[color:var(--color-line)] bg-white/78 p-4"
                >
                  <div className="flex items-center justify-between gap-4">
                    <div className="text-lg font-semibold text-ink">{decision.title}</div>
                    <span className="rounded-full border border-[color:var(--color-line)] px-3 py-1 text-xs font-semibold uppercase tracking-[0.14em] text-ink/60">
                      {decision.status}
                    </span>
                  </div>
                  <Link
                    href={`/meetings/${meetingId}/decisions`}
                    className="mt-3 inline-flex rounded-full border border-[color:var(--color-line)] px-3 py-1 text-xs font-semibold uppercase tracking-[0.14em] text-ink/68 transition hover:bg-white/90"
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
                <article
                  key={task.id}
                  className="rounded-[1.5rem] border border-[color:var(--color-line)] bg-white/78 p-4"
                >
                  <div className="flex items-center justify-between gap-4">
                    <div className="text-lg font-semibold text-ink">{task.title}</div>
                    <span className="rounded-full border border-[color:var(--color-line)] px-3 py-1 text-xs font-semibold uppercase tracking-[0.14em] text-ink/60">
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
        {count ? children : (
          <div className="rounded-[1.5rem] border border-dashed border-[color:var(--color-line)] bg-white/55 px-5 py-6 text-sm text-ink/58">
            {emptyMessage}
          </div>
        )}
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

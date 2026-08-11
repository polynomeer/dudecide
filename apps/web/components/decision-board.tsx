"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  apiClient,
  type DecisionDetailResponse,
  type DecisionHistoryView,
  type DecisionResponse,
} from "@/lib/api/client";

export function DecisionBoard({ meetingId }: { meetingId: string }) {
  const meetingQuery = useQuery({
    queryKey: ["meeting", meetingId],
    queryFn: () => apiClient.getMeeting(meetingId),
  });
  const decisionsQuery = useQuery({
    queryKey: ["meeting-decisions", meetingId],
    queryFn: () => apiClient.listDecisions(meetingId),
  });
  const [selectedDecisionId, setSelectedDecisionId] = useState<string | null>(null);

  useEffect(() => {
    if (!selectedDecisionId && decisionsQuery.data && decisionsQuery.data.length > 0) {
      setSelectedDecisionId(decisionsQuery.data[0].id);
    }
  }, [decisionsQuery.data, selectedDecisionId]);

  const selectedDecision = useMemo(
    () => decisionsQuery.data?.find((item) => item.id === selectedDecisionId) ?? null,
    [decisionsQuery.data, selectedDecisionId],
  );

  const detailQuery = useQuery({
    queryKey: ["decision-detail", selectedDecisionId],
    queryFn: () => apiClient.getDecision(selectedDecisionId!),
    enabled: Boolean(selectedDecisionId),
  });

  if (meetingQuery.isLoading || decisionsQuery.isLoading) {
    return <div className="panel px-6 py-6 text-sm text-ink/60">결정 보드를 불러오는 중입니다.</div>;
  }

  if (meetingQuery.error || decisionsQuery.error || !meetingQuery.data) {
    return (
      <div className="panel px-6 py-6 text-sm text-ember">
        결정 보드를 불러오지 못했습니다. 세션 상태와 회의 접근 권한, 백엔드 실행 상태를 확인해 주세요.
      </div>
    );
  }

  return (
    <div className="grid gap-5 xl:grid-cols-[0.9fr_1.1fr]">
      <section className="panel px-6 py-6">
        <div className="flex items-start justify-between gap-4">
          <div>
            <div className="eyebrow">Decision list</div>
            <h2 className="mt-2 font-[var(--font-display)] text-3xl font-semibold tracking-[-0.05em]">
              회의 결정 목록
            </h2>
          </div>
          <div className="rounded-full border border-[color:var(--color-line)] bg-white/70 px-4 py-2 text-sm">
            총 {decisionsQuery.data?.length ?? 0}건
          </div>
        </div>

        <div className="mt-5 space-y-3">
          {decisionsQuery.data?.length ? (
            decisionsQuery.data.map((decision) => (
              <button
                key={decision.id}
                type="button"
                onClick={() => setSelectedDecisionId(decision.id)}
                className={[
                  "w-full rounded-[1.5rem] border px-4 py-4 text-left transition",
                  decision.id === selectedDecisionId
                    ? "border-pine bg-pine/10"
                    : "border-[color:var(--color-line)] bg-white/72 hover:bg-white/88",
                ].join(" ")}
              >
                <div className="flex items-center justify-between gap-4">
                  <span className="text-xs font-semibold uppercase tracking-[0.16em] text-ink/45">
                    {decision.decisionType}
                  </span>
                  <StatusBadge status={decision.status} />
                </div>
                <div className="mt-2 font-[var(--font-display)] text-xl font-semibold tracking-[-0.04em]">
                  {decision.title}
                </div>
                <div className="mt-3 text-sm text-ink/65">
                  Thread {decision.threadId.slice(0, 8)} ·{" "}
                  {decision.decidedAt ? formatDateTime(decision.decidedAt) : "미확정"}
                </div>
              </button>
            ))
          ) : (
            <div className="rounded-[1.5rem] border border-dashed border-[color:var(--color-line)] bg-white/55 px-5 py-6 text-sm text-ink/58">
              아직 생성된 결정이 없습니다. 다음 단위에서 결정 생성 폼과 호스트 액션을 추가할 예정입니다.
            </div>
          )}
        </div>
      </section>

      <section className="panel px-6 py-6">
        <div className="eyebrow">Decision detail</div>
        {selectedDecision ? (
          detailQuery.isLoading ? (
            <div className="mt-4 text-sm text-ink/60">선택한 결정을 불러오는 중입니다.</div>
          ) : detailQuery.error || !detailQuery.data ? (
            <div className="mt-4 text-sm text-ember">결정 상세를 불러오지 못했습니다.</div>
          ) : (
            <DecisionDetailPanel meetingId={meetingId} decision={detailQuery.data} />
          )
        ) : (
          <div className="mt-4 text-sm text-ink/60">왼쪽 목록에서 확인할 결정을 선택해 주세요.</div>
        )}
      </section>
    </div>
  );
}

function DecisionDetailPanel({
  meetingId,
  decision,
}: {
  meetingId: string;
  decision: DecisionDetailResponse;
}) {
  return (
    <div className="mt-3 space-y-6">
      <div>
        <div className="flex items-center justify-between gap-4">
          <h3 className="font-[var(--font-display)] text-3xl font-semibold tracking-[-0.05em]">
            {decision.title}
          </h3>
          <StatusBadge status={decision.status} />
        </div>
        <div className="mt-3 flex flex-wrap gap-2 text-sm text-ink/62">
          <span className="rounded-full border border-[color:var(--color-line)] px-3 py-1">
            Type {decision.decisionType}
          </span>
          <Link
            href={`/meetings/${meetingId}/threads/${decision.threadId}`}
            className="rounded-full border border-[color:var(--color-line)] px-3 py-1 transition hover:bg-white/80"
          >
            Open thread
          </Link>
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <StateCard title="Proposed result" state={decision.proposedResult} />
        <StateCard title="Confirmed result" state={decision.confirmedResult} />
      </div>

      <section className="rounded-[1.75rem] border border-[color:var(--color-line)] bg-white/70 p-5">
        <div className="flex items-center justify-between gap-4">
          <h4 className="font-[var(--font-display)] text-2xl font-semibold tracking-[-0.04em]">
            Evidence
          </h4>
          <span className="text-sm text-ink/55">{decision.evidence.length}건</span>
        </div>
        <div className="mt-4 space-y-3">
          {decision.evidence.length ? (
            decision.evidence.map((item) => (
              <article
                key={item.id}
                className="rounded-[1.5rem] border border-[color:var(--color-line)] bg-white/78 p-4"
              >
                <div className="flex items-center justify-between gap-4">
                  <span className="text-xs font-semibold uppercase tracking-[0.16em] text-ink/45">
                    {item.evidenceType}
                  </span>
                  <span className="text-xs text-ink/45">
                    {item.createdAt ? formatDateTime(item.createdAt) : "시간 미기록"}
                  </span>
                </div>
                <p className="mt-3 text-sm leading-6 text-ink/82">{item.quoteSnapshot}</p>
              </article>
            ))
          ) : (
            <div className="text-sm text-ink/58">아직 연결된 evidence가 없습니다.</div>
          )}
        </div>
      </section>

      <section className="rounded-[1.75rem] border border-[color:var(--color-line)] bg-white/70 p-5">
        <div className="flex items-center justify-between gap-4">
          <h4 className="font-[var(--font-display)] text-2xl font-semibold tracking-[-0.04em]">
            History
          </h4>
          <span className="text-sm text-ink/55">{decision.history.length}건</span>
        </div>
        <div className="mt-4 space-y-4">
          {decision.history.length ? (
            decision.history.map((entry) => <HistoryEntry key={entry.id} entry={entry} />)
          ) : (
            <div className="text-sm text-ink/58">기록된 상태 이력이 없습니다.</div>
          )}
        </div>
      </section>
    </div>
  );
}

function StateCard({
  title,
  state,
}: {
  title: string;
  state: Record<string, unknown>;
}) {
  return (
    <section className="rounded-[1.75rem] border border-[color:var(--color-line)] bg-white/72 p-5">
      <div className="text-xs font-semibold uppercase tracking-[0.16em] text-ink/45">{title}</div>
      <pre className="mt-3 overflow-x-auto rounded-[1.25rem] bg-ink px-4 py-4 text-xs leading-6 text-mist">
        {JSON.stringify(state, null, 2)}
      </pre>
    </section>
  );
}

function HistoryEntry({ entry }: { entry: DecisionHistoryView }) {
  return (
    <article className="rounded-[1.5rem] border border-[color:var(--color-line)] bg-white/78 p-4">
      <div className="flex items-center justify-between gap-4">
        <div className="text-xs font-semibold uppercase tracking-[0.16em] text-ink/45">
          {entry.action}
        </div>
        <div className="text-xs text-ink/45">
          {entry.createdAt ? formatDateTime(entry.createdAt) : "시간 미기록"}
        </div>
      </div>
      {entry.reason ? <p className="mt-3 text-sm text-ink/78">{entry.reason}</p> : null}
      <div className="mt-4 grid gap-3 lg:grid-cols-2">
        <MiniState title="Before" state={entry.beforeState} />
        <MiniState title="After" state={entry.afterState} />
      </div>
    </article>
  );
}

function MiniState({
  title,
  state,
}: {
  title: string;
  state: Record<string, unknown>;
}) {
  return (
    <div className="rounded-[1.25rem] bg-mist/70 p-3">
      <div className="text-xs font-semibold uppercase tracking-[0.16em] text-ink/45">{title}</div>
      <pre className="mt-2 overflow-x-auto text-xs leading-5 text-ink/76">
        {JSON.stringify(state, null, 2)}
      </pre>
    </div>
  );
}

function StatusBadge({ status }: { status: string }) {
  const tone =
    status === "CONFIRMED"
      ? "bg-pine/12 text-pine border-pine/15"
      : status === "NEEDS_RECONSIDERATION"
        ? "bg-ember/12 text-ember border-ember/15"
        : "bg-sky/18 text-ink border-sky/20";

  return (
    <span className={`rounded-full border px-3 py-1 text-xs font-semibold uppercase tracking-[0.14em] ${tone}`}>
      {status.replaceAll("_", " ")}
    </span>
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

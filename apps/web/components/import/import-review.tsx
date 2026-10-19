"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { Route } from "next";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMemo, useState } from "react";
import { ApiErrorPanel } from "@/components/api-error-panel";
import {
  DECISION_STATUS_LABELS,
  EVIDENCE_ROLE_LABELS,
  formatDateTime,
  STAGE_LABELS,
} from "@/components/import/import-copy";
import { getApiErrorMessage } from "@/lib/api/error-display";
import { apiClient, type ApplyChatImportResponse, type ChatImportView, type MessageView } from "@/lib/api/client";
import { useMeetingHostAccess } from "@/lib/session";

type ResultOption = { label: string; supporters: number; objectors: number; superseded: boolean };

export type ResultItem = {
  itemId: string;
  kind: "decision" | "open_question" | "task";
  status?: string;
  applicable?: boolean;
  decisionType?: string;
  title: string;
  proposedResult?: { option?: string };
  options?: ResultOption[];
  evidence: Array<{ messageId: string; role: string }>;
  objections?: string[];
  ownerSpeakerId?: string | null;
};

type ImportResult = { items: ResultItem[]; digest: string | null; appliedItemIds: string[] };

const POLL_MS = 2000;
const SECTIONS: Array<{ title: string; match: (item: ResultItem) => boolean }> = [
  { title: "결정된 것으로 보이는 항목", match: (item) => item.kind === "decision" && item.status === "likely_decided" },
  { title: "의견이 갈린 항목", match: (item) => item.kind === "decision" && item.status === "contested" },
  { title: "아직 논의 중인 항목", match: (item) => item.kind === "decision" && item.status === "open" },
  { title: "할 일", match: (item) => item.kind === "task" },
  { title: "답이 없는 질문", match: (item) => item.kind === "open_question" },
  { title: "흐지부지된 제안", match: (item) => item.kind === "decision" && item.status === "abandoned" },
];

export function ImportReview({ meetingId, importId }: { meetingId: string; importId: string }) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const { isHost } = useMeetingHostAccess(meetingId);
  const [selection, setSelection] = useState<Set<string> | null>(null);
  const [applied, setApplied] = useState<ApplyChatImportResponse | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const importQuery = useQuery({
    queryKey: ["chat-import", importId],
    queryFn: () => apiClient.getChatImport(importId),
    refetchInterval: (query) => (isAnalyzing(query.state.data) ? POLL_MS : false),
  });
  const view = importQuery.data;
  const reviewable = view?.status === "ready" || view?.status === "applied";
  const evidenceQuery = useQuery({
    queryKey: ["chat-import-evidence", importId],
    queryFn: () => apiClient.getChatImportEvidence(importId),
    enabled: reviewable,
  });

  const result = useMemo(() => readResult(view?.result), [view?.result]);
  const messagesById = useMemo(
    () => new Map((evidenceQuery.data ?? []).map((message) => [message.id, message])),
    [evidenceQuery.data],
  );
  const defaultSelection = useMemo(
    () =>
      new Set(
        result.items
          .filter((item) => isApplicable(item) && !result.appliedItemIds.includes(item.itemId))
          .filter((item) => item.kind === "task" || item.status === "likely_decided")
          .map((item) => item.itemId),
      ),
    [result],
  );
  const selected = selection ?? defaultSelection;

  const applyMutation = useMutation({
    mutationFn: () => apiClient.applyChatImport(importId, [...selected], crypto.randomUUID()),
    onSuccess: async (response) => {
      setApplied(response);
      setSelection(new Set());
      setActionError(null);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["chat-import", importId] }),
        queryClient.invalidateQueries({ queryKey: ["meeting", meetingId] }),
        queryClient.invalidateQueries({ queryKey: ["meeting-decisions", meetingId] }),
        queryClient.invalidateQueries({ queryKey: ["meeting-tasks", meetingId] }),
      ]);
    },
    onError: (error: unknown) =>
      setActionError(
        getApiErrorMessage(error, {
          resourceName: "결정 보드 반영",
          conflictMessage: "분석이 끝난 가져오기만 반영할 수 있습니다.",
        }),
      ),
  });

  const deleteMutation = useMutation({
    mutationFn: async () => {
      const impact = (await apiClient.getChatImport(importId, true)).deletionImpact;
      const message = [
        "가져온 대화를 삭제할까요?",
        `메시지 ${impact?.removedMessages ?? 0}개와 이 메시지를 근거로 한 근거 ${impact?.removedEvidence ?? 0}건이 함께 지워집니다.`,
        impact?.affectedConfirmedDecisions
          ? `확정된 결정 ${impact.affectedConfirmedDecisions}건의 근거도 사라집니다.`
          : "",
      ]
        .filter(Boolean)
        .join("\n");
      if (!window.confirm(message)) {
        return null;
      }
      return apiClient.deleteChatImport(importId);
    },
    onSuccess: (response) => {
      if (response) {
        router.push(`/meetings/${meetingId}` as Route);
      }
    },
    onError: (error: unknown) => setActionError(getApiErrorMessage(error, { resourceName: "가져온 대화 삭제" })),
  });

  if (importQuery.isLoading) {
    return <div className="panel px-6 py-6 text-sm text-ink/60">가져오기 상태를 불러오는 중입니다.</div>;
  }
  if (importQuery.error || !view) {
    return <ApiErrorPanel error={importQuery.error} resourceName="가져온 대화" />;
  }

  if (isAnalyzing(view)) {
    return <AnalysisProgress view={view} />;
  }
  if (view.status === "failed") {
    return (
      <section className="panel px-6 py-6" role="alert">
        <div className="info-chip">분석 실패</div>
        <p className="mt-4 text-sm">분석 중 문제가 생겼습니다: {view.failureReason ?? "알 수 없는 오류"}</p>
        <Link href={`/meetings/${meetingId}/import` as Route} className="action-secondary mt-4 inline-flex">
          다시 가져오기
        </Link>
      </section>
    );
  }
  if (view.status === "deleted") {
    return <section className="panel px-6 py-6 text-sm">삭제된 가져오기입니다.</section>;
  }

  const toggle = (itemId: string) =>
    setSelection(() => {
      const next = new Set(selected);
      if (next.has(itemId)) {
        next.delete(itemId);
      } else {
        next.add(itemId);
      }
      return next;
    });

  return (
    <div className="grid gap-5 xl:grid-cols-[1.25fr_0.75fr]">
      <div className="space-y-5">
        {result.digest ? (
          <section className="panel px-6 py-5">
            <div className="info-chip">요약</div>
            <p className="mt-3 text-sm">{result.digest}</p>
          </section>
        ) : null}
        {SECTIONS.map((section) => {
          const items = result.items.filter(section.match);
          if (items.length === 0) {
            return null;
          }
          return (
            <section key={section.title} className="panel px-6 py-6" aria-label={section.title}>
              <h2 className="text-xl font-semibold tracking-[-0.02em]">{section.title}</h2>
              <ul className="mt-4 space-y-3">
                {items.map((item) => (
                  <ResultItemCard
                    key={item.itemId}
                    item={item}
                    messagesById={messagesById}
                    alreadyApplied={result.appliedItemIds.includes(item.itemId)}
                    selectable={isHost && isApplicable(item) && !result.appliedItemIds.includes(item.itemId)}
                    checked={selected.has(item.itemId)}
                    onToggle={() => toggle(item.itemId)}
                  />
                ))}
              </ul>
            </section>
          );
        })}
        {result.items.length === 0 ? (
          <section className="empty-state px-6 py-6 text-sm">대화에서 결정이나 할 일을 찾지 못했습니다.</section>
        ) : null}
      </div>

      <aside className="space-y-5">
        <section className="panel px-6 py-6">
          <div className="info-chip">결정 보드에 반영</div>
          <p className="section-copy mt-3">
            고른 항목은 확정되지 않은 상태로 보드에 올라갑니다. 결정은 모임 구성원이 확인한 뒤 확정하세요.
          </p>
          {isHost ? (
            <button
              type="button"
              onClick={() => applyMutation.mutate()}
              disabled={selected.size === 0 || applyMutation.isPending}
              className="action-primary mt-4 w-full disabled:cursor-not-allowed disabled:bg-[rgba(13,21,38,0.4)]"
            >
              {applyMutation.isPending ? "반영하는 중..." : `${selected.size}개 항목 반영`}
            </button>
          ) : (
            <p className="mt-4 text-sm text-ink/60">호스트만 보드에 반영할 수 있습니다.</p>
          )}
          {applied ? (
            <div className="soft-card mt-4 px-4 py-3 text-sm" role="status">
              결정 {applied.decisionIds.length}건, 할 일 {applied.taskIds.length}건을 반영했습니다.
              <div className="mt-2 flex gap-3">
                <Link href={`/meetings/${meetingId}/decisions` as Route} className="font-semibold underline">
                  결정 보드
                </Link>
                <Link href={`/meetings/${meetingId}/tasks` as Route} className="font-semibold underline">
                  할 일
                </Link>
              </div>
            </div>
          ) : null}
          {actionError ? (
            <p role="alert" className="mt-3 text-sm text-[color:var(--color-danger,#b42318)]">
              {actionError}
            </p>
          ) : null}
        </section>
        {isHost ? <ShareInviteLink meetingId={meetingId} /> : null}
        <section className="panel px-6 py-6 text-sm">
          <div className="info-chip">가져온 대화</div>
          <p className="mt-3">
            {view.sourceRoomName ?? "채팅방"} · 메시지 {view.messageCount.toLocaleString()}개
            {view.duplicateCount ? ` (중복 ${view.duplicateCount}개 제외)` : ""}
          </p>
          <p className="section-copy mt-1">
            {formatDateTime(view.rangeStart)} ~ {formatDateTime(view.rangeEnd)}
          </p>
          <button
            type="button"
            onClick={() => deleteMutation.mutate()}
            disabled={deleteMutation.isPending}
            className="action-secondary mt-4 w-full"
          >
            가져온 대화 삭제
          </button>
        </section>
      </aside>
    </div>
  );
}

function ShareInviteLink({ meetingId }: { meetingId: string }) {
  const [copied, setCopied] = useState(false);
  const inviteMutation = useMutation({
    mutationFn: () => apiClient.createInviteLink(meetingId),
  });
  const url = inviteMutation.data ? `${window.location.origin}/invite/${inviteMutation.data.token}` : null;

  const copy = async () => {
    if (!url) {
      return;
    }
    try {
      await navigator.clipboard.writeText(url);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  };

  return (
    <section className="panel px-6 py-6 text-sm" aria-label="카톡방에 공유">
      <div className="info-chip">카톡방에 공유</div>
      <p className="section-copy mt-3">
        링크를 연 사람은 참여하기 전에는 결정 제목과 개수만 봅니다. 대화 원문과 이름은 참여한 뒤에 보입니다.
      </p>
      {url ? (
        <div className="mt-4 space-y-2">
          <input readOnly value={url} aria-label="초대 링크" className="field font-mono text-xs" />
          <button type="button" onClick={() => void copy()} className="action-secondary w-full">
            {copied ? "복사했어요" : "링크 복사"}
          </button>
        </div>
      ) : (
        <button
          type="button"
          onClick={() => inviteMutation.mutate()}
          disabled={inviteMutation.isPending}
          className="action-secondary mt-4 w-full"
        >
          초대 링크 만들기
        </button>
      )}
      {inviteMutation.error ? (
        <p role="alert" className="mt-2 text-[color:var(--color-danger,#b42318)]">
          {getApiErrorMessage(inviteMutation.error, { resourceName: "초대 링크" })}
        </p>
      ) : null}
    </section>
  );
}

function AnalysisProgress({ view }: { view: ChatImportView }) {
  const stage = typeof view.progress.stage === "string" ? view.progress.stage : "claimed";
  const done = Number(view.progress.done ?? 0);
  const total = Number(view.progress.total ?? 0);
  const percent = stage === "map" && total > 0 ? Math.round((done / total) * 100) : null;

  return (
    <section className="panel px-6 py-8" aria-live="polite">
      <div className="info-chip">분석 중</div>
      <h2 className="mt-4 text-2xl font-semibold tracking-[-0.03em]">
        {view.status === "submitted" ? "분석 순서를 기다리고 있습니다" : (STAGE_LABELS[stage] ?? "분석 중입니다")}
      </h2>
      <p className="section-copy mt-2">메시지 {view.messageCount.toLocaleString()}개를 읽고 있습니다. 이 화면을 열어 두면 끝나는 대로 보여 드립니다.</p>
      {percent !== null ? (
        <div className="mt-5">
          <div
            role="progressbar"
            aria-valuenow={percent}
            aria-valuemin={0}
            aria-valuemax={100}
            className="h-2 overflow-hidden rounded-full bg-[rgba(13,21,38,0.08)]"
          >
            <div className="h-full bg-[color:var(--color-pine,#1f6f5c)]" style={{ width: `${percent}%` }} />
          </div>
          <p className="mt-2 text-xs text-ink/50">
            {done}/{total} 구간
          </p>
        </div>
      ) : null}
    </section>
  );
}

function ResultItemCard({
  item,
  messagesById,
  alreadyApplied,
  selectable,
  checked,
  onToggle,
}: {
  item: ResultItem;
  messagesById: Map<string, MessageView>;
  alreadyApplied: boolean;
  selectable: boolean;
  checked: boolean;
  onToggle: () => void;
}) {
  const quotes = item.evidence
    .map((entry) => ({ entry, message: messagesById.get(entry.messageId) }))
    .filter((quote): quote is { entry: ResultItem["evidence"][number]; message: MessageView } => Boolean(quote.message));
  const headline = item.proposedResult?.option ? `${item.title}: ${item.proposedResult.option}` : item.title;

  return (
    <li className="soft-card px-4 py-4">
      <div className="flex items-start gap-3">
        {selectable ? (
          <input type="checkbox" aria-label={`${headline} 반영`} checked={checked} onChange={onToggle} className="mt-1" />
        ) : null}
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="font-semibold">{headline}</h3>
            {item.status ? <span className="info-chip">{DECISION_STATUS_LABELS[item.status] ?? item.status}</span> : null}
            {alreadyApplied ? <span className="info-chip">반영됨</span> : null}
          </div>
          {item.options && item.options.length > 0 && item.status !== "likely_decided" ? (
            <ul className="mt-2 flex flex-wrap gap-2 text-xs">
              {item.options.map((option) => (
                <li key={option.label} className={option.superseded ? "text-ink/40 line-through" : "text-ink/70"}>
                  {option.label} · 찬성 {option.supporters} · 반대 {option.objectors}
                </li>
              ))}
            </ul>
          ) : null}
          {quotes.length > 0 ? (
            <details className="mt-3 text-sm">
              <summary className="cursor-pointer text-ink/60">근거 {quotes.length}개 보기</summary>
              <ul className="mt-2 space-y-1">
                {quotes.map(({ entry, message }) => (
                  <li key={`${entry.messageId}-${entry.role}`} className="text-ink/80">
                    <span className="text-xs text-ink/50">
                      {EVIDENCE_ROLE_LABELS[entry.role] ?? entry.role} · {message.speaker.displayName} ·{" "}
                      {formatDateTime(message.createdAt)}
                    </span>
                    <p className="whitespace-pre-wrap">{message.body}</p>
                  </li>
                ))}
              </ul>
            </details>
          ) : null}
        </div>
      </div>
    </li>
  );
}

function isAnalyzing(view: ChatImportView | undefined): boolean {
  return view?.status === "submitted" || view?.status === "analyzing";
}

function isApplicable(item: ResultItem): boolean {
  return item.kind === "task" || (item.kind === "decision" && item.applicable !== false);
}

function readResult(result: Record<string, unknown> | null | undefined): ImportResult {
  const items = Array.isArray(result?.items) ? (result.items as ResultItem[]) : [];
  const applied = Array.isArray(result?.appliedItemIds) ? (result.appliedItemIds as string[]) : [];
  return { items, digest: typeof result?.digest === "string" ? result.digest : null, appliedItemIds: applied };
}

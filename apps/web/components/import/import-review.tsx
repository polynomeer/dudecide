"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { Route } from "next";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useMemo, useState } from "react";
import { ApiErrorPanel } from "@/components/api-error-panel";
import { ImportSteps } from "@/components/import/import-steps";
import {
  DECISION_STATUS_LABELS,
  EVIDENCE_ROLE_LABELS,
  formatDateTime,
  STAGE_LABELS,
} from "@/components/import/import-copy";
import { getApiErrorMessage } from "@/lib/api/error-display";
import { apiClient, type ApplyChatImportResponse, type ChatImportView, type MessageView } from "@/lib/api/client";
import { decisionTypeLabel } from "@/lib/display";
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
const SECTIONS: Array<{ title: string; chip: string; hint?: string; match: (item: ResultItem) => boolean }> = [
  {
    title: "결정된 것으로 보이는 항목",
    chip: "chip-confirmed",
    hint: "기본으로 골라 뒀어요",
    match: (item) => item.kind === "decision" && item.status === "likely_decided",
  },
  {
    title: "의견이 갈린 항목",
    chip: "chip-voting",
    hint: "투표로 정하면 좋아요",
    match: (item) => item.kind === "decision" && item.status === "contested",
  },
  { title: "아직 논의 중인 항목", chip: "chip-discussion", match: (item) => item.kind === "decision" && item.status === "open" },
  { title: "할 일", chip: "chip", match: (item) => item.kind === "task" },
  { title: "답이 없는 질문", chip: "chip-discussion", hint: "보드에 남지 않아요", match: (item) => item.kind === "open_question" },
  { title: "흐지부지된 제안", chip: "chip-hold", match: (item) => item.kind === "decision" && item.status === "abandoned" },
];

const KIND_LABELS: Record<ResultItem["kind"], string> = {
  decision: "결정",
  open_question: "질문",
  task: "할 일",
};

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
    return <div className="panel px-6 py-6 text-sm text-muted">가져오기 상태를 불러오는 중입니다.</div>;
  }
  if (importQuery.error || !view) {
    return <ApiErrorPanel error={importQuery.error} resourceName="가져온 대화" />;
  }

  if (isAnalyzing(view)) {
    return <AnalysisProgress view={view} />;
  }
  if (view.status === "failed") {
    return (
      <section className="panel flex flex-col items-start gap-3 p-7" role="alert">
        <ImportSteps current={3} />
        <h1 className="font-display m-0 mt-2 text-2xl">분석하지 못했어요</h1>
        <p className="m-0 text-sm">분석 중 문제가 생겼습니다: {view.failureReason ?? "알 수 없는 오류"}</p>
        <Link href={`/meetings/${meetingId}/import` as Route} className="action-secondary">
          다시 가져오기
        </Link>
      </section>
    );
  }
  if (view.status === "deleted") {
    return <section className="empty-state">삭제된 가져오기입니다.</section>;
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
  const selectedItems = result.items.filter((item) => selected.has(item.itemId));
  const counts = {
    decisions: selectedItems.filter((item) => item.kind === "decision").length,
    tasks: selectedItems.filter((item) => item.kind === "task").length,
    questions: result.items.filter((item) => item.kind === "open_question").length,
  };

  return (
    <div className="flex flex-col gap-5">
      <ImportSteps current={3} />
      <div className="flex flex-wrap items-start gap-5">
        <div className="flex min-w-0 flex-[999_1_640px] flex-col gap-[18px]">
          <div className="flex flex-col gap-1.5">
            <span className="num text-xs font-semibold text-muted">
              {view.sourceRoomName ?? "채팅방"} · 메시지 {view.messageCount.toLocaleString()}개에서
            </span>
            <h1 className="font-display m-0 text-[clamp(1.7rem,2.6vw,2.2rem)]">
              {result.items.length ? `대화에서 ${result.items.length}개를 찾았어요` : "대화 분석 결과"}
            </h1>
            <p className="m-0 text-sm text-muted">
              AI는 확정하지 않아요. 체크한 항목만 ‘제안’으로 모임에 들어가고, 확정은 호스트가 해요.
            </p>
          </div>
          {result.digest ? <p className="m-0 rounded-2xl bg-surface px-5 py-4 text-sm leading-relaxed">{result.digest}</p> : null}
          {SECTIONS.map((section) => {
            const items = result.items.filter(section.match);
            if (items.length === 0) {
              return null;
            }
            return (
              <section key={section.title} className="flex flex-col gap-2.5" aria-label={section.title}>
                <div className="flex items-center gap-2">
                  <h2 className="m-0 text-base font-bold">{section.title}</h2>
                  <span className={section.chip}>{items.length}</span>
                  {section.hint ? <span className="text-xs text-muted">{section.hint}</span> : null}
                </div>
                <ul className="m-0 grid list-none grid-cols-[repeat(auto-fill,minmax(320px,1fr))] gap-2.5 p-0">
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
            <section className="empty-state">대화에서 결정이나 할 일을 찾지 못했습니다.</section>
          ) : null}
        </div>

        <aside className="flex max-w-full flex-[1_1_300px] flex-col gap-3.5 lg:max-w-[360px]">
          <section aria-label="결정 보드에 반영" className="flex flex-col gap-3.5 rounded-[20px] bg-surface px-6 py-[22px] ring-2 ring-accent">
            <h2 className="m-0 text-base font-bold">결정 보드에 반영</h2>
            <dl className="m-0">
              {[
                ["결정 제안", counts.decisions],
                ["할 일", counts.tasks],
                ["답 없는 질문", counts.questions],
              ].map(([label, value]) => (
                <div key={label} className="flex items-center justify-between border-t border-line py-2.5 text-sm first:border-t-0">
                  <dt>{label}</dt>
                  <dd className="num m-0 font-bold">{value}</dd>
                </div>
              ))}
            </dl>
            {isHost ? (
              <button
                type="button"
                onClick={() => applyMutation.mutate()}
                disabled={selected.size === 0 || applyMutation.isPending}
                className="action-primary min-h-[52px] w-full rounded-[14px] text-[15px]"
              >
                {applyMutation.isPending ? "반영하는 중..." : `${selected.size}개 항목 반영`}
              </button>
            ) : (
              <p className="m-0 text-sm text-muted">호스트만 보드에 반영할 수 있습니다.</p>
            )}
            <p className="m-0 text-xs leading-normal text-muted">
              고른 항목은 확정되지 않은 상태로 보드에 올라가고, 근거 메시지는 각 항목에 연결돼요.
            </p>
            {applied ? (
              <div className="rounded-xl bg-surface-2 px-4 py-3 text-sm" role="status">
                결정 {applied.decisionIds.length}건, 할 일 {applied.taskIds.length}건을 반영했습니다.
                <div className="mt-2 flex gap-3">
                  <Link href={`/meetings/${meetingId}/decisions` as Route} className="link-accent">
                    결정 보드
                  </Link>
                  <Link href={`/meetings/${meetingId}/tasks` as Route} className="link-accent">
                    할 일
                  </Link>
                </div>
              </div>
            ) : null}
            {actionError ? (
              <p role="alert" className="m-0 text-sm text-danger">
                {actionError}
              </p>
            ) : null}
          </section>
          {isHost ? <ShareInviteLink meetingId={meetingId} /> : null}
          <section aria-label="가져온 대화 관리" className="flex flex-col gap-2 rounded-[20px] bg-surface px-6 py-[18px] text-[13px] text-ink/80">
            <b className="text-sm text-ink">가져온 대화</b>
            <span>
              {view.sourceRoomName ?? "채팅방"} · 메시지 {view.messageCount.toLocaleString()}개
              {view.duplicateCount ? ` (중복 ${view.duplicateCount}개 제외)` : ""}
            </span>
            <span className="num text-xs text-muted">
              {formatDateTime(view.rangeStart)} ~ {formatDateTime(view.rangeEnd)}
            </span>
            {view.purgeAt ? (
              <span className="num text-xs text-muted">반영하지 않으면 {formatDateTime(view.purgeAt)} 자동 삭제</span>
            ) : null}
            <button
              type="button"
              onClick={() => deleteMutation.mutate()}
              disabled={deleteMutation.isPending}
              className="min-h-[40px] self-start font-bold text-risk-ink hover:underline disabled:opacity-40"
            >
              가져온 대화 삭제
            </button>
          </section>
        </aside>
      </div>
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
    <section className="flex flex-col gap-3 rounded-[20px] bg-surface px-6 py-[18px] text-sm" aria-label="카톡방에 공유">
      <b className="text-sm">카톡방에 공유</b>
      <p className="m-0 text-[13px] leading-normal text-muted">
        링크를 연 사람은 참여하기 전에는 결정 제목과 개수만 봅니다. 대화 원문과 이름은 참여한 뒤에 보입니다.
      </p>
      {url ? (
        <div className="flex flex-col gap-2">
          <input readOnly value={url} aria-label="초대 링크" className="field num text-xs" />
          <button type="button" onClick={() => void copy()} className="action-secondary w-full">
            {copied ? "복사했어요" : "링크 복사"}
          </button>
        </div>
      ) : (
        <button
          type="button"
          onClick={() => inviteMutation.mutate()}
          disabled={inviteMutation.isPending}
          className="action-secondary w-full"
        >
          초대 링크 만들기
        </button>
      )}
      {inviteMutation.error ? (
        <p role="alert" className="m-0 text-danger">
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
    <section className="panel flex flex-col gap-3 p-8" aria-live="polite">
      <ImportSteps current={3} />
      <span className="num mt-3 text-xs font-semibold text-accent-text">분석 중</span>
      <h1 className="font-display m-0 text-2xl">
        {view.status === "submitted" ? "분석 순서를 기다리고 있습니다" : (STAGE_LABELS[stage] ?? "분석 중입니다")}
      </h1>
      <p className="section-copy m-0">메시지 {view.messageCount.toLocaleString()}개를 읽고 있습니다. 이 화면을 열어 두면 끝나는 대로 보여 드립니다.</p>
      {percent !== null ? (
        <div className="mt-2">
          <div
            role="progressbar"
            aria-valuenow={percent}
            aria-valuemin={0}
            aria-valuemax={100}
            className="h-2 overflow-hidden rounded-full bg-line"
          >
            <div className="h-full bg-accent transition-[width]" style={{ width: `${percent}%` }} />
          </div>
          <p className="num mt-2 text-xs text-muted">
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
  const kind = [item.decisionType ? decisionTypeLabel(item.decisionType) : KIND_LABELS[item.kind], item.status ? DECISION_STATUS_LABELS[item.status] ?? item.status : null]
    .filter(Boolean)
    .join(" · ");

  return (
    <li
      className={`flex flex-col gap-2.5 rounded-2xl p-4 transition ${
        checked ? "bg-surface ring-2 ring-accent" : "bg-surface ring-1 ring-line"
      } ${alreadyApplied ? "opacity-70" : ""}`}
    >
      <div className="flex items-start gap-2.5">
        {selectable ? (
          <input
            type="checkbox"
            aria-label={`${headline} 반영`}
            checked={checked}
            onChange={onToggle}
            className="mt-0.5 h-5 w-5 flex-none accent-[rgb(var(--accent))]"
          />
        ) : null}
        <div className="min-w-0 flex-1">
          <span className="num block text-[11px] font-semibold text-muted">
            {kind}
            {alreadyApplied ? " · 반영됨" : ""}
          </span>
          <h3 className="m-0 mt-0.5 text-base font-bold">{headline}</h3>
          {item.options && item.options.length > 0 && item.status !== "likely_decided" ? (
            <ul className="m-0 mt-1 flex list-none flex-wrap gap-x-3 gap-y-1 p-0 text-[13px]">
              {item.options.map((option) => (
                <li key={option.label} className={option.superseded ? "text-muted line-through" : "text-ink/80"}>
                  {option.label} · 찬성 {option.supporters} · 반대 {option.objectors}
                </li>
              ))}
            </ul>
          ) : null}
        </div>
      </div>
      {quotes.length > 0 ? (
        <ul className="m-0 flex list-none flex-col gap-1.5 rounded-xl bg-surface-2 px-3 py-2.5 text-[13px] leading-normal">
          {quotes.map(({ entry, message }) => (
            <li key={`${entry.messageId}-${entry.role}`} className="flex gap-2 text-ink/80">
              <span className="num flex-none text-xs text-muted">{EVIDENCE_ROLE_LABELS[entry.role] ?? entry.role}</span>
              <span className="min-w-0">
                <b className="text-ink">{message.speaker.displayName}</b>{" "}
                <span className="whitespace-pre-wrap">{message.body}</span>
              </span>
            </li>
          ))}
        </ul>
      ) : null}
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

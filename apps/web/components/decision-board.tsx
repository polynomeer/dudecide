"use client";

import Link from "next/link";
import type { Route } from "next";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useEffect, useMemo, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiErrorPanel } from "@/components/api-error-panel";
import { LogoMark } from "@/components/brand/logo";
import { HostOnlyNotice } from "@/components/host-only-notice";
import {
  type AddDecisionEvidenceRequest,
  apiClient,
  type ConfirmDecisionRequest,
  type CreateDecisionRequest,
  type DecisionDetailResponse,
  type DecisionEvidenceView,
  type DecisionHistoryView,
  type MeetingDetailResponse,
  type MessageView,
  type ReconsiderDecisionRequest,
} from "@/lib/api/client";
import { getApiErrorMessage } from "@/lib/api/error-display";
import {
  avatarColor,
  decisionStatusChip,
  decisionStatusLabel,
  decisionTypeLabel,
  formatShortDateTime,
  initialOf,
  normalizeDecisionStatus,
} from "@/lib/display";
import { useCurrentActor } from "@/lib/session";

const DECISION_TYPES = ["date", "location", "lodging", "transport", "budget", "task", "other"] as const;
const EVIDENCE_TYPES = [
  { value: "support", label: "찬성" },
  { value: "opposition", label: "반대" },
  { value: "condition", label: "조건" },
  { value: "proposal", label: "제안" },
] as const;

const HISTORY_LABELS: Record<string, string> = {
  created: "결정을 만들었어요",
  confirmed: "확정했어요",
  reconsidered: "재검토로 돌렸어요",
  reconsider: "재검토로 돌렸어요",
  evidence_added: "근거를 연결했어요",
  status_changed: "상태가 바뀌었어요",
};

function isObjection(evidence: DecisionEvidenceView) {
  return evidence.evidenceType.toLowerCase() === "opposition";
}

function summaryOf(result: Record<string, unknown>) {
  return typeof result.summary === "string" && result.summary.trim() ? result.summary : null;
}

export function DecisionBoard({ meetingId }: { meetingId: string }) {
  const queryClient = useQueryClient();
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const actorQuery = useCurrentActor();
  const meetingQuery = useQuery({
    queryKey: ["meeting", meetingId],
    queryFn: () => apiClient.getMeeting(meetingId),
  });
  const decisionsQuery = useQuery({
    queryKey: ["meeting-decisions", meetingId],
    queryFn: () => apiClient.listDecisions(meetingId),
  });
  const [creating, setCreating] = useState(false);

  const requestedId = searchParams?.get("decision") ?? null;
  const decisions = decisionsQuery.data ?? [];
  const selectedDecisionId =
    (requestedId && decisions.some((decision) => decision.id === requestedId) ? requestedId : decisions[0]?.id) ?? null;

  const detailQuery = useQuery({
    queryKey: ["decision-detail", selectedDecisionId],
    queryFn: () => apiClient.getDecision(selectedDecisionId!),
    enabled: Boolean(selectedDecisionId),
  });

  function selectDecision(decisionId: string) {
    setCreating(false);
    router.replace(`${pathname}?decision=${decisionId}` as Route, { scroll: false });
  }

  if (meetingQuery.isLoading || decisionsQuery.isLoading) {
    return <div className="panel px-6 py-6 text-sm text-muted">결정 보드를 불러오는 중이에요…</div>;
  }

  if (meetingQuery.error || decisionsQuery.error || !meetingQuery.data) {
    return <ApiErrorPanel error={meetingQuery.error ?? decisionsQuery.error} resourceName="결정 보드" />;
  }

  const meeting = meetingQuery.data;
  const isHost = actorQuery.data?.userId === meeting.ownerUserId;
  const selected = decisions.find((decision) => decision.id === selectedDecisionId) ?? null;

  return (
    <>
      <nav aria-label="현재 위치" className="flex flex-wrap items-center gap-2 text-sm">
        <Link href={`/meetings/${meetingId}` as Route} className="inline-flex min-h-[44px] items-center text-muted hover:text-ink">
          {meeting.title}
        </Link>
        <span aria-hidden="true" className="text-muted/60">
          /
        </span>
        <span className={selected ? "text-muted" : "font-bold"}>결정 보드</span>
        {selected ? (
          <>
            <span aria-hidden="true" className="text-muted/60">
              /
            </span>
            <b aria-current="page">{decisionTypeLabel(selected.decisionType)}</b>
          </>
        ) : null}
        <Link href={`/meetings/${meetingId}` as Route} className="link-accent ml-auto inline-flex min-h-[44px] items-center text-sm">
          ← 대시보드
        </Link>
      </nav>

      <div className="flex flex-wrap items-start gap-5">
        <aside aria-label="결정 목록" className="panel flex w-full min-w-0 flex-col gap-0.5 p-2.5 lg:w-auto lg:max-w-[300px] lg:flex-[1_1_240px]">
          <div className="flex items-center justify-between px-3.5 pb-1 pt-2">
            <span className="mono-label">결정 {decisions.length}건</span>
            {isHost ? (
              <button type="button" onClick={() => setCreating(true)} className="link-accent text-xs">
                + 새 결정
              </button>
            ) : null}
          </div>
          <div className="flex gap-1 overflow-x-auto [scrollbar-width:none] lg:flex-col lg:gap-0.5 lg:overflow-visible [&::-webkit-scrollbar]:hidden">
          {decisions.map((decision) => {
            const active = decision.id === selectedDecisionId && !creating;
            const status = normalizeDecisionStatus(decision.status);
            return (
              <button
                key={decision.id}
                type="button"
                aria-current={active ? "true" : undefined}
                onClick={() => selectDecision(decision.id)}
                className={`flex w-[168px] flex-none flex-col gap-1 rounded-[14px] px-3.5 py-3 text-left transition lg:w-auto ${
                  active ? "bg-accent-soft" : "hover:bg-surface-2"
                } ${status === "on_hold" ? "text-muted" : "text-ink"}`}
              >
                <span
                  className={`text-[11px] font-semibold ${
                    active ? "text-accent-soft-ink" : status === "needs_reconsideration" ? "text-risk-ink" : "text-muted"
                  }`}
                >
                  {decisionTypeLabel(decision.decisionType)} · {decisionStatusLabel(decision.status)}
                </span>
                <b className="text-sm">{decision.title}</b>
              </button>
            );
          })}
          </div>
          {decisions.length === 0 ? (
            <p className="section-copy px-3.5 py-3">아직 결정이 없어요.</p>
          ) : null}
        </aside>

        {creating || !selected ? (
          <div className="flex min-w-0 flex-[999_1_520px] flex-col gap-4">
            {isHost ? (
              <CreateDecisionPanel
                meeting={meeting}
                onCancel={decisions.length ? () => setCreating(false) : undefined}
                onCreated={async (decisionId) => {
                  await invalidateDecisionQueries(queryClient, meetingId, decisionId);
                  selectDecision(decisionId);
                }}
              />
            ) : (
              <div className="panel p-7">
                <HostOnlyNotice message="아직 결정이 없어요. 결정은 호스트가 만들거나 대화 가져오기로 찾을 수 있어요." />
              </div>
            )}
          </div>
        ) : detailQuery.isLoading ? (
          <div className="panel flex-[999_1_520px] p-7 text-sm text-muted">결정을 불러오는 중이에요…</div>
        ) : detailQuery.error || !detailQuery.data ? (
          <div className="min-w-0 flex-[999_1_520px]">
            <ApiErrorPanel error={detailQuery.error} resourceName="결정 상세" />
          </div>
        ) : (
          <DecisionWorkspace
            key={detailQuery.data.id}
            meeting={meeting}
            decision={detailQuery.data}
            canManage={isHost}
            onMutated={(decisionId) => invalidateDecisionQueries(queryClient, meetingId, decisionId)}
          />
        )}
      </div>
    </>
  );
}

function DecisionWorkspace({
  meeting,
  decision,
  canManage,
  onMutated,
}: {
  meeting: MeetingDetailResponse;
  decision: DecisionDetailResponse;
  canManage: boolean;
  onMutated: (decisionId: string) => Promise<void>;
}) {
  const threadMessagesQuery = useQuery({
    queryKey: ["decision-thread-messages", decision.threadId],
    queryFn: () => apiClient.listMessages(decision.threadId, undefined, 50),
  });
  const supports = decision.evidence.filter((item) => !isObjection(item));
  const objections = decision.evidence.filter(isObjection);
  const [focusedMessageId, setFocusedMessageId] = useState<string | null>(supports[0]?.messageId ?? null);
  const messagesById = useMemo(
    () => new Map((threadMessagesQuery.data ?? []).map((message) => [message.id, message])),
    [threadMessagesQuery.data],
  );
  const thread = meeting.threads.find((item) => item.id === decision.threadId);
  const status = normalizeDecisionStatus(decision.status);
  const confirmed = status === "confirmed";
  const summary = summaryOf(confirmed ? decision.confirmedResult : decision.proposedResult);

  return (
    <>
      <section
        className={`panel flex min-w-0 flex-[999_1_520px] flex-col gap-[22px] p-7 ring-2 ${confirmed ? "ring-ink" : status === "proposed" ? "ring-accent" : "ring-transparent"}`}
      >
        <div className="flex flex-wrap items-start justify-between gap-2.5">
          <div className="flex flex-col gap-1.5">
            <span className="num text-xs font-semibold text-muted">
              {decisionTypeLabel(decision.decisionType)}
              {thread ? ` · # ${thread.title}` : ""}
              {decision.decidedAt ? ` · ${formatShortDateTime(decision.decidedAt)} 확정` : ""}
            </span>
            <h1 className="font-display m-0 text-[clamp(1.7rem,2.6vw,2.2rem)] leading-[1.2]">{decision.title}</h1>
          </div>
          <span className={`${decisionStatusChip(decision.status)} !px-2.5 !py-1.5 !text-xs`}>
            {decisionStatusLabel(decision.status)}
          </span>
        </div>

        <div className="grid grid-cols-[repeat(auto-fit,minmax(160px,1fr))] gap-2.5">
          <div className="rounded-[14px] bg-surface-2 px-3.5 py-3">
            <span className="text-[11px] font-semibold text-muted">근거</span>
            <b className="num block text-xl">{supports.length}개</b>
          </div>
          <div className="rounded-[14px] bg-risk-soft px-3.5 py-3 text-risk-ink">
            <span className="text-[11px] font-semibold">반대</span>
            <b className="num block text-xl">{objections.length}개</b>
          </div>
          {summary ? (
            <div className="rounded-[14px] bg-surface-2 px-3.5 py-3 sm:col-span-1">
              <span className="text-[11px] font-semibold text-muted">{confirmed ? "확정 내용" : "제안 내용"}</span>
              <b className="block text-sm leading-snug">{summary}</b>
            </div>
          ) : null}
        </div>

        <section aria-label="근거 메시지" className="flex flex-col gap-2">
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <h2 className="m-0 text-[15px] font-bold">근거 메시지 {supports.length}</h2>
            {decision.evidence.length ? (
              <span className="text-xs text-muted">누르면 원래 대화에서 위치를 보여줘요</span>
            ) : null}
          </div>
          {supports.map((item) => {
            const pressed = focusedMessageId === item.messageId;
            const source = messagesById.get(item.messageId);
            return (
              <button
                key={item.id}
                type="button"
                aria-pressed={pressed}
                onClick={() => setFocusedMessageId(item.messageId)}
                className={`flex w-full items-start gap-2.5 rounded-[14px] border px-3.5 py-3 text-left text-ink transition ${
                  pressed ? "border-accent/50 bg-accent-soft" : "border-transparent bg-surface-2 hover:border-line"
                }`}
              >
                <span className="num w-11 flex-none text-xs text-muted">{timeOf(source?.createdAt ?? item.createdAt)}</span>
                <span className="min-w-0 flex-1 text-sm leading-normal">
                  {source ? <b>{source.speaker.displayName} </b> : null}
                  {quoteBody(item.quoteSnapshot, source?.speaker.displayName)}
                </span>
                <span className="flex-none text-xs font-bold text-accent-text">원문 →</span>
              </button>
            );
          })}
          {objections.map((item) => (
            <button
              key={item.id}
              type="button"
              aria-pressed={focusedMessageId === item.messageId}
              onClick={() => setFocusedMessageId(item.messageId)}
              className="flex gap-2.5 rounded-[14px] border border-risk-ink/25 bg-risk-soft px-3.5 py-3 text-left text-sm leading-normal text-risk-ink"
            >
              <span className="num flex-none pt-0.5 text-xs font-semibold">반대</span>
              <span>
                {messagesById.get(item.messageId) ? <b>{messagesById.get(item.messageId)!.speaker.displayName} </b> : null}
                {quoteBody(item.quoteSnapshot, messagesById.get(item.messageId)?.speaker.displayName)}
              </span>
            </button>
          ))}
          {decision.evidence.length === 0 ? (
            <div className="empty-state">아직 연결된 근거가 없어요.</div>
          ) : null}
        </section>

        {canManage ? (
          <DecisionActions decision={decision} messages={threadMessagesQuery.data ?? []} onMutated={onMutated} />
        ) : (
          <p className="m-0 text-xs text-muted">확정과 재검토는 호스트·공동 호스트만 할 수 있어요.</p>
        )}

        <section aria-label="변경 이력" className="border-t border-line pt-3.5">
          <h2 className="m-0 mb-1 text-[15px] font-bold">변경 이력</h2>
          {decision.history.length ? (
            decision.history.map((entry) => <HistoryRow key={entry.id} entry={entry} />)
          ) : (
            <p className="section-copy m-0">기록된 이력이 없어요.</p>
          )}
        </section>
      </section>

      <SourceThread
        title={thread?.title ?? "원래 대화"}
        meetingId={meeting.id}
        threadId={decision.threadId}
        query={threadMessagesQuery}
        evidence={decision.evidence}
        focusedMessageId={focusedMessageId}
      />
    </>
  );
}

function SourceThread({
  title,
  meetingId,
  threadId,
  query,
  evidence,
  focusedMessageId,
}: {
  title: string;
  meetingId: string;
  threadId: string;
  query: { data?: MessageView[]; isLoading: boolean; error: unknown };
  evidence: DecisionEvidenceView[];
  focusedMessageId: string | null;
}) {
  const refs = useRef(new Map<string, HTMLDivElement>());
  const evidenceByMessage = useMemo(() => new Map(evidence.map((item) => [item.messageId, item])), [evidence]);
  const messages = query.data ?? [];

  useEffect(() => {
    if (focusedMessageId) {
      refs.current.get(focusedMessageId)?.scrollIntoView({ block: "nearest", behavior: "smooth" });
    }
  }, [focusedMessageId, messages.length]);

  return (
    <aside aria-label="원래 대화" className="panel flex max-h-[calc(100vh-140px)] max-w-full flex-[1_1_320px] flex-col gap-1.5 overflow-y-auto p-[18px] lg:sticky lg:top-6 lg:max-w-[420px]">
      <div className="flex items-baseline justify-between px-1 pb-2">
        <h2 className="m-0 text-[15px] font-bold"># {title} · 원래 대화</h2>
        <Link href={`/meetings/${meetingId}/threads/${threadId}` as Route} className="link-accent text-xs">
          토픽 열기
        </Link>
      </div>
      {query.isLoading ? <p className="section-copy px-1">대화를 불러오는 중이에요…</p> : null}
      {query.error ? <p className="px-1 text-sm text-danger">대화를 불러오지 못했어요.</p> : null}
      {messages.map((message) => {
        const linked = evidenceByMessage.get(message.id);
        const focused = focusedMessageId === message.id;
        const tone = focused
          ? "bg-accent-soft shadow-[inset_0_0_0_2px_rgb(var(--accent))]"
          : linked
            ? isObjection(linked)
              ? "bg-risk-soft"
              : "bg-surface-2"
            : "";
        const tag = message.origin === "imported" ? "· 단톡방" : linked ? (isObjection(linked) ? "· 반대" : "· 근거") : "";
        return (
          <div
            key={message.id}
            ref={(node) => {
              if (node) refs.current.set(message.id, node);
              else refs.current.delete(message.id);
            }}
            className={`flex gap-2.5 rounded-xl px-2.5 py-2 ${tone}`}
          >
            <span
              className="flex h-[30px] w-[30px] flex-none items-center justify-center rounded-full text-xs font-bold text-[#15171C]"
              style={{ background: avatarColor(message.speaker.displayName) }}
            >
              {initialOf(message.speaker.displayName)}
            </span>
            <span className="min-w-0 flex-1 text-[13px] leading-normal">
              <span className="block text-[11px] font-bold text-muted">
                {message.speaker.displayName} · {timeOf(message.createdAt)} {tag}
              </span>
              {message.body}
            </span>
          </div>
        );
      })}
      {!query.isLoading && !query.error && messages.length === 0 ? (
        <p className="section-copy px-1">이 토픽에는 아직 메시지가 없어요.</p>
      ) : null}
    </aside>
  );
}

type ActionMode = "confirm" | "reconsider" | "evidence" | null;

function DecisionActions({
  decision,
  messages,
  onMutated,
}: {
  decision: DecisionDetailResponse;
  messages: MessageView[];
  onMutated: (decisionId: string) => Promise<void>;
}) {
  const status = normalizeDecisionStatus(decision.status);
  const [mode, setMode] = useState<ActionMode>(null);
  const [reason, setReason] = useState("");
  const [resultText, setResultText] = useState(() =>
    JSON.stringify(Object.keys(decision.proposedResult).length ? decision.proposedResult : { summary: decision.title }, null, 2),
  );
  const [evidenceForm, setEvidenceForm] = useState({ messageId: "", evidenceType: "support" });
  const [actionError, setActionError] = useState<string | null>(null);

  const handleError = (resourceName: string, conflictMessage: string) => (error: unknown) =>
    setActionError(getApiErrorMessage(error, { resourceName, conflictMessage }));
  const handleSuccess = async () => {
    setActionError(null);
    setMode(null);
    setReason("");
    await onMutated(decision.id);
  };

  const confirmMutation = useMutation({
    mutationFn: (body: ConfirmDecisionRequest) => apiClient.confirmDecision(decision.id, body),
    onSuccess: handleSuccess,
    onError: handleError("결정 확정", "현재 결정 상태에서는 확정을 진행할 수 없습니다."),
  });
  const reconsiderMutation = useMutation({
    mutationFn: (body: ReconsiderDecisionRequest) => apiClient.reconsiderDecision(decision.id, body),
    onSuccess: handleSuccess,
    onError: handleError("재검토 요청", "현재 결정 상태에서는 재검토 요청을 보낼 수 없습니다."),
  });
  const evidenceMutation = useMutation({
    mutationFn: (body: AddDecisionEvidenceRequest) => apiClient.addDecisionEvidence(decision.id, body),
    onSuccess: handleSuccess,
    onError: handleError("근거 추가", "현재 결정 상태에서는 근거를 추가할 수 없습니다."),
  });

  function submitConfirm(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    let confirmedResult: Record<string, unknown>;
    try {
      confirmedResult = parseJsonObject(resultText);
    } catch (error) {
      setActionError(error instanceof Error ? error.message : "확정 내용 JSON 형식이 올바르지 않습니다.");
      return;
    }
    confirmMutation.mutate({ confirmedResult, reason: reason.trim() || undefined });
  }

  function submitReconsider(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    reconsiderMutation.mutate({ reason: reason.trim() });
  }

  function submitEvidence(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const message = messages.find((item) => item.id === evidenceForm.messageId);
    if (!message) {
      setActionError("근거로 연결할 메시지를 골라 주세요.");
      return;
    }
    evidenceMutation.mutate({
      messageId: message.id,
      evidenceType: evidenceForm.evidenceType,
      quoteSnapshot: message.body,
    });
  }

  function open(next: ActionMode) {
    setActionError(null);
    setReason("");
    setMode((current) => (current === next ? null : next));
  }

  return (
    <div className="flex flex-col gap-3">
      {status === "confirmed" ? (
        <div className="flex flex-wrap items-center justify-between gap-2.5 rounded-[14px] bg-surface-2 px-3.5 py-3 text-sm">
          <span>
            확정된 결정이에요. 근거 {decision.evidence.filter((item) => !isObjection(item)).length}개와 반대{" "}
            {decision.evidence.filter(isObjection).length}개가 함께 기록됐어요.
          </span>
          <button type="button" onClick={() => open("reconsider")} className="link-accent min-h-[44px] px-2">
            다시 정하기
          </button>
        </div>
      ) : (
        <div className="flex flex-wrap items-center gap-2">
          <button type="button" onClick={() => open("confirm")} className="action-primary min-h-[48px] px-[22px] text-[15px]">
            확정하기
          </button>
          <button type="button" onClick={() => open("evidence")} className="action-secondary">
            근거 추가
          </button>
          <button type="button" onClick={() => open("reconsider")} className="action-secondary">
            재검토 요청
          </button>
        </div>
      )}

      {mode === "confirm" ? (
        <form onSubmit={submitConfirm} className="inset-card flex flex-col gap-3">
          <label className="flex flex-col gap-1.5 text-sm font-semibold">
            확정 메모 (선택)
            <input
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              className="field"
              placeholder="예: 7/8 응답 후 조기 마감"
            />
          </label>
          <details>
            <summary className="cursor-pointer text-xs font-semibold text-muted">확정 내용 직접 편집 (JSON)</summary>
            <textarea
              value={resultText}
              onChange={(event) => setResultText(event.target.value)}
              className="mono-panel mt-2 min-h-[140px] w-full border border-line"
              spellCheck={false}
            />
          </details>
          <div className="flex gap-2">
            <button type="submit" disabled={confirmMutation.isPending} className="action-primary">
              {confirmMutation.isPending ? "확정 중…" : "이대로 확정"}
            </button>
            <button type="button" onClick={() => setMode(null)} className="btn-ghost">
              취소
            </button>
          </div>
        </form>
      ) : null}

      {mode === "reconsider" ? (
        <form onSubmit={submitReconsider} className="flex flex-col gap-3 rounded-xl bg-risk-soft p-4 text-risk-ink">
          <label className="flex flex-col gap-1.5 text-sm font-semibold">
            왜 다시 정해야 하나요?
            <textarea
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              className="field min-h-[96px]"
              placeholder="예: 항공편 시간이 바뀌었어요"
              required
            />
          </label>
          <div className="flex gap-2">
            <button type="submit" disabled={reconsiderMutation.isPending} className="action-primary">
              {reconsiderMutation.isPending ? "바꾸는 중…" : "재검토로 돌리기"}
            </button>
            <button type="button" onClick={() => setMode(null)} className="btn-ghost">
              취소
            </button>
          </div>
        </form>
      ) : null}

      {mode === "evidence" ? (
        <form onSubmit={submitEvidence} className="inset-card flex flex-col gap-3">
          <label className="flex flex-col gap-1.5 text-sm font-semibold">
            근거 메시지
            <select
              value={evidenceForm.messageId}
              onChange={(event) => setEvidenceForm((current) => ({ ...current, messageId: event.target.value }))}
              className="field"
              required
            >
              <option value="">메시지를 골라 주세요</option>
              {messages.map((message) => (
                <option key={message.id} value={message.id}>
                  {timeOf(message.createdAt)} {message.speaker.displayName} · {truncateText(message.body, 60)}
                </option>
              ))}
            </select>
          </label>
          <fieldset className="flex flex-wrap gap-2">
            <legend className="mb-1.5 text-sm font-semibold">종류</legend>
            {EVIDENCE_TYPES.map((type) => (
              <label key={type.value} className="chip cursor-pointer has-[:checked]:border-accent has-[:checked]:text-accent-text">
                <input
                  type="radio"
                  name="evidence-type"
                  value={type.value}
                  checked={evidenceForm.evidenceType === type.value}
                  onChange={() => setEvidenceForm((current) => ({ ...current, evidenceType: type.value }))}
                  className="sr-only"
                />
                {type.label}
              </label>
            ))}
          </fieldset>
          <div className="flex gap-2">
            <button type="submit" disabled={evidenceMutation.isPending || messages.length === 0} className="action-primary">
              {evidenceMutation.isPending ? "연결 중…" : "근거 연결"}
            </button>
            <button type="button" onClick={() => setMode(null)} className="btn-ghost">
              취소
            </button>
          </div>
        </form>
      ) : null}

      {actionError ? (
        <p role="alert" className="m-0 text-sm text-danger">
          {actionError}
        </p>
      ) : null}
    </div>
  );
}

function CreateDecisionPanel({
  meeting,
  onCancel,
  onCreated,
}: {
  meeting: MeetingDetailResponse;
  onCancel?: () => void;
  onCreated: (decisionId: string) => Promise<void>;
}) {
  const [form, setForm] = useState({
    threadId: meeting.threads[0]?.id ?? "",
    decisionType: "other",
    title: "",
    summary: "",
  });
  const [error, setError] = useState<string | null>(null);
  const createMutation = useMutation({
    mutationFn: (body: CreateDecisionRequest) => apiClient.createDecision(meeting.id, body),
    onSuccess: async (created) => {
      setError(null);
      await onCreated(created.id);
    },
    onError: (mutationError: unknown) =>
      setError(
        getApiErrorMessage(mutationError, {
          resourceName: "결정 생성",
          conflictMessage: "현재 회의 또는 스레드 상태에서는 결정을 생성할 수 없습니다.",
        }),
      ),
  });

  function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    createMutation.mutate({
      threadId: form.threadId,
      decisionType: form.decisionType,
      title: form.title.trim(),
      proposedResult: form.summary.trim() ? { summary: form.summary.trim() } : {},
    });
  }

  return (
    <form onSubmit={submit} className="panel flex flex-col gap-4 p-7">
      <div className="flex items-center gap-2">
        <LogoMark size={22} />
        <h1 className="font-display m-0 text-2xl">새 결정 만들기</h1>
      </div>
      <p className="section-copy m-0">토픽을 고르고 무엇을 정할지 적어 주세요. 근거는 만든 뒤에 연결할 수 있어요.</p>
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="flex flex-col gap-1.5 text-sm font-semibold">
          토픽
          <select
            value={form.threadId}
            onChange={(event) => setForm((current) => ({ ...current, threadId: event.target.value }))}
            className="field"
          >
            {meeting.threads.map((thread) => (
              <option key={thread.id} value={thread.id}>
                # {thread.title}
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1.5 text-sm font-semibold">
          종류
          <select
            value={form.decisionType}
            onChange={(event) => setForm((current) => ({ ...current, decisionType: event.target.value }))}
            className="field"
          >
            {DECISION_TYPES.map((type) => (
              <option key={type} value={type}>
                {decisionTypeLabel(type)}
              </option>
            ))}
          </select>
        </label>
      </div>
      <label className="flex flex-col gap-1.5 text-sm font-semibold">
        제목
        <input
          value={form.title}
          onChange={(event) => setForm((current) => ({ ...current, title: event.target.value }))}
          className="field"
          placeholder="예: 애월 독채"
          required
        />
      </label>
      <label className="flex flex-col gap-1.5 text-sm font-semibold">
        제안 내용 (선택)
        <textarea
          value={form.summary}
          onChange={(event) => setForm((current) => ({ ...current, summary: event.target.value }))}
          className="field min-h-[88px]"
          placeholder="예: 6인실 + 넓은 거실, 1박 1인 6만원대"
        />
      </label>
      {error ? (
        <p role="alert" className="m-0 text-sm text-danger">
          {error}
        </p>
      ) : null}
      <div className="flex gap-2">
        <button type="submit" disabled={createMutation.isPending || meeting.threads.length === 0} className="action-primary">
          {createMutation.isPending ? "만드는 중…" : "결정 만들기"}
        </button>
        {onCancel ? (
          <button type="button" onClick={onCancel} className="btn-ghost">
            취소
          </button>
        ) : null}
      </div>
    </form>
  );
}

function HistoryRow({ entry }: { entry: DecisionHistoryView }) {
  return (
    <div className="flex gap-2.5 py-2 text-[13px] leading-[1.45]">
      <span className="num w-20 flex-none text-xs text-muted">{formatShortDateTime(entry.createdAt)}</span>
      <span>
        {HISTORY_LABELS[entry.action.toLowerCase()] ?? entry.action}
        {entry.reason ? <span className="block text-muted">{entry.reason}</span> : null}
      </span>
    </div>
  );
}

// Imported and suggested evidence snapshots read "Name: body"; drop the name when it is already shown.
function quoteBody(snapshot: string, speaker?: string) {
  return speaker && snapshot.startsWith(`${speaker}: `) ? snapshot.slice(speaker.length + 2) : snapshot;
}

function timeOf(value?: string | null) {
  if (!value) {
    return "";
  }
  return new Intl.DateTimeFormat("ko-KR", { hour: "2-digit", minute: "2-digit", hour12: false }).format(new Date(value));
}

function truncateText(value: string, limit: number) {
  return value.length <= limit ? value : `${value.slice(0, limit - 1)}…`;
}

function parseJsonObject(value: string): Record<string, unknown> {
  const parsed: unknown = JSON.parse(value);
  if (!parsed || Array.isArray(parsed) || typeof parsed !== "object") {
    throw new Error("JSON은 객체 형태여야 합니다.");
  }
  return parsed as Record<string, unknown>;
}

async function invalidateDecisionQueries(
  queryClient: ReturnType<typeof useQueryClient>,
  meetingId: string,
  decisionId: string,
) {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: ["meeting-decisions", meetingId] }),
    queryClient.invalidateQueries({ queryKey: ["decision-detail", decisionId] }),
    queryClient.invalidateQueries({ queryKey: ["meeting", meetingId] }),
  ]);
}

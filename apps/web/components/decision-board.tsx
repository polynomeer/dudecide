"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiErrorPanel } from "@/components/api-error-panel";
import { HostOnlyNotice } from "@/components/host-only-notice";
import {
  type AddDecisionEvidenceRequest,
  apiClient,
  type ConfirmDecisionRequest,
  type CreateDecisionRequest,
  type DecisionDetailResponse,
  type DecisionHistoryView,
  type DecisionResponse,
  type ReconsiderDecisionRequest,
} from "@/lib/api/client";
import { getApiErrorMessage } from "@/lib/api/error-display";
import { useCurrentActor } from "@/lib/session";

const DEFAULT_PROPOSED_RESULT = '{\n  "summary": ""\n}';
const DEFAULT_CONFIRMED_RESULT = '{\n  "summary": ""\n}';

export function DecisionBoard({ meetingId }: { meetingId: string }) {
  const queryClient = useQueryClient();
  const actorQuery = useCurrentActor();
  const meetingQuery = useQuery({
    queryKey: ["meeting", meetingId],
    queryFn: () => apiClient.getMeeting(meetingId),
  });
  const decisionsQuery = useQuery({
    queryKey: ["meeting-decisions", meetingId],
    queryFn: () => apiClient.listDecisions(meetingId),
  });
  const [selectedDecisionId, setSelectedDecisionId] = useState<string | null>(null);
  const [createForm, setCreateForm] = useState({
    threadId: "",
    decisionType: "CONSENSUS",
    title: "",
    proposedResultText: DEFAULT_PROPOSED_RESULT,
  });
  const [createError, setCreateError] = useState<string | null>(null);

  useEffect(() => {
    if (!selectedDecisionId && decisionsQuery.data && decisionsQuery.data.length > 0) {
      setSelectedDecisionId(decisionsQuery.data[0].id);
    }
  }, [decisionsQuery.data, selectedDecisionId]);

  useEffect(() => {
    if (!createForm.threadId && meetingQuery.data?.threads.length) {
      setCreateForm((current) => ({ ...current, threadId: meetingQuery.data!.threads[0].id }));
    }
  }, [createForm.threadId, meetingQuery.data]);

  const selectedDecision = useMemo(
    () => decisionsQuery.data?.find((item) => item.id === selectedDecisionId) ?? null,
    [decisionsQuery.data, selectedDecisionId],
  );

  const detailQuery = useQuery({
    queryKey: ["decision-detail", selectedDecisionId],
    queryFn: () => apiClient.getDecision(selectedDecisionId!),
    enabled: Boolean(selectedDecisionId),
  });

  const createDecisionMutation = useMutation({
    mutationFn: (body: CreateDecisionRequest) => apiClient.createDecision(meetingId, body),
    onSuccess: async (created) => {
      setCreateError(null);
      setCreateForm((current) => ({
        ...current,
        title: "",
        proposedResultText: DEFAULT_PROPOSED_RESULT,
      }));
      setSelectedDecisionId(created.id);
      await invalidateDecisionQueries(queryClient, meetingId, created.id);
    },
    onError: (error: Error) => {
      setCreateError(
        getApiErrorMessage(error, {
          resourceName: "결정 생성",
          conflictMessage: "현재 회의 또는 스레드 상태에서는 결정을 생성할 수 없습니다.",
        }),
      );
    },
  });

  if (meetingQuery.isLoading || decisionsQuery.isLoading) {
    return <div className="panel px-6 py-6 text-sm text-ink/60">결정 보드를 불러오는 중입니다.</div>;
  }

  if (meetingQuery.error || decisionsQuery.error || !meetingQuery.data) {
    return <ApiErrorPanel error={meetingQuery.error ?? decisionsQuery.error} resourceName="결정 보드" />;
  }

  function handleCreateDecisionSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setCreateError(null);

    let proposedResult: Record<string, unknown>;
    try {
      proposedResult = parseJsonObject(createForm.proposedResultText);
    } catch (error) {
      setCreateError(error instanceof Error ? error.message : "제안 결과 JSON 형식이 올바르지 않습니다.");
      return;
    }

    createDecisionMutation.mutate({
      threadId: createForm.threadId,
      decisionType: createForm.decisionType,
      title: createForm.title.trim(),
      proposedResult,
    });
  }

  const isHost = actorQuery.data?.userId === meetingQuery.data.ownerUserId;

  return (
    <div className="space-y-5">
      <section className="panel px-6 py-6">
        <div className="flex items-start justify-between gap-4">
          <div>
            <div className="eyebrow">Create decision</div>
            <h2 className="mt-2 font-[var(--font-display)] text-3xl font-semibold tracking-[-0.05em]">
              새 결정 초안 생성
            </h2>
            <p className="mt-3 max-w-2xl text-sm leading-6 text-ink/66">
              스레드 단위로 결정을 생성하고, 제안 결과를 JSON으로 남길 수 있습니다. 확정과 재검토는
              아래 상세 패널에서 이어서 처리합니다.
            </p>
          </div>
          <div className="rounded-full border border-[color:var(--color-line)] bg-white/70 px-4 py-2 text-sm">
            스레드 {meetingQuery.data.threads.length}개
          </div>
        </div>

        {isHost ? (
          <form className="mt-5 grid gap-4 lg:grid-cols-[1.1fr_0.9fr]" onSubmit={handleCreateDecisionSubmit}>
            <div className="space-y-4">
              <label className="block">
                <span className="text-xs font-semibold uppercase tracking-[0.16em] text-ink/45">
                  Thread
                </span>
                <select
                  value={createForm.threadId}
                  onChange={(event) =>
                    setCreateForm((current) => ({ ...current, threadId: event.target.value }))
                  }
                  className="mt-2 w-full rounded-[1.2rem] border border-[color:var(--color-line)] bg-white/85 px-4 py-3 text-sm outline-none transition focus:border-pine"
                >
                  {meetingQuery.data.threads.map((thread) => (
                    <option key={thread.id} value={thread.id}>
                      {thread.title} ({thread.category})
                    </option>
                  ))}
                </select>
              </label>

              <div className="grid gap-4 md:grid-cols-[0.7fr_1.3fr]">
                <label className="block">
                  <span className="text-xs font-semibold uppercase tracking-[0.16em] text-ink/45">
                    Type
                  </span>
                  <input
                    value={createForm.decisionType}
                    onChange={(event) =>
                      setCreateForm((current) => ({ ...current, decisionType: event.target.value }))
                    }
                    className="mt-2 w-full rounded-[1.2rem] border border-[color:var(--color-line)] bg-white/85 px-4 py-3 text-sm outline-none transition focus:border-pine"
                    placeholder="CONSENSUS"
                  />
                </label>

                <label className="block">
                  <span className="text-xs font-semibold uppercase tracking-[0.16em] text-ink/45">
                    Title
                  </span>
                  <input
                    value={createForm.title}
                    onChange={(event) =>
                      setCreateForm((current) => ({ ...current, title: event.target.value }))
                    }
                    className="mt-2 w-full rounded-[1.2rem] border border-[color:var(--color-line)] bg-white/85 px-4 py-3 text-sm outline-none transition focus:border-pine"
                    placeholder="예: 1차 출시 범위 확정"
                    required
                  />
                </label>
              </div>
            </div>

            <label className="block">
              <span className="text-xs font-semibold uppercase tracking-[0.16em] text-ink/45">
                Proposed result JSON
              </span>
              <textarea
                value={createForm.proposedResultText}
                onChange={(event) =>
                  setCreateForm((current) => ({ ...current, proposedResultText: event.target.value }))
                }
                className="mt-2 min-h-[220px] w-full rounded-[1.2rem] border border-[color:var(--color-line)] bg-ink px-4 py-4 font-mono text-xs leading-6 text-mist outline-none transition focus:border-pine"
                spellCheck={false}
              />
            </label>

            <div className="lg:col-span-2 flex flex-wrap items-center justify-between gap-3 rounded-[1.25rem] border border-[color:var(--color-line)] bg-white/58 px-4 py-3">
              <div className="text-sm text-ink/62">
                {createError ? <span className="text-ember">{createError}</span> : "결정 생성 시 목록과 상세 패널이 즉시 갱신됩니다."}
              </div>
              <button
                type="submit"
                disabled={createDecisionMutation.isPending || meetingQuery.data.threads.length === 0}
                className="rounded-full bg-ink px-5 py-3 text-sm font-semibold text-mist transition hover:bg-pine disabled:cursor-not-allowed disabled:bg-ink/40"
              >
                {createDecisionMutation.isPending ? "생성 중..." : "결정 생성"}
              </button>
            </div>
          </form>
        ) : (
          <div className="mt-5">
            <HostOnlyNotice message="현재 프론트엔드에서는 회의 소유자에게만 결정 생성과 상태 전이 액션을 노출합니다." />
          </div>
        )}
      </section>

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
                아직 생성된 결정이 없습니다. 상단 생성 폼에서 첫 결정을 등록해 주세요.
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
              <ApiErrorPanel error={detailQuery.error} resourceName="결정 상세" />
            ) : (
              <DecisionDetailPanel
                meetingId={meetingId}
                decision={detailQuery.data}
                canManage={isHost}
                onMutated={(decisionId) => invalidateDecisionQueries(queryClient, meetingId, decisionId)}
              />
            )
          ) : (
            <div className="mt-4 text-sm text-ink/60">왼쪽 목록에서 확인할 결정을 선택해 주세요.</div>
          )}
        </section>
      </div>
    </div>
  );
}

function DecisionDetailPanel({
  meetingId,
  decision,
  canManage,
  onMutated,
}: {
  meetingId: string;
  decision: DecisionDetailResponse;
  canManage: boolean;
  onMutated: (decisionId: string) => Promise<void>;
}) {
  const threadMessagesQuery = useQuery({
    queryKey: ["decision-thread-messages", decision.threadId],
    queryFn: () => apiClient.listMessages(decision.threadId, undefined, 50),
  });
  const [confirmReason, setConfirmReason] = useState("");
  const [confirmResultText, setConfirmResultText] = useState(
    formatStateText(decision.confirmedResult, DEFAULT_CONFIRMED_RESULT),
  );
  const [reconsiderReason, setReconsiderReason] = useState("");
  const [actionError, setActionError] = useState<string | null>(null);
  const [evidenceForm, setEvidenceForm] = useState({
    messageId: "",
    evidenceType: "MESSAGE_QUOTE",
    quoteSnapshot: "",
    confidence: "0.8",
  });

  useEffect(() => {
    setConfirmResultText(formatStateText(decision.confirmedResult, DEFAULT_CONFIRMED_RESULT));
    setConfirmReason("");
    setReconsiderReason("");
    setActionError(null);
  }, [decision.id, decision.confirmedResult]);

  useEffect(() => {
    const firstMessage = threadMessagesQuery.data?.[0];
    if (firstMessage) {
      setEvidenceForm({
        messageId: firstMessage.id,
        evidenceType: "MESSAGE_QUOTE",
        quoteSnapshot: firstMessage.body,
        confidence: "0.8",
      });
    }
  }, [decision.id, threadMessagesQuery.data]);

  const confirmMutation = useMutation({
    mutationFn: (body: ConfirmDecisionRequest) => apiClient.confirmDecision(decision.id, body),
    onSuccess: async () => {
      setActionError(null);
      await onMutated(decision.id);
    },
    onError: (error: unknown) => {
      setActionError(
        getApiErrorMessage(error, {
          resourceName: "결정 확정",
          conflictMessage: "현재 결정 상태에서는 확정을 진행할 수 없습니다.",
        }),
      );
    },
  });

  const addEvidenceMutation = useMutation({
    mutationFn: (body: AddDecisionEvidenceRequest) => apiClient.addDecisionEvidence(decision.id, body),
    onSuccess: async () => {
      setActionError(null);
      await onMutated(decision.id);
    },
    onError: (error: unknown) => {
      setActionError(
        getApiErrorMessage(error, {
          resourceName: "Evidence 추가",
          conflictMessage: "현재 결정 상태에서는 evidence를 추가할 수 없습니다.",
        }),
      );
    },
  });

  const reconsiderMutation = useMutation({
    mutationFn: (body: ReconsiderDecisionRequest) => apiClient.reconsiderDecision(decision.id, body),
    onSuccess: async () => {
      setActionError(null);
      await onMutated(decision.id);
    },
    onError: (error: unknown) => {
      setActionError(
        getApiErrorMessage(error, {
          resourceName: "재검토 요청",
          conflictMessage: "현재 결정 상태에서는 재검토 요청을 보낼 수 없습니다.",
        }),
      );
    },
  });

  function handleConfirmSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setActionError(null);

    let confirmedResult: Record<string, unknown>;
    try {
      confirmedResult = parseJsonObject(confirmResultText);
    } catch (error) {
      setActionError(error instanceof Error ? error.message : "확정 결과 JSON 형식이 올바르지 않습니다.");
      return;
    }

    confirmMutation.mutate({
      confirmedResult,
      reason: confirmReason.trim() || undefined,
    });
  }

  function handleReconsiderSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setActionError(null);
    reconsiderMutation.mutate({
      reason: reconsiderReason.trim(),
    });
  }

  function handleAddEvidenceSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setActionError(null);

    const confidence = evidenceForm.confidence.trim()
      ? Number.parseFloat(evidenceForm.confidence)
      : undefined;

    if (confidence !== undefined && Number.isNaN(confidence)) {
      setActionError("신뢰도는 숫자로 입력해 주세요.");
      return;
    }

    addEvidenceMutation.mutate({
      messageId: evidenceForm.messageId,
      evidenceType: evidenceForm.evidenceType.trim(),
      quoteSnapshot: evidenceForm.quoteSnapshot.trim() || undefined,
      confidence,
    });
  }

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
        <div className="flex items-start justify-between gap-4">
          <div>
            <h4 className="font-[var(--font-display)] text-2xl font-semibold tracking-[-0.04em]">
              Status actions
            </h4>
            <p className="mt-2 text-sm leading-6 text-ink/64">
              제안 결과를 확정하거나, 사유를 남기고 재검토 상태로 전환할 수 있습니다.
            </p>
          </div>
          <span className="rounded-full border border-[color:var(--color-line)] px-3 py-1 text-xs font-semibold uppercase tracking-[0.14em] text-ink/52">
            {decision.status}
          </span>
        </div>

        {canManage ? (
          <div className="mt-5 grid gap-4 xl:grid-cols-2">
            <form
              className="rounded-[1.5rem] border border-[color:var(--color-line)] bg-white/78 p-4"
              onSubmit={handleConfirmSubmit}
            >
              <div className="text-xs font-semibold uppercase tracking-[0.16em] text-ink/45">
                Confirm decision
              </div>
              <textarea
                value={confirmResultText}
                onChange={(event) => setConfirmResultText(event.target.value)}
                className="mt-3 min-h-[180px] w-full rounded-[1.2rem] border border-[color:var(--color-line)] bg-ink px-4 py-4 font-mono text-xs leading-6 text-mist outline-none transition focus:border-pine"
                spellCheck={false}
              />
              <input
                value={confirmReason}
                onChange={(event) => setConfirmReason(event.target.value)}
                className="mt-3 w-full rounded-[1.2rem] border border-[color:var(--color-line)] bg-white/88 px-4 py-3 text-sm outline-none transition focus:border-pine"
                placeholder="확정 사유 또는 비고"
              />
              <button
                type="submit"
                disabled={confirmMutation.isPending}
                className="mt-3 rounded-full bg-pine px-5 py-3 text-sm font-semibold text-white transition hover:bg-ink disabled:cursor-not-allowed disabled:bg-pine/40"
              >
                {confirmMutation.isPending ? "확정 중..." : "결정 확정"}
              </button>
            </form>

            <form
              className="rounded-[1.5rem] border border-[color:var(--color-line)] bg-white/78 p-4"
              onSubmit={handleReconsiderSubmit}
            >
              <div className="text-xs font-semibold uppercase tracking-[0.16em] text-ink/45">
                Reconsider
              </div>
              <textarea
                value={reconsiderReason}
                onChange={(event) => setReconsiderReason(event.target.value)}
                className="mt-3 min-h-[180px] w-full rounded-[1.2rem] border border-[color:var(--color-line)] bg-white/88 px-4 py-4 text-sm leading-6 outline-none transition focus:border-ember"
                placeholder="재검토 사유를 남겨 주세요."
                required
              />
              <button
                type="submit"
                disabled={reconsiderMutation.isPending}
                className="mt-3 rounded-full bg-ember px-5 py-3 text-sm font-semibold text-white transition hover:bg-ink disabled:cursor-not-allowed disabled:bg-ember/40"
              >
                {reconsiderMutation.isPending ? "전환 중..." : "재검토 요청"}
              </button>
            </form>
          </div>
        ) : (
          <div className="mt-5">
            <HostOnlyNotice message="현재 프론트엔드에서는 회의 소유자에게만 결정 확정, 재검토, evidence 추가 액션을 노출합니다." />
          </div>
        )}

        {actionError ? <div className="mt-4 text-sm text-ember">{actionError}</div> : null}
      </section>

      <section className="rounded-[1.75rem] border border-[color:var(--color-line)] bg-white/70 p-5">
        <div className="flex items-center justify-between gap-4">
          <h4 className="font-[var(--font-display)] text-2xl font-semibold tracking-[-0.04em]">
            Evidence
          </h4>
          <span className="text-sm text-ink/55">{decision.evidence.length}건</span>
        </div>
        {canManage ? (
          <form
            className="mt-4 rounded-[1.5rem] border border-[color:var(--color-line)] bg-white/78 p-4"
            onSubmit={handleAddEvidenceSubmit}
          >
          <div className="grid gap-4 lg:grid-cols-[1.05fr_0.95fr]">
            <div className="space-y-4">
              <label className="block">
                <span className="text-xs font-semibold uppercase tracking-[0.16em] text-ink/45">
                  Source message
                </span>
                <select
                  value={evidenceForm.messageId}
                  onChange={(event) => {
                    const nextMessage = threadMessagesQuery.data?.find(
                      (message) => message.id === event.target.value,
                    );
                    setEvidenceForm((current) => ({
                      ...current,
                      messageId: event.target.value,
                      quoteSnapshot: nextMessage?.body ?? current.quoteSnapshot,
                    }));
                  }}
                  className="mt-2 w-full rounded-[1.2rem] border border-[color:var(--color-line)] bg-white/85 px-4 py-3 text-sm outline-none transition focus:border-pine"
                  disabled={threadMessagesQuery.isLoading || addEvidenceMutation.isPending}
                >
                  {(threadMessagesQuery.data ?? []).map((message) => (
                    <option key={message.id} value={message.id}>
                      {formatDateTime(message.createdAt)} · {truncateText(message.body, 72)}
                    </option>
                  ))}
                </select>
              </label>

              <div className="grid gap-4 md:grid-cols-[1fr_0.8fr]">
                <label className="block">
                  <span className="text-xs font-semibold uppercase tracking-[0.16em] text-ink/45">
                    Evidence type
                  </span>
                  <input
                    value={evidenceForm.evidenceType}
                    onChange={(event) =>
                      setEvidenceForm((current) => ({
                        ...current,
                        evidenceType: event.target.value,
                      }))
                    }
                    className="mt-2 w-full rounded-[1.2rem] border border-[color:var(--color-line)] bg-white/88 px-4 py-3 text-sm outline-none transition focus:border-pine"
                    placeholder="MESSAGE_QUOTE"
                  />
                </label>

                <label className="block">
                  <span className="text-xs font-semibold uppercase tracking-[0.16em] text-ink/45">
                    Confidence
                  </span>
                  <input
                    value={evidenceForm.confidence}
                    onChange={(event) =>
                      setEvidenceForm((current) => ({
                        ...current,
                        confidence: event.target.value,
                      }))
                    }
                    className="mt-2 w-full rounded-[1.2rem] border border-[color:var(--color-line)] bg-white/88 px-4 py-3 text-sm outline-none transition focus:border-pine"
                    placeholder="0.8"
                  />
                </label>
              </div>
            </div>

            <label className="block">
              <span className="text-xs font-semibold uppercase tracking-[0.16em] text-ink/45">
                Quote snapshot
              </span>
              <textarea
                value={evidenceForm.quoteSnapshot}
                onChange={(event) =>
                  setEvidenceForm((current) => ({
                    ...current,
                    quoteSnapshot: event.target.value,
                  }))
                }
                className="mt-2 min-h-[170px] w-full rounded-[1.2rem] border border-[color:var(--color-line)] bg-white/88 px-4 py-4 text-sm leading-6 outline-none transition focus:border-pine"
                placeholder="결정의 근거가 되는 메시지 요약 또는 인용문"
              />
            </label>
          </div>

          <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
            <div className="text-sm text-ink/60">
              {threadMessagesQuery.isLoading
                ? "메시지 후보를 불러오는 중입니다."
                : threadMessagesQuery.data?.length
                  ? "선택한 메시지 본문을 기본 인용문으로 불러옵니다."
                  : "현재 스레드에 연결할 메시지가 없습니다."}
            </div>
            <button
              type="submit"
              disabled={
                addEvidenceMutation.isPending ||
                threadMessagesQuery.isLoading ||
                !threadMessagesQuery.data?.length
              }
              className="rounded-full bg-ink px-5 py-3 text-sm font-semibold text-mist transition hover:bg-pine disabled:cursor-not-allowed disabled:bg-ink/40"
            >
              {addEvidenceMutation.isPending ? "추가 중..." : "Evidence 추가"}
            </button>
          </div>
          </form>
        ) : null}
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
    <span
      className={`rounded-full border px-3 py-1 text-xs font-semibold uppercase tracking-[0.14em] ${tone}`}
    >
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

function truncateText(value: string, limit: number) {
  if (value.length <= limit) {
    return value;
  }
  return `${value.slice(0, limit - 1)}…`;
}

function parseJsonObject(value: string): Record<string, unknown> {
  const parsed: unknown = JSON.parse(value);
  if (!parsed || Array.isArray(parsed) || typeof parsed !== "object") {
    throw new Error("JSON은 객체 형태여야 합니다.");
  }
  return parsed as Record<string, unknown>;
}

function formatStateText(state: Record<string, unknown>, fallback: string) {
  return Object.keys(state).length ? JSON.stringify(state, null, 2) : fallback;
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

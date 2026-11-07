"use client";

import Link from "next/link";
import type { Route } from "next";
import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiErrorPanel } from "@/components/api-error-panel";
import { HostOnlyNotice } from "@/components/host-only-notice";
import { getApiErrorMessage } from "@/lib/api/error-display";
import {
  apiClient,
  type VoteResponseView,
} from "@/lib/api/client";
import { MeetingRealtimeBridge } from "@/components/meeting-realtime-bridge";
import { useMeetingHostAccess } from "@/lib/session";

const RESPONSE_CHOICES: Record<string, Array<{ value: string; label: string }>> = {
  date_matrix: [
    { value: "available", label: "가능" },
    { value: "maybe", label: "조정 가능" },
    { value: "unavailable", label: "불가" },
  ],
  package_preference: [
    { value: "good", label: "좋음" },
    { value: "okay", label: "괜찮음" },
    { value: "dislike", label: "비선호" },
  ],
  binary_confirm: [
    { value: "yes", label: "찬성" },
    { value: "no", label: "반대" },
  ],
};

export function VoteWorkspace({ voteId }: { voteId: string }) {
  const queryClient = useQueryClient();
  const voteQuery = useQuery({
    queryKey: ["vote", voteId],
    queryFn: () => apiClient.getVote(voteId),
  });
  const [draftResponses, setDraftResponses] = useState<Record<string, string>>({});
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!voteQuery.data) {
      return;
    }

    const nextDraft = voteQuery.data.responses.reduce<Record<string, string>>((acc, item) => {
      acc[item.optionId] = item.responseValue;
      return acc;
    }, {});
    setDraftResponses(nextDraft);
  }, [voteQuery.data]);

  const submitMutation = useMutation({
    mutationFn: (vote: VoteResponseView) =>
      apiClient.submitVoteResponses(vote.voteId, {
        responses: vote.options
          .map((option) => ({
            optionId: option.id,
            responseValue: draftResponses[option.id],
          }))
          .filter((item) => Boolean(item.responseValue)),
      }),
    onSuccess: async () => {
      setErrorMessage(null);
      await queryClient.invalidateQueries({ queryKey: ["vote", voteId] });
    },
    onError: (error: unknown) => {
      setErrorMessage(
        getApiErrorMessage(error, {
          resourceName: "투표 응답 저장",
          conflictMessage: "투표가 이미 마감되었거나 현재 상태에서는 응답을 저장할 수 없습니다.",
        }),
      );
    },
  });

  const closeMutation = useMutation({
    mutationFn: () => apiClient.closeVote(voteId),
    onSuccess: async () => {
      setErrorMessage(null);
      await queryClient.invalidateQueries({ queryKey: ["vote", voteId] });
    },
    onError: (error: unknown) => {
      setErrorMessage(
        getApiErrorMessage(error, {
          resourceName: "투표 마감",
          conflictMessage: "이미 마감된 투표이거나 현재 상태에서는 마감할 수 없습니다.",
        }),
      );
    },
  });

  const allowedChoices = voteQuery.data ? RESPONSE_CHOICES[voteQuery.data.voteType] ?? [] : [];
  const hasAllResponses = voteQuery.data
    ? voteQuery.data.options.every((option) => Boolean(draftResponses[option.id]))
    : false;

  const leadingIds = useMemo(
    () => new Set(voteQuery.data?.aggregates?.leadingOptionIds ?? []),
    [voteQuery.data],
  );

  const access = useMeetingHostAccess(voteQuery.data?.meetingId);

  if (voteQuery.isLoading) {
    return <div className="panel px-6 py-6 text-sm text-muted">투표를 불러오는 중이에요…</div>;
  }

  if (voteQuery.error || !voteQuery.data) {
    return <ApiErrorPanel error={voteQuery.error} resourceName="투표 상세" />;
  }

  const vote = voteQuery.data;
  const status = vote.status.toLowerCase();
  const isOpen = status === "open";
  const isFinished = status === "closed" || status === "superseded";
  const aggregates = vote.aggregates;
  const ranked = [...(aggregates?.options ?? [])].sort((a, b) => (a.rank ?? 99) - (b.rank ?? 99));
  const maxScore = Math.max(1, ...ranked.map((option) => option.rawScore ?? 0));
  const meetingHref = `/meetings/${vote.meetingId}` as Route;

  return (
    <>
      <MeetingRealtimeBridge meetingId={vote.meetingId} voteId={vote.voteId} />
      <div className="flex flex-col gap-1.5">
        <Link href={meetingHref} className="inline-flex min-h-[44px] items-center self-start text-[13px] font-semibold text-muted hover:text-ink">
          ← {access.meetingQuery.data?.title ?? "모임으로"}
        </Link>
        <span className="num text-[11px] font-semibold tracking-[0.06em] text-accent-text">
          {labelForVoteType(vote.voteType)} · {VOTE_STATUS_LABELS[status] ?? vote.status}
        </span>
        <h1 className="font-display m-0 text-[clamp(1.8rem,3.4vw,2.4rem)]">{VOTE_TITLES[vote.voteType] ?? "투표"}</h1>
        <p className="m-0 text-[13px] text-muted">
          {aggregates
            ? `${aggregates.totalParticipants}명 중 ${aggregates.respondedParticipants}명 응답`
            : "아직 응답이 없어요"}
          {vote.deadlineAt ? ` · ${formatDateTime(vote.deadlineAt)} 마감` : ""} · 반대가 적은 안을 먼저 추천해요
        </p>
      </div>

      <div className="flex flex-wrap items-start gap-5">
        <section aria-label="추천 순위" className="flex min-w-0 flex-[1_1_360px] flex-col gap-1.5">
          {ranked.length ? (
            ranked.map((option, index) => {
              const top = index === 0 && leadingIds.has(option.optionId);
              const width = `${Math.round(((option.rawScore ?? 0) / maxScore) * 100)}%`;
              return (
                <div
                  key={option.optionId}
                  className={`flex items-center gap-3 rounded-[14px] px-3.5 py-3 ${top ? "bg-accent text-on-accent" : "bg-surface text-ink"}`}
                >
                  <span className="num w-[22px] text-sm font-semibold">{String(option.rank ?? index + 1).padStart(2, "0")}</span>
                  <div className="flex min-w-0 flex-1 flex-col gap-1.5">
                    <div className="flex flex-wrap items-baseline justify-between gap-2">
                      <span className="text-[15px] font-bold">{option.label}</span>
                      <span className="num text-[11px]">
                        {Object.entries(option.counts)
                          .map(([key, value]) => `${CHOICE_LABELS[key] ?? key} ${value}`)
                          .join(" · ")}
                      </span>
                    </div>
                    <div className={`h-1.5 rounded-[3px] ${top ? "bg-white/25" : "bg-line"}`}>
                      <div className={`h-1.5 rounded-[3px] ${top ? "bg-white" : "bg-ink"}`} style={{ width }} />
                    </div>
                  </div>
                </div>
              );
            })
          ) : (
            <div className="empty-state">아직 집계할 응답이 없어요.</div>
          )}

          {aggregates?.canEarlyClose && isOpen ? (
            <div className="mt-1.5 flex gap-2.5 rounded-[14px] bg-accent-soft px-3.5 py-3 text-[13px] leading-[1.45] text-accent-soft-ink">
              <span aria-hidden="true">ⓘ</span>
              <span>
                남은 {aggregates.unresolvedParticipants}명이 응답해도 1위가 바뀌지 않아요. 지금 마감해도 돼요.
              </span>
            </div>
          ) : null}

          <div className="mt-2 flex flex-wrap gap-2">
            {access.isHost ? (
              <button
                type="button"
                onClick={() => closeMutation.mutate()}
                disabled={closeMutation.isPending || isFinished}
                className="action-primary min-h-[48px] flex-1"
              >
                {closeMutation.isPending ? "마감 중…" : isFinished ? "마감된 투표" : "투표 마감"}
              </button>
            ) : (
              <HostOnlyNotice message="투표 마감은 호스트가 해요." />
            )}
            {vote.decisionId ? (
              <Link href={`/meetings/${vote.meetingId}/decisions?decision=${vote.decisionId}` as Route} className="action-secondary min-h-[48px]">
                결정 보기
              </Link>
            ) : null}
            <Link href={`/meetings/${vote.meetingId}/threads/${vote.threadId}` as Route} className="action-secondary min-h-[48px]">
              토픽 열기
            </Link>
          </div>
        </section>

        <form
          aria-label="내 응답"
          className="panel flex min-w-0 flex-[1_1_420px] flex-col gap-3 p-5"
          onSubmit={(event) => {
            event.preventDefault();
            setErrorMessage(null);
            submitMutation.mutate(vote);
          }}
        >
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <h2 className="m-0 text-base font-bold">내 응답</h2>
            <span className={`text-xs font-semibold ${hasAllResponses ? "text-muted" : "text-warn-ink"}`}>
              {hasAllResponses ? "모두 응답했어요" : "아직 고르지 않은 후보가 있어요"}
            </span>
          </div>
          <ul className="m-0 flex list-none flex-col p-0">
            {vote.options.map((option) => (
              <li key={option.id} className="flex flex-wrap items-center justify-between gap-2 border-t border-line py-3 first:border-t-0">
                <span className="min-w-0">
                  <b className="text-sm">{option.label}</b>
                  {leadingIds.has(option.id) ? <span className="chip-voting ml-2">1위</span> : null}
                </span>
                <span role="radiogroup" aria-label={`${option.label} 응답`} className="flex gap-1">
                  {allowedChoices.map((choice) => {
                    const selected = draftResponses[option.id] === choice.value;
                    return (
                      <button
                        key={choice.value}
                        type="button"
                        role="radio"
                        aria-checked={selected}
                        disabled={!isOpen}
                        onClick={() =>
                          setDraftResponses((current) => ({
                            ...current,
                            [option.id]: choice.value,
                          }))
                        }
                        className={`min-h-[40px] rounded-[10px] border px-3 text-[13px] font-bold transition disabled:cursor-not-allowed ${
                          selected ? CHOICE_SELECTED[choice.value] ?? "border-ink bg-ink text-on-ink" : "border-line bg-surface text-ink hover:bg-surface-2"
                        }`}
                      >
                        {choice.label}
                      </button>
                    );
                  })}
                </span>
              </li>
            ))}
          </ul>
          {errorMessage ? <p className="m-0 text-sm text-danger">{errorMessage}</p> : null}
          <button type="submit" disabled={submitMutation.isPending || !isOpen} className="btn-accent min-h-[48px]">
            {submitMutation.isPending ? "저장 중…" : isOpen ? "응답 저장" : "응답을 받지 않는 투표예요"}
          </button>
        </form>
      </div>
    </>
  );
}

const VOTE_STATUS_LABELS: Record<string, string> = {
  draft: "준비 중",
  open: "투표 중",
  closed: "마감",
  superseded: "대체됨",
};

const VOTE_TITLES: Record<string, string> = {
  date_matrix: "언제 갈까?",
  package_preference: "어디로 갈까?",
  binary_confirm: "이대로 정할까?",
};

const CHOICE_LABELS: Record<string, string> = Object.fromEntries(
  Object.values(RESPONSE_CHOICES)
    .flat()
    .map((choice) => [choice.value, choice.label]),
);

const CHOICE_SELECTED: Record<string, string> = {
  available: "border-ink bg-ink text-on-ink",
  good: "border-ink bg-ink text-on-ink",
  yes: "border-ink bg-ink text-on-ink",
  maybe: "border-[#F2B33D] bg-[#F2B33D] text-[#15171C]",
  okay: "border-[#F2B33D] bg-[#F2B33D] text-[#15171C]",
  unavailable: "border-risk-ink/40 bg-risk-soft text-risk-ink",
  dislike: "border-risk-ink/40 bg-risk-soft text-risk-ink",
  no: "border-risk-ink/40 bg-risk-soft text-risk-ink",
};

function labelForVoteType(voteType: string) {
  if (voteType === "date_matrix") {
    return "날짜";
  }
  if (voteType === "package_preference") {
    return "장소";
  }
  if (voteType === "binary_confirm") {
    return "확정 여부";
  }
  return voteType;
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

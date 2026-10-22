"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiErrorPanel } from "@/components/api-error-panel";
import { HostOnlyNotice } from "@/components/host-only-notice";
import { getApiErrorMessage } from "@/lib/api/error-display";
import {
  apiClient,
  type VoteAggregateOptionView,
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

  if (voteQuery.isLoading) {
    return <div className="panel px-6 py-6 text-sm text-ink/60">투표 작업공간을 불러오는 중입니다.</div>;
  }

  if (voteQuery.error || !voteQuery.data) {
    return <ApiErrorPanel error={voteQuery.error} resourceName="투표 상세" />;
  }

  const vote = voteQuery.data;
  const access = useMeetingHostAccess(vote.meetingId);

  return (
    <>
      <MeetingRealtimeBridge meetingId={vote.meetingId} voteId={vote.voteId} />
      <div className="grid gap-5 xl:grid-cols-[0.92fr_1.08fr]">
        <section className="panel px-6 py-6">
          <div className="info-chip">Vote summary</div>
          <h2 className="mt-4 font-[var(--font-display)] text-4xl font-semibold leading-[0.96] tracking-[-0.055em]">
            {labelForVoteType(vote.voteType)}
          </h2>
          <div className="mt-6 grid gap-3 sm:grid-cols-2">
            <Metric label="Status" value={vote.status} />
            <Metric label="Options" value={String(vote.options.length)} />
            <Metric
              label="Responded"
              value={`${vote.aggregates?.respondedParticipants ?? 0}/${vote.aggregates?.totalParticipants ?? 0}`}
            />
            <Metric label="Deadline" value={vote.deadlineAt ? formatDateTime(vote.deadlineAt) : "미설정"} />
          </div>
          <div className="mt-5 flex flex-wrap gap-2 text-sm text-[color:rgba(13,21,38,0.62)]">
            {vote.decisionId ? (
              <Link href={`/meetings/${vote.meetingId}/decisions`} className="action-secondary">
                Decision board
              </Link>
            ) : null}
            <Link href={`/meetings/${vote.meetingId}/threads/${vote.threadId}`} className="action-secondary">
              Open thread
            </Link>
            <Link href={`/meetings/${vote.meetingId}`} className="action-secondary">
              Meeting overview
            </Link>
          </div>

          <form
            className="mt-6 rounded-[1.75rem] border border-[color:var(--color-line)] bg-[rgba(255,255,255,0.66)] p-5"
            onSubmit={(event) => {
              event.preventDefault();
              setErrorMessage(null);
              submitMutation.mutate(vote);
            }}
          >
            <div className="flex items-center justify-between gap-4">
              <h3 className="font-[var(--font-display)] text-2xl font-semibold tracking-[-0.04em]">
                내 응답
              </h3>
              <span className="text-sm text-[color:rgba(13,21,38,0.55)]">
                {hasAllResponses ? "모든 옵션 응답 완료" : "미응답 옵션이 있습니다"}
              </span>
            </div>

          <div className="mt-4 space-y-4">
            {vote.options.map((option) => (
              <article
                key={option.id}
                className="rounded-[1.5rem] border border-[color:var(--color-line)] bg-white/82 p-4"
              >
                <div className="flex items-center justify-between gap-4">
                  <div>
                    <div className="text-lg font-semibold text-ink">{option.label}</div>
                    <div className="mt-1 text-xs uppercase tracking-[0.16em] text-ink/45">
                      {option.optionType}
                    </div>
                  </div>
                  {leadingIds.has(option.id) ? (
                    <span className="rounded-full border border-pine/20 bg-pine/10 px-3 py-1 text-xs font-semibold uppercase tracking-[0.14em] text-pine">
                      Leading
                    </span>
                  ) : null}
                </div>
                {Object.keys(option.payload).length ? (
                  <pre className="mt-3 overflow-x-auto rounded-[1.25rem] bg-mist/90 px-3 py-3 text-xs leading-5 text-ink/75">
                    {JSON.stringify(option.payload, null, 2)}
                  </pre>
                ) : null}
                <div className="mt-4 flex flex-wrap gap-2">
                  {allowedChoices.map((choice) => {
                    const selected = draftResponses[option.id] === choice.value;
                    return (
                      <button
                        key={choice.value}
                        type="button"
                        onClick={() =>
                          setDraftResponses((current) => ({
                            ...current,
                            [option.id]: choice.value,
                          }))
                        }
                        className={[
                          "rounded-full border px-4 py-2 text-sm font-semibold transition",
                          selected
                            ? "border-pine bg-pine text-white"
                            : "border-[color:var(--color-line)] bg-white/88 text-ink hover:bg-white",
                        ].join(" ")}
                      >
                        {choice.label}
                      </button>
                    );
                  })}
                </div>
              </article>
            ))}
          </div>

          {errorMessage ? <div className="mt-4 text-sm text-ember">{errorMessage}</div> : null}

          <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
            <div className="text-sm text-ink/60">
              응답은 현재 사용자 기준으로 전체 교체 저장됩니다.
            </div>
            <button
              type="submit"
              disabled={submitMutation.isPending || vote.status !== "OPEN"}
              className="action-primary disabled:cursor-not-allowed disabled:bg-[rgba(13,21,38,0.4)]"
            >
              {submitMutation.isPending ? "저장 중..." : "응답 저장"}
            </button>
          </div>
          </form>
        </section>

        <section className="panel px-6 py-6">
          <div className="flex items-start justify-between gap-4">
            <div>
              <div className="info-chip">Aggregates</div>
              <h3 className="mt-3 font-[var(--font-display)] text-3xl font-semibold tracking-[-0.05em]">
                집계 결과
              </h3>
            </div>
            {access.isHost ? (
              <button
                type="button"
                onClick={() => closeMutation.mutate()}
                disabled={closeMutation.isPending || vote.status === "CLOSED" || vote.status === "SUPERSEDED"}
                className="inline-flex items-center justify-center rounded-full bg-[color:var(--color-ember)] px-5 py-3 text-sm font-semibold text-white transition active:translate-y-px hover:bg-[var(--color-ink)] disabled:cursor-not-allowed disabled:bg-[rgba(211,85,51,0.4)]"
              >
                {closeMutation.isPending ? "마감 중..." : "투표 마감"}
              </button>
            ) : null}
          </div>

          {vote.aggregates ? (
            <>
              <div className="mt-5 grid gap-3 sm:grid-cols-3">
                <Metric label="Participants" value={String(vote.aggregates.totalParticipants)} />
                <Metric label="Responded" value={String(vote.aggregates.respondedParticipants)} />
                <Metric label="Unresolved" value={String(vote.aggregates.unresolvedParticipants)} />
              </div>

              <div className="mt-5 space-y-3">
                {vote.aggregates.options.map((option) => (
                  <AggregateCard
                    key={option.optionId}
                    option={option}
                    isLeading={leadingIds.has(option.optionId)}
                  />
                ))}
              </div>

              <div className="mt-5 rounded-[1.5rem] border border-[color:var(--color-line)] bg-white/72 p-4 text-sm text-ink/68">
                조기 마감 가능 여부:{" "}
                <strong>
                  {vote.aggregates.canEarlyClose == null
                    ? "판정 없음"
                    : vote.aggregates.canEarlyClose
                      ? "가능"
                      : "아직 불가"}
                </strong>
              </div>
              {!access.isHost ? (
                <HostOnlyNotice message="현재 프론트엔드에서는 회의 소유자에게만 투표 마감 액션을 노출합니다." />
              ) : null}
            </>
          ) : (
            <div className="mt-5 rounded-[1.5rem] border border-dashed border-[color:var(--color-line)] bg-white/60 px-5 py-6 text-sm text-ink/58">
              집계 정보가 아직 없습니다.
            </div>
          )}
        </section>
      </div>
    </>
  );
}

function AggregateCard({
  option,
  isLeading,
}: {
  option: VoteAggregateOptionView;
  isLeading: boolean;
}) {
  return (
    <article
      className={[
        "rounded-[1.5rem] border p-4",
        isLeading ? "border-pine bg-pine/10" : "border-[color:var(--color-line)] bg-white/78",
      ].join(" ")}
    >
      <div className="flex items-center justify-between gap-4">
        <div>
          <div className="text-lg font-semibold text-ink">{option.label}</div>
          <div className="mt-1 text-xs uppercase tracking-[0.16em] text-ink/45">
            Rank {option.rank ?? "-"} · Score {option.rawScore ?? 0}
          </div>
        </div>
        {isLeading ? (
          <span className="rounded-full border border-pine/20 bg-white/80 px-3 py-1 text-xs font-semibold uppercase tracking-[0.14em] text-pine">
            Leading
          </span>
        ) : null}
      </div>
      <div className="mt-4 flex flex-wrap gap-2">
        {Object.entries(option.counts).map(([key, value]) => (
          <span
            key={key}
            className="rounded-full border border-[color:var(--color-line)] bg-white/82 px-3 py-2 text-xs font-semibold uppercase tracking-[0.14em] text-ink/65"
          >
            {key} {value}
          </span>
        ))}
      </div>
    </article>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-[1.5rem] border border-[color:var(--color-line)] bg-white/70 p-4">
      <div className="text-xs font-semibold uppercase tracking-[0.16em] text-ink/45">{label}</div>
      <div className="mt-2 font-[var(--font-display)] text-2xl font-semibold tracking-[-0.04em]">
        {value}
      </div>
    </div>
  );
}

function labelForVoteType(voteType: string) {
  if (voteType === "date_matrix") {
    return "날짜 매트릭스 투표";
  }
  if (voteType === "package_preference") {
    return "패키지 선호 투표";
  }
  if (voteType === "binary_confirm") {
    return "이진 확정 투표";
  }
  return voteType;
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

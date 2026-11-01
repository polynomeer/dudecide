"use client";

import type { Route } from "next";
import { useRouter } from "next/navigation";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { getApiErrorMessage } from "@/lib/api/error-display";
import { apiClient, type DecisionSuggestionView } from "@/lib/api/client";
import { decisionTypeLabel } from "@/lib/display";

function headline(suggestion: DecisionSuggestionView) {
  const option = typeof suggestion.proposedResult.option === "string" ? suggestion.proposedResult.option : null;
  return `${decisionTypeLabel(suggestion.decisionType)}${option ? `: ${option}` : ""}`;
}

/** The "결정 후보를 찾았어요" card from the Discussion design. Only hosts and co-hosts can act on it. */
export function DecisionSuggestionCard({
  meetingId,
  suggestion,
  canManage,
}: {
  meetingId: string;
  suggestion: DecisionSuggestionView;
  canManage: boolean;
}) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const supports = suggestion.evidence.filter((item) => item.role !== "opposition").length;
  const objections = suggestion.evidence.filter((item) => item.role === "opposition").length;
  const contested = suggestion.analysisStatus === "contested";

  const refresh = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ["decision-suggestions", suggestion.threadId] }),
      queryClient.invalidateQueries({ queryKey: ["meeting-decisions", meetingId] }),
      queryClient.invalidateQueries({ queryKey: ["meeting", meetingId] }),
    ]);
  const accept = useMutation({
    mutationFn: () => apiClient.acceptDecisionSuggestion(suggestion.id),
    onSuccess: async (decision) => {
      await refresh();
      router.push(`/meetings/${meetingId}/decisions?decision=${decision.id}` as Route);
    },
  });
  const dismiss = useMutation({
    mutationFn: () => apiClient.dismissDecisionSuggestion(suggestion.id),
    onSuccess: refresh,
  });
  const error = accept.error ?? dismiss.error;

  return (
    <section
      aria-label="결정 후보"
      className="ml-10 flex max-w-[22rem] flex-col gap-2.5 rounded-2xl bg-band p-3.5 text-white"
    >
      <div className="num flex items-center gap-1.5 text-[11px] font-semibold tracking-[0.04em] text-[#B8C2FF]">
        <svg aria-hidden="true" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <path d="M12 3v4M12 17v4M3 12h4M17 12h4M6 6l2.5 2.5M15.5 15.5 18 18M6 18l2.5-2.5M15.5 8.5 18 6" />
        </svg>
        {contested ? "의견이 갈린 주제를 찾았어요" : "결정 후보를 찾았어요"}
      </div>
      <p className="m-0 text-sm leading-[1.45]">
        <b>{headline(suggestion)}</b>
        {contested ? " — 결정 보드에 올려서 같이 정할까요?" : " — 결정 초안으로 만들까요?"} 근거 메시지 {supports}개
        {objections ? `와 반대 의견 ${objections}개` : ""}가 함께 저장돼요.
      </p>
      {canManage ? (
        <div className="flex gap-1.5">
          <button
            type="button"
            onClick={() => accept.mutate()}
            disabled={accept.isPending || dismiss.isPending}
            className="h-10 flex-1 rounded-[10px] bg-white text-[13px] font-bold text-[#15171C] disabled:opacity-60"
          >
            {accept.isPending ? "만드는 중…" : "초안 만들기"}
          </button>
          <button
            type="button"
            onClick={() => dismiss.mutate()}
            disabled={accept.isPending || dismiss.isPending}
            className="h-10 flex-1 rounded-[10px] border border-white/30 text-[13px] font-semibold text-white disabled:opacity-60"
          >
            그냥 대화예요
          </button>
        </div>
      ) : (
        <p className="m-0 text-xs text-[#C9CCD3]">호스트가 확인하면 결정 보드에 올라가요.</p>
      )}
      {error ? (
        <p role="alert" className="m-0 text-xs text-[#FFB38A]">
          {getApiErrorMessage(error, {
            resourceName: "결정 후보",
            conflictMessage: "이미 다른 사람이 처리한 후보예요.",
          })}
        </p>
      ) : null}
    </section>
  );
}

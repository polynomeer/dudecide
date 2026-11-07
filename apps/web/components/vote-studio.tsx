"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQuery } from "@tanstack/react-query";
import { ApiErrorPanel } from "@/components/api-error-panel";
import { HostOnlyNotice } from "@/components/host-only-notice";
import { getApiErrorMessage } from "@/lib/api/error-display";
import { apiClient, type CreateVoteRequest, type CreateVoteOptionRequest } from "@/lib/api/client";
import { useMeetingHostAccess } from "@/lib/session";

const DEFAULT_OPTION_TEXT: Record<string, string> = {
  date_matrix: ["2026-09-12", "2026-09-19"].join("\n"),
  package_preference: ["Gapyeong|180000|90", "Yangpyeong|220000|120"].join("\n"),
  binary_confirm: ["현재 제안안으로 확정", "추가 논의 후 재투표"].join("\n"),
};

export function VoteStudio({ meetingId }: { meetingId: string }) {
  const router = useRouter();
  const access = useMeetingHostAccess(meetingId);
  const meetingQuery = useQuery({
    queryKey: ["meeting", meetingId],
    queryFn: () => apiClient.getMeeting(meetingId),
  });
  const decisionsQuery = useQuery({
    queryKey: ["meeting-decisions", meetingId],
    queryFn: () => apiClient.listDecisions(meetingId),
  });

  const [form, setForm] = useState({
    threadId: "",
    decisionId: "",
    title: "",
    voteType: "date_matrix",
    opensAt: "",
    deadlineAt: "",
    optionText: DEFAULT_OPTION_TEXT.date_matrix,
  });
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!form.threadId && meetingQuery.data?.threads.length) {
      setForm((current) => ({ ...current, threadId: meetingQuery.data!.threads[0].id }));
    }
  }, [form.threadId, meetingQuery.data]);

  const filteredDecisions = useMemo(
    () =>
      (decisionsQuery.data ?? []).filter((decision) =>
        form.threadId ? decision.threadId === form.threadId : true,
      ),
    [decisionsQuery.data, form.threadId],
  );

  const createVoteMutation = useMutation({
    mutationFn: (payload: CreateVoteRequest) => apiClient.createVote(meetingId, payload),
    onSuccess: (vote) => {
      setErrorMessage(null);
      router.push(`/votes/${vote.voteId}`);
    },
    onError: (error: unknown) => {
      setErrorMessage(
        getApiErrorMessage(error, {
          resourceName: "투표 생성",
          conflictMessage: "현재 연결된 의사결정 또는 회의 상태에서는 새 투표를 만들 수 없습니다.",
        }),
      );
    },
  });

  function handleVoteTypeChange(nextVoteType: string) {
    setForm((current) => ({
      ...current,
      voteType: nextVoteType,
      optionText: DEFAULT_OPTION_TEXT[nextVoteType] ?? "",
    }));
  }

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setErrorMessage(null);

    let options: CreateVoteOptionRequest[];
    try {
      options = parseOptions(form.voteType, form.optionText);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "투표 옵션 형식이 올바르지 않습니다.");
      return;
    }

    createVoteMutation.mutate({
      threadId: form.threadId,
      decisionId: form.decisionId || undefined,
      title: form.title.trim(),
      voteType: form.voteType,
      opensAt: form.opensAt || undefined,
      deadlineAt: form.deadlineAt || undefined,
      options,
    });
  }

  if (meetingQuery.isLoading || decisionsQuery.isLoading) {
    return <div className="text-sm text-muted">투표 만들기를 불러오는 중이에요…</div>;
  }

  if (meetingQuery.error || decisionsQuery.error || !meetingQuery.data) {
    return <ApiErrorPanel error={meetingQuery.error ?? decisionsQuery.error} resourceName="투표 생성 화면" />;
  }

  if (!access.isHost) {
    return (
      <HostOnlyNotice message="투표는 호스트가 만들 수 있어요." />
    );
  }

  return (
    <section>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="section-copy m-0">토픽과 결정을 골라 투표를 열면 바로 투표 화면으로 이동해요.</p>
        <span className="chip">결정 {filteredDecisions.length}건 연결 가능</span>
      </div>

      <form className="mt-5 grid gap-4 xl:grid-cols-[1.05fr_0.95fr]" onSubmit={handleSubmit}>
        <div className="space-y-4">
          <div className="grid gap-4 md:grid-cols-2">
            <label className="block">
              <span className="text-xs font-bold">
                토픽
              </span>
              <select
                value={form.threadId}
                onChange={(event) =>
                  setForm((current) => ({
                    ...current,
                    threadId: event.target.value,
                    decisionId: "",
                  }))
                }
                className="field mt-2"
              >
                {meetingQuery.data.threads.map((thread) => (
                  <option key={thread.id} value={thread.id}>
                    {thread.title} ({thread.category})
                  </option>
                ))}
              </select>
            </label>

            <label className="block">
              <span className="text-xs font-bold">
                투표 종류
              </span>
              <select
                value={form.voteType}
                onChange={(event) => handleVoteTypeChange(event.target.value)}
                className="field mt-2"
              >
                <option value="date_matrix">날짜 (가능·애매·불가)</option>
                <option value="package_preference">장소·패키지 (좋아·괜찮아·별로)</option>
                <option value="binary_confirm">확정 여부 (찬성·반대)</option>
              </select>
            </label>
          </div>

          <label className="block">
            <span className="text-xs font-bold">
              연결할 결정
            </span>
            <select
              value={form.decisionId}
              onChange={(event) =>
                setForm((current) => ({
                  ...current,
                  decisionId: event.target.value,
                }))
              }
              className="field mt-2"
            >
              <option value="">새 결정 자동 생성</option>
              {filteredDecisions.map((decision) => (
                <option key={decision.id} value={decision.id}>
                  {decision.title} ({decision.status})
                </option>
              ))}
            </select>
          </label>

          <label className="block">
            <span className="text-xs font-bold">
              제목
            </span>
            <input
              value={form.title}
              onChange={(event) =>
                setForm((current) => ({
                  ...current,
                  title: event.target.value,
                }))
              }
              className="field mt-2"
              placeholder="예: 1차 일정 투표"
              required
            />
          </label>

          <div className="grid gap-4 md:grid-cols-2">
            <label className="block">
              <span className="text-xs font-bold">
                시작
              </span>
              <input
                type="datetime-local"
                value={form.opensAt}
                onChange={(event) =>
                  setForm((current) => ({
                    ...current,
                    opensAt: event.target.value,
                  }))
                }
                className="field mt-2"
              />
            </label>

            <label className="block">
              <span className="text-xs font-bold">
                마감
              </span>
              <input
                type="datetime-local"
                value={form.deadlineAt}
                onChange={(event) =>
                  setForm((current) => ({
                    ...current,
                    deadlineAt: event.target.value,
                  }))
                }
                className="field mt-2"
              />
            </label>
          </div>
        </div>

        <label className="block">
          <span className="text-xs font-bold">
            후보 (한 줄에 하나)
          </span>
          <textarea
            value={form.optionText}
            onChange={(event) =>
              setForm((current) => ({
                ...current,
                optionText: event.target.value,
              }))
            }
            className="mono-panel mt-2 min-h-[280px] w-full border border-line"
            spellCheck={false}
          />
          <div className="soft-card mt-3 text-xs leading-6 text-ink/64">
            {helperTextForVoteType(form.voteType)}
          </div>
        </label>

        <div className="soft-card xl:col-span-2 flex flex-wrap items-center justify-between gap-3">
          <div className="text-sm text-ink/62">
            {errorMessage ?? "생성 후 투표 상세 페이지로 즉시 이동합니다."}
          </div>
          <button
            type="submit"
            disabled={createVoteMutation.isPending}
            className="action-primary disabled:cursor-not-allowed disabled:opacity-40"
          >
            {createVoteMutation.isPending ? "생성 중..." : "투표 생성"}
          </button>
        </div>
      </form>
    </section>
  );
}

function parseOptions(voteType: string, optionText: string): CreateVoteOptionRequest[] {
  const lines = optionText
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean);

  if (lines.length === 0) {
    throw new Error("최소 한 개 이상의 옵션이 필요합니다.");
  }

  if (voteType === "date_matrix") {
    return lines.map((line, index) => ({
      optionType: "date",
      label: line,
      payload: { date: line },
      sortOrder: index,
    }));
  }

  if (voteType === "package_preference") {
    return lines.map((line, index) => {
      const [label, maxBudget, travelMinutes] = line.split("|").map((part) => part.trim());
      if (!label || !maxBudget || !travelMinutes) {
        throw new Error("package_preference는 `지역명|maxBudget|travelMinutes` 형식이어야 합니다.");
      }
      return {
        optionType: "package",
        label,
        payload: {
          maxBudget: Number.parseInt(maxBudget, 10),
          travelMinutes: Number.parseInt(travelMinutes, 10),
        },
        sortOrder: index,
      };
    });
  }

  return lines.map((line, index) => ({
    optionType: "choice",
    label: line,
    payload: {},
    sortOrder: index,
  }));
}

function helperTextForVoteType(voteType: string) {
  if (voteType === "date_matrix") {
    return "한 줄에 하루씩 입력합니다. 예: 2026-09-12";
  }
  if (voteType === "package_preference") {
    return "한 줄에 `지역명|maxBudget|travelMinutes` 형식으로 입력합니다. 예: Gapyeong|180000|90";
  }
  return "한 줄에 하나씩 이진 선택지를 입력합니다. 예: 현재 제안안으로 확정";
}

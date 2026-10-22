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
    return <div className="panel px-6 py-6 text-sm text-ink/60">투표 생성 작업공간을 불러오는 중입니다.</div>;
  }

  if (meetingQuery.error || decisionsQuery.error || !meetingQuery.data) {
    return <ApiErrorPanel error={meetingQuery.error ?? decisionsQuery.error} resourceName="투표 생성 화면" />;
  }

  if (!access.isHost) {
    return (
      <section className="panel px-6 py-6">
        <div className="info-chip">Vote studio</div>
        <h3 className="mt-4 font-display text-4xl">
          새 투표 생성
        </h3>
        <div className="mt-5">
          <HostOnlyNotice message="현재 프론트엔드에서는 회의 소유자에게만 투표 생성 액션을 노출합니다." />
        </div>
      </section>
    );
  }

  return (
    <section className="panel px-6 py-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <div className="info-chip">Vote studio</div>
          <h3 className="mt-4 font-display text-4xl leading-[0.96]">
            새 투표 생성
          </h3>
          <p className="section-copy mt-3 max-w-2xl">
            스레드와 의사결정 맥락을 연결해 투표를 만들고, 생성 직후 상세 화면으로 이동합니다.
          </p>
        </div>
        <div className="info-chip normal-case tracking-[0.02em]">결정 {filteredDecisions.length}건 연결 가능</div>
      </div>

      <form className="mt-5 grid gap-4 xl:grid-cols-[1.05fr_0.95fr]" onSubmit={handleSubmit}>
        <div className="space-y-4">
          <div className="grid gap-4 md:grid-cols-2">
            <label className="block">
              <span className="text-xs font-semibold uppercase tracking-[0.16em] text-ink/45">
                Thread
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
              <span className="text-xs font-semibold uppercase tracking-[0.16em] text-ink/45">
                Vote type
              </span>
              <select
                value={form.voteType}
                onChange={(event) => handleVoteTypeChange(event.target.value)}
                className="field mt-2"
              >
                <option value="date_matrix">date_matrix</option>
                <option value="package_preference">package_preference</option>
                <option value="binary_confirm">binary_confirm</option>
              </select>
            </label>
          </div>

          <label className="block">
            <span className="text-xs font-semibold uppercase tracking-[0.16em] text-ink/45">
              Linked decision
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
              <option value="">새 decision 자동 생성</option>
              {filteredDecisions.map((decision) => (
                <option key={decision.id} value={decision.id}>
                  {decision.title} ({decision.status})
                </option>
              ))}
            </select>
          </label>

          <label className="block">
            <span className="text-xs font-semibold uppercase tracking-[0.16em] text-ink/45">
              Title
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
              <span className="text-xs font-semibold uppercase tracking-[0.16em] text-ink/45">
                Opens at
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
              <span className="text-xs font-semibold uppercase tracking-[0.16em] text-ink/45">
                Deadline
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
          <span className="text-xs font-semibold uppercase tracking-[0.16em] text-ink/45">
            Options
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

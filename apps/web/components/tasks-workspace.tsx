"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { ApiErrorPanel } from "@/components/api-error-panel";
import { HostOnlyNotice } from "@/components/host-only-notice";
import { getApiErrorMessage } from "@/lib/api/error-display";
import { apiClient, type UpdateTaskRequest } from "@/lib/api/client";
import { memberName, useMeetingMembers } from "@/lib/members";
import { useMeetingHostAccess } from "@/lib/session";

// Matches the backend task_status enum; "cancelled" exists but is not offered here.
const TASK_STATUSES = ["todo", "in_progress", "done"] as const;
const TASK_STATUS_LABELS: Record<(typeof TASK_STATUSES)[number], string> = {
  todo: "할 일",
  in_progress: "하는 중",
  done: "완료",
};

export function TasksWorkspace({ meetingId }: { meetingId: string }) {
  const queryClient = useQueryClient();
  const access = useMeetingHostAccess(meetingId);
  const tasksQuery = useQuery({
    queryKey: ["meeting-tasks", meetingId],
    queryFn: () => apiClient.listTasks(meetingId),
  });
  const membersQuery = useMeetingMembers(meetingId);
  const members = membersQuery.data ?? [];
  const [form, setForm] = useState({
    title: "",
    dueAt: "",
    ownerUserId: "",
  });
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const createTaskMutation = useMutation({
    mutationFn: () =>
      apiClient.createTask(meetingId, {
        title: form.title.trim(),
        dueAt: form.dueAt ? new Date(form.dueAt).toISOString() : undefined,
        ownerUserId: form.ownerUserId || undefined,
      }),
    onSuccess: async () => {
      setForm({ title: "", dueAt: "", ownerUserId: "" });
      setErrorMessage(null);
      await queryClient.invalidateQueries({ queryKey: ["meeting-tasks", meetingId] });
      await queryClient.invalidateQueries({ queryKey: ["meeting-search", meetingId] });
    },
    onError: (error: unknown) => {
      setErrorMessage(
        getApiErrorMessage(error, {
          resourceName: "태스크 생성",
          conflictMessage: "현재 회의 상태에서는 태스크를 생성할 수 없습니다.",
        }),
      );
    },
  });

  const updateTaskMutation = useMutation({
    mutationFn: ({ taskId, ...body }: UpdateTaskRequest & { taskId: string }) => apiClient.updateTask(taskId, body),
    onSuccess: async () => {
      setErrorMessage(null);
      await queryClient.invalidateQueries({ queryKey: ["meeting-tasks", meetingId] });
      await queryClient.invalidateQueries({ queryKey: ["meeting-search", meetingId] });
    },
    onError: (error: unknown) => {
      setErrorMessage(
        getApiErrorMessage(error, {
          resourceName: "태스크 업데이트",
          conflictMessage: "현재 태스크 상태에서는 이 변경을 적용할 수 없습니다.",
        }),
      );
    },
  });

  if (tasksQuery.isLoading) {
    return <div className="panel px-6 py-6 text-sm text-muted">할 일을 불러오는 중이에요…</div>;
  }

  if (tasksQuery.error || !tasksQuery.data) {
    return <ApiErrorPanel error={tasksQuery.error} resourceName="태스크 목록" />;
  }

  const tasks = tasksQuery.data;
  const remaining = tasks.filter((task) => task.status !== "done" && task.status !== "cancelled").length;

  return (
    <div className="flex flex-col gap-4">
      {access.isHost ? (
        <form
          aria-label="할 일 추가"
          className="panel flex flex-wrap items-end gap-2.5 p-5"
          onSubmit={(event) => {
            event.preventDefault();
            setErrorMessage(null);
            createTaskMutation.mutate();
          }}
        >
          <label className="flex min-w-0 flex-[1_1_280px] flex-col gap-1.5 text-xs font-bold">
            할 일
            <input
              value={form.title}
              onChange={(event) => setForm((current) => ({ ...current, title: event.target.value }))}
              className="field h-11 text-sm font-normal"
              placeholder="예: 항공권 가격 비교해서 공유"
              required
            />
          </label>
          <label className="flex flex-[0_1_180px] flex-col gap-1.5 text-xs font-bold">
            담당 (선택)
            <select
              value={form.ownerUserId}
              onChange={(event) => setForm((current) => ({ ...current, ownerUserId: event.target.value }))}
              className="field h-11 text-sm font-normal"
            >
              <option value="">정하지 않음</option>
              {members.map((member) => (
                <option key={member.userId} value={member.userId}>
                  {member.displayName}
                </option>
              ))}
            </select>
          </label>
          <label className="flex flex-[0_1_220px] flex-col gap-1.5 text-xs font-bold">
            기한 (선택)
            <input
              type="datetime-local"
              value={form.dueAt}
              onChange={(event) => setForm((current) => ({ ...current, dueAt: event.target.value }))}
              className="field h-11 text-sm font-normal"
            />
          </label>
          <button type="submit" disabled={createTaskMutation.isPending} className="action-primary">
            {createTaskMutation.isPending ? "추가하는 중…" : "추가"}
          </button>
        </form>
      ) : (
        <HostOnlyNotice message="할 일 추가와 상태 변경은 호스트만 할 수 있어요." />
      )}

      {errorMessage ? (
        <p role="alert" className="m-0 text-sm text-danger">
          {errorMessage}
        </p>
      ) : null}

      <section aria-label="할 일 목록" className="rounded-[22px] bg-surface px-6 pb-3 pt-2">
        <div className="flex items-center justify-between pb-2 pt-4">
          <h2 className="m-0 text-[17px] font-bold">남은 할 일</h2>
          <span className="chip-voting">
            {remaining} / {tasks.length}
          </span>
        </div>
        {tasks.length ? (
          <ul className="m-0 list-none p-0">
            {tasks.map((task) => {
              const done = task.status === "done";
              return (
                <li key={task.id} className="flex flex-wrap items-center gap-x-4 gap-y-2 border-t border-line py-3.5">
                  <div className="min-w-0 flex-[1_1_260px]">
                    <div className={`text-[15px] ${done ? "text-muted line-through" : "font-bold"}`}>{task.title}</div>
                    <div className="num mt-0.5 text-xs text-muted">
                      {task.dueAt ? `기한 ${formatDateTime(task.dueAt)}` : "기한 없음"}
                    </div>
                  </div>
                  {access.isHost ? (
                    <select
                      aria-label={`${task.title} 담당`}
                      value={task.ownerUserId ?? ""}
                      disabled={updateTaskMutation.isPending}
                      onChange={(event) => {
                        if (event.target.value) {
                          updateTaskMutation.mutate({ taskId: task.id, ownerUserId: event.target.value });
                        }
                      }}
                      className={`field h-[38px] w-auto py-0 text-xs font-semibold ${task.ownerUserId ? "" : "text-risk-ink"}`}
                    >
                      <option value="" disabled>
                        담당 정하기
                      </option>
                      {members.map((member) => (
                        <option key={member.userId} value={member.userId}>
                          {member.displayName}
                        </option>
                      ))}
                    </select>
                  ) : (
                    <span className={`text-xs font-semibold ${task.ownerUserId ? "text-muted" : "text-risk-ink"}`}>
                      {memberName(members, task.ownerUserId) ?? (task.ownerUserId ? "멤버" : "담당 없음")}
                    </span>
                  )}
                  <div role="radiogroup" aria-label={`${task.title} 상태`} className="flex gap-0.5 rounded-[10px] bg-surface-2 p-[3px]">
                    {TASK_STATUSES.map((status) => {
                      const checked = task.status === status;
                      return (
                        <button
                          key={status}
                          type="button"
                          role="radio"
                          aria-checked={checked}
                          disabled={updateTaskMutation.isPending || !access.isHost}
                          onClick={() => updateTaskMutation.mutate({ taskId: task.id, status })}
                          className={`min-h-[34px] rounded-lg px-3 text-xs font-bold transition disabled:cursor-not-allowed ${
                            checked ? "bg-surface text-ink shadow-[0_1px_2px_rgb(0_0_0/0.08)]" : "text-muted"
                          }`}
                        >
                          {TASK_STATUS_LABELS[status]}
                        </button>
                      );
                    })}
                  </div>
                </li>
              );
            })}
          </ul>
        ) : (
          <p className="section-copy m-0 border-t border-line py-4">아직 할 일이 없어요.</p>
        )}
      </section>
    </div>
  );
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

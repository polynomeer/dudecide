"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { apiClient } from "@/lib/api/client";

const TASK_STATUSES = ["todo", "doing", "done"];

export function TasksWorkspace({ meetingId }: { meetingId: string }) {
  const queryClient = useQueryClient();
  const tasksQuery = useQuery({
    queryKey: ["meeting-tasks", meetingId],
    queryFn: () => apiClient.listTasks(meetingId),
  });
  const [form, setForm] = useState({
    title: "",
    dueAt: "",
  });
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const createTaskMutation = useMutation({
    mutationFn: () =>
      apiClient.createTask(meetingId, {
        title: form.title.trim(),
        dueAt: form.dueAt || undefined,
      }),
    onSuccess: async () => {
      setForm({ title: "", dueAt: "" });
      setErrorMessage(null);
      await queryClient.invalidateQueries({ queryKey: ["meeting-tasks", meetingId] });
      await queryClient.invalidateQueries({ queryKey: ["meeting-search", meetingId] });
    },
    onError: (error: Error) => {
      setErrorMessage(error.message);
    },
  });

  const updateTaskMutation = useMutation({
    mutationFn: ({ taskId, status }: { taskId: string; status: string }) =>
      apiClient.updateTask(taskId, { status }),
    onSuccess: async () => {
      setErrorMessage(null);
      await queryClient.invalidateQueries({ queryKey: ["meeting-tasks", meetingId] });
      await queryClient.invalidateQueries({ queryKey: ["meeting-search", meetingId] });
    },
    onError: (error: Error) => {
      setErrorMessage(error.message);
    },
  });

  if (tasksQuery.isLoading) {
    return <div className="panel px-6 py-6 text-sm text-ink/60">태스크 화면을 불러오는 중입니다.</div>;
  }

  if (tasksQuery.error || !tasksQuery.data) {
    return (
      <div className="panel px-6 py-6 text-sm text-ember">
        태스크 정보를 불러오지 못했습니다. 세션 상태와 백엔드 실행 상태를 확인해 주세요.
      </div>
    );
  }

  return (
    <div className="grid gap-5 xl:grid-cols-[0.78fr_1.22fr]">
      <section className="panel px-6 py-6">
        <div className="eyebrow">Create task</div>
        <h2 className="mt-2 font-[var(--font-display)] text-3xl font-semibold tracking-[-0.05em]">
          회의 태스크 추가
        </h2>
        <p className="mt-3 text-sm leading-6 text-ink/66">
          호스트 또는 코호스트가 후속 액션을 등록하고, 현재 상태를 `todo`, `doing`, `done`으로 관리할 수 있습니다.
        </p>

        <form
          className="mt-5 space-y-4"
          onSubmit={(event) => {
            event.preventDefault();
            setErrorMessage(null);
            createTaskMutation.mutate();
          }}
        >
          <label className="block">
            <span className="text-xs font-semibold uppercase tracking-[0.16em] text-ink/45">
              Title
            </span>
            <input
              value={form.title}
              onChange={(event) => setForm((current) => ({ ...current, title: event.target.value }))}
              className="mt-2 w-full rounded-[1.2rem] border border-[color:var(--color-line)] bg-white/85 px-4 py-3 text-sm outline-none transition focus:border-pine"
              placeholder="예: 숙소 후보 2곳 예산표 정리"
              required
            />
          </label>

          <label className="block">
            <span className="text-xs font-semibold uppercase tracking-[0.16em] text-ink/45">
              Due at
            </span>
            <input
              type="datetime-local"
              value={form.dueAt}
              onChange={(event) => setForm((current) => ({ ...current, dueAt: event.target.value }))}
              className="mt-2 w-full rounded-[1.2rem] border border-[color:var(--color-line)] bg-white/85 px-4 py-3 text-sm outline-none transition focus:border-pine"
            />
          </label>

          {errorMessage ? <div className="text-sm text-ember">{errorMessage}</div> : null}

          <div className="flex justify-end">
            <button
              type="submit"
              disabled={createTaskMutation.isPending}
              className="rounded-full bg-ink px-5 py-3 text-sm font-semibold text-mist transition hover:bg-pine disabled:cursor-not-allowed disabled:bg-ink/40"
            >
              {createTaskMutation.isPending ? "생성 중..." : "태스크 생성"}
            </button>
          </div>
        </form>
      </section>

      <section className="panel px-6 py-6">
        <div className="flex items-start justify-between gap-4">
          <div>
            <div className="eyebrow">Task list</div>
            <h3 className="mt-2 font-[var(--font-display)] text-3xl font-semibold tracking-[-0.05em]">
              현재 태스크
            </h3>
          </div>
          <div className="rounded-full border border-[color:var(--color-line)] bg-white/70 px-4 py-2 text-sm">
            총 {tasksQuery.data.length}건
          </div>
        </div>

        <div className="mt-5 space-y-3">
          {tasksQuery.data.length ? (
            tasksQuery.data.map((task) => (
              <article
                key={task.id}
                className="rounded-[1.5rem] border border-[color:var(--color-line)] bg-white/78 p-4"
              >
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <div className="text-lg font-semibold text-ink">{task.title}</div>
                    <div className="mt-2 text-sm text-ink/62">
                      생성 {formatDateTime(task.createdAt)}
                      {task.dueAt ? ` · 마감 ${formatDateTime(task.dueAt)}` : " · 마감 미설정"}
                    </div>
                  </div>
                  <select
                    value={task.status}
                    onChange={(event) =>
                      updateTaskMutation.mutate({ taskId: task.id, status: event.target.value })
                    }
                    disabled={updateTaskMutation.isPending}
                    className="rounded-full border border-[color:var(--color-line)] bg-white px-4 py-2 text-sm font-semibold outline-none transition focus:border-pine"
                  >
                    {TASK_STATUSES.map((status) => (
                      <option key={status} value={status}>
                        {status}
                      </option>
                    ))}
                  </select>
                </div>
              </article>
            ))
          ) : (
            <div className="rounded-[1.5rem] border border-dashed border-[color:var(--color-line)] bg-white/55 px-5 py-6 text-sm text-ink/58">
              아직 생성된 태스크가 없습니다.
            </div>
          )}
        </div>
      </section>
    </div>
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

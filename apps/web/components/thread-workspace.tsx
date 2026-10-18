"use client";

import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiErrorPanel } from "@/components/api-error-panel";
import { useForm } from "react-hook-form";
import { getApiErrorMessage } from "@/lib/api/error-display";
import { apiClient } from "@/lib/api/client";
import { useCurrentActor } from "@/lib/session";

type ThreadWorkspaceProps = {
  meetingId: string;
  threadId: string;
};

type ComposerValues = {
  body: string;
};

export function ThreadWorkspace({ meetingId, threadId }: ThreadWorkspaceProps) {
  const queryClient = useQueryClient();
  const actorQuery = useCurrentActor();
  const meetingQuery = useQuery({
    queryKey: ["meeting", meetingId],
    queryFn: () => apiClient.getMeeting(meetingId),
  });
  const messagesQuery = useQuery({
    queryKey: ["thread-messages", threadId],
    queryFn: () => apiClient.listMessages(threadId, undefined, 50),
  });
  const [olderMessages, setOlderMessages] = useState<Awaited<
    ReturnType<typeof apiClient.listMessages>
  >>([]);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const form = useForm<ComposerValues>({
    defaultValues: {
      body: "",
    },
  });

  const postMutation = useMutation({
    mutationFn: (values: ComposerValues) =>
      apiClient.postMessage(threadId, {
        body: values.body,
      }),
    onSuccess: async () => {
      form.reset();
      setErrorMessage(null);
      await queryClient.invalidateQueries({ queryKey: ["meeting", meetingId] });
      await queryClient.invalidateQueries({ queryKey: ["thread-messages", threadId] });
    },
    onError: (error: unknown) => {
      setErrorMessage(
        getApiErrorMessage(error, {
          resourceName: "메시지 등록",
          conflictMessage: "현재 스레드 상태에서는 메시지를 등록할 수 없습니다.",
        }),
      );
    },
  });

  const thread = useMemo(
    () => meetingQuery.data?.threads.find((item) => item.id === threadId),
    [meetingQuery.data, threadId],
  );

  const messages = [...olderMessages, ...(messagesQuery.data ?? [])];

  async function loadOlderMessages() {
    if (loadingOlder || messages.length === 0) {
      return;
    }

    const firstMessage = messages[0];
    setLoadingOlder(true);
    setErrorMessage(null);
    try {
      const nextPage = await apiClient.listMessages(threadId, firstMessage.createdAt, 20);
      if (nextPage.length > 0) {
        setOlderMessages((current) => [...current, ...nextPage]);
      }
    } catch (error) {
      setErrorMessage(
        getApiErrorMessage(error, {
          resourceName: "이전 메시지",
          fallbackMessage: "이전 메시지를 불러오지 못했습니다.",
        }),
      );
    } finally {
      setLoadingOlder(false);
    }
  }

  if (meetingQuery.isLoading || messagesQuery.isLoading) {
    return <div className="panel px-6 py-6 text-sm text-ink/60">스레드 작업공간을 불러오는 중입니다.</div>;
  }

  if (meetingQuery.error || messagesQuery.error || !thread) {
    return <ApiErrorPanel error={meetingQuery.error ?? messagesQuery.error} resourceName="스레드 작업공간" />;
  }

  return (
    <div className="grid gap-5 lg:grid-cols-[0.9fr_1.1fr]">
      <section className="panel px-6 py-6">
        <div className="eyebrow">Thread context</div>
        <h2 className="mt-2 font-[var(--font-display)] text-3xl font-semibold tracking-[-0.05em]">
          {thread.title}
        </h2>
        <div className="mt-5 space-y-3 text-sm text-ink/72">
          <ContextRow label="Category" value={thread.category} />
          <ContextRow label="Status" value={thread.status} />
          <ContextRow label="Messages" value={String(thread.messageCount)} />
          <ContextRow label="Decisions" value={String(thread.decisionCount)} />
          <ContextRow label="Open votes" value={String(thread.openVoteCount)} />
        </div>
        <div className="mt-6 rounded-[1.5rem] border border-[color:var(--color-line)] bg-white/72 p-4 text-sm leading-6 text-ink/72">
          다음 단계에서는 이 화면에 스레드 상태 업데이트, 메시지 수정, SSE 실시간 반영, 결정/투표 연계 액션이 추가될 예정입니다.
        </div>
      </section>

      <section className="panel flex min-h-[36rem] flex-col px-6 py-6">
        <div className="flex items-start justify-between gap-4 border-b border-[color:var(--color-line)] pb-4">
          <div>
            <div className="eyebrow">Discussion</div>
            <h3 className="mt-2 font-[var(--font-display)] text-2xl font-semibold tracking-[-0.04em]">
              메시지 흐름
            </h3>
          </div>
          <div className="rounded-full border border-[color:var(--color-line)] bg-white/70 px-4 py-2 text-sm">
            {actorQuery.data?.displayName ?? "Local Host"}
          </div>
        </div>

        <div className="flex-1 space-y-4 py-5">
          {messages.length > 0 ? (
            <button
              type="button"
              onClick={loadOlderMessages}
              disabled={loadingOlder}
              className="rounded-full border border-[color:var(--color-line)] px-4 py-2 text-sm font-semibold transition hover:bg-white/80 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {loadingOlder ? "이전 메시지를 불러오는 중..." : "이전 메시지 더 보기"}
            </button>
          ) : null}

          <div className="space-y-3">
            {messages.length === 0 ? (
              <div className="rounded-[1.5rem] border border-dashed border-[color:var(--color-line)] bg-white/50 px-5 py-6 text-sm text-ink/58">
                아직 메시지가 없습니다. 첫 번째 계획 메모를 남겨 주세요.
              </div>
            ) : (
              messages.map((message) => {
                const isOwnMessage = message.senderUserId !== null && actorQuery.data?.userId === message.senderUserId;
                return (
                  <article
                    key={message.id}
                    className={[
                      "rounded-[1.5rem] border px-4 py-4",
                      isOwnMessage
                        ? "border-pine/20 bg-pine/10"
                        : "border-[color:var(--color-line)] bg-white/72",
                    ].join(" ")}
                  >
                    <div className="flex items-center justify-between gap-4 text-xs uppercase tracking-[0.16em] text-ink/45">
                      <span>{isOwnMessage ? "You" : message.speaker.displayName}</span>
                      <span>{formatDateTime(message.createdAt)}</span>
                    </div>
                    <p className="mt-3 whitespace-pre-wrap text-sm leading-6 text-ink/86">{message.body}</p>
                    {message.editedAt ? (
                      <div className="mt-3 text-xs text-ink/45">Edited {formatDateTime(message.editedAt)}</div>
                    ) : null}
                  </article>
                );
              })
            )}
          </div>
        </div>

        <form
          className="border-t border-[color:var(--color-line)] pt-4"
          onSubmit={form.handleSubmit((values) => {
            setErrorMessage(null);
            postMutation.mutate(values);
          })}
        >
          <label className="mb-2 block text-sm font-semibold">새 메시지</label>
          <textarea
            {...form.register("body", { required: true, minLength: 2 })}
            rows={5}
            className="w-full rounded-[1.5rem] border border-[color:var(--color-line)] bg-white/85 px-4 py-3 outline-none transition focus:border-ink"
            placeholder="예: 9월 둘째 주는 전원 가능하고, 가평이면 차량 2대로도 이동 가능합니다."
          />
          {form.formState.errors.body ? (
            <p className="mt-2 text-sm text-ember">메시지는 두 글자 이상 입력해 주세요.</p>
          ) : null}
          {errorMessage ? <p className="mt-2 text-sm text-ember">{errorMessage}</p> : null}
          <div className="mt-4 flex justify-end">
            <button
              type="submit"
              disabled={postMutation.isPending}
              className="rounded-full bg-ink px-5 py-3 text-sm font-semibold text-mist transition hover:bg-pine disabled:cursor-not-allowed disabled:opacity-60"
            >
              {postMutation.isPending ? "등록 중..." : "메시지 등록"}
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}

function ContextRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between rounded-full border border-[color:var(--color-line)] bg-white/72 px-4 py-3">
      <span className="text-xs font-semibold uppercase tracking-[0.16em] text-ink/45">{label}</span>
      <span className="font-medium text-ink">{value}</span>
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

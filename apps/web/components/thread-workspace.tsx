"use client";

import Link from "next/link";
import type { Route } from "next";
import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiErrorPanel } from "@/components/api-error-panel";
import { useForm } from "react-hook-form";
import { MeetingPageHeader } from "@/components/meeting/meeting-page-header";
import { getApiErrorMessage } from "@/lib/api/error-display";
import { apiClient } from "@/lib/api/client";
import { avatarColor, initialOf } from "@/lib/display";
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
    return <div className="panel px-6 py-6 text-sm text-muted">토픽을 불러오는 중이에요…</div>;
  }

  if (meetingQuery.error || messagesQuery.error || !thread || !meetingQuery.data) {
    return <ApiErrorPanel error={meetingQuery.error ?? messagesQuery.error} resourceName="스레드 작업공간" />;
  }

  const meeting = meetingQuery.data;

  return (
    <>
      <MeetingPageHeader
        kicker={`토픽 · 메시지 ${thread.messageCount} · 결정 ${thread.decisionCount}${thread.openVoteCount ? ` · 투표 ${thread.openVoteCount}` : ""}`}
        title={`# ${thread.title}`}
        actions={
          thread.decisionCount ? (
            <Link href={`/meetings/${meetingId}/decisions` as Route} className="action-secondary">
              이 토픽의 결정 보기
            </Link>
          ) : null
        }
      />

      <nav aria-label="토픽" className="-mx-1 flex gap-1.5 overflow-x-auto px-1 pb-1">
        {meeting.threads.map((item) => {
          const active = item.id === threadId;
          return (
            <Link
              key={item.id}
              href={`/meetings/${meetingId}/threads/${item.id}` as Route}
              aria-current={active ? "page" : undefined}
              className={`inline-flex h-9 flex-none items-center gap-1 whitespace-nowrap rounded-full border px-3 text-[13px] font-semibold ${
                active ? "border-ink bg-ink text-on-ink" : "border-line bg-surface text-ink hover:bg-surface-2"
              }`}
            >
              {item.title}
              {item.openVoteCount ? <span className={active ? "text-[#F2B33D]" : "text-accent-text"}>{item.openVoteCount}</span> : null}
            </Link>
          );
        })}
      </nav>

      <section aria-label="메시지" className="panel flex min-h-[32rem] flex-col p-0">
        <div className="flex flex-1 flex-col gap-3 px-5 py-5">
          {messages.length > 0 ? (
            <button type="button" onClick={loadOlderMessages} disabled={loadingOlder} className="btn-ghost self-center text-xs">
              {loadingOlder ? "이전 메시지를 불러오는 중…" : "이전 메시지 더 보기"}
            </button>
          ) : null}

          {messages.length === 0 ? (
            <div className="empty-state">아직 메시지가 없어요. 첫 이야기를 남겨 주세요.</div>
          ) : (
            messages.map((message) => {
              const isOwnMessage = message.senderUserId !== null && actorQuery.data?.userId === message.senderUserId;
              return (
                <article key={message.id} className={`flex gap-2 ${isOwnMessage ? "flex-row-reverse" : ""}`}>
                  {isOwnMessage ? null : (
                    <span
                      aria-hidden="true"
                      className="flex h-8 w-8 flex-none items-center justify-center rounded-full text-xs font-bold text-[#15171C]"
                      style={{ background: avatarColor(message.speaker.displayName) }}
                    >
                      {initialOf(message.speaker.displayName)}
                    </span>
                  )}
                  <div className={`flex max-w-[min(34rem,80%)] flex-col ${isOwnMessage ? "items-end" : ""}`}>
                    <div className="mb-1 text-[11px] font-semibold text-muted">
                      {isOwnMessage ? "나" : message.speaker.displayName} · {formatDateTime(message.createdAt)}
                      {message.origin === "imported" ? " · 단톡방" : ""}
                      {message.editedAt ? " · 수정됨" : ""}
                    </div>
                    <p
                      className={`m-0 whitespace-pre-wrap px-3 py-2.5 text-sm leading-[1.45] ${
                        isOwnMessage
                          ? "rounded-[16px_4px_16px_16px] bg-accent-soft text-ink"
                          : "rounded-[4px_16px_16px_16px] bg-surface-2 text-ink"
                      }`}
                    >
                      {message.body}
                    </p>
                  </div>
                </article>
              );
            })
          )}
        </div>

        <form
          className="flex flex-col gap-2 border-t border-line px-4 py-3"
          onSubmit={form.handleSubmit((values) => {
            setErrorMessage(null);
            postMutation.mutate(values);
          })}
        >
          <div className="flex items-end gap-2">
            <label htmlFor="thread-message" className="sr-only">
              {thread.title} 토픽에 메시지 보내기
            </label>
            <textarea
              id="thread-message"
              {...form.register("body", { required: true, minLength: 2 })}
              rows={2}
              className="field min-h-[46px] flex-1 resize-y"
              placeholder={`${thread.title} 토픽에 이야기하기`}
            />
            <button
              type="submit"
              aria-label="보내기"
              disabled={postMutation.isPending}
              className="flex h-[46px] w-[46px] flex-none items-center justify-center rounded-[14px] bg-accent text-on-accent disabled:opacity-40"
            >
              <svg aria-hidden="true" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M5 12h14M13 6l6 6-6 6" />
              </svg>
            </button>
          </div>
          {form.formState.errors.body ? <p className="m-0 text-sm text-danger">메시지는 두 글자 이상 입력해 주세요.</p> : null}
          {errorMessage ? <p className="m-0 text-sm text-danger">{errorMessage}</p> : null}
        </form>
      </section>
    </>
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

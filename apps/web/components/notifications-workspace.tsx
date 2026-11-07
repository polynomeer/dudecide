"use client";

import Link from "next/link";
import type { Route } from "next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiErrorPanel } from "@/components/api-error-panel";
import { memberName, useMeetingMembers } from "@/lib/members";
import { apiClient, type NotificationView } from "@/lib/api/client";
import { formatShortDateTime, notificationSummary } from "@/lib/display";

const KIND_STYLE: Record<string, { icon: string; className: string }> = {
  decision_confirmed: { icon: "✓", className: "bg-ink text-on-ink" },
  vote_closed: { icon: "!", className: "bg-accent text-on-accent" },
  report_ready: { icon: "“", className: "bg-accent-soft text-accent-soft-ink" },
  invite_joined: { icon: "+", className: "bg-[#CFE9DE] text-[#15171C]" },
  invite_created: { icon: "↗", className: "bg-surface-2 text-ink" },
};

function notificationHref(meetingId: string, notification: NotificationView): string {
  const payload = notification.payload ?? {};
  switch (notification.type) {
    case "decision_confirmed":
      return typeof payload.decisionId === "string"
        ? `/meetings/${meetingId}/decisions?decision=${payload.decisionId}`
        : `/meetings/${meetingId}/decisions`;
    case "vote_closed":
      return typeof payload.voteId === "string" ? `/votes/${payload.voteId}` : `/meetings/${meetingId}`;
    case "report_ready":
      return `/meetings/${meetingId}/reports/latest`;
    default:
      return `/meetings/${meetingId}`;
  }
}

function isToday(value: string) {
  const date = new Date(value);
  const now = new Date();
  return date.toDateString() === now.toDateString();
}

export function NotificationsWorkspace({ meetingId }: { meetingId: string }) {
  const { data: members } = useMeetingMembers(meetingId);
  const queryClient = useQueryClient();
  const queryKey = ["meeting-notifications", meetingId];
  const notificationsQuery = useQuery({
    queryKey,
    queryFn: () => apiClient.listNotifications(meetingId),
  });

  // Mark read optimistically so the bell badge updates before the request returns.
  const markLocally = (ids: string[] | "all") =>
    queryClient.setQueryData<NotificationView[]>(queryKey, (current) =>
      current?.map((item) =>
        (ids === "all" || ids.includes(item.id)) && !item.readAt ? { ...item, readAt: new Date().toISOString() } : item,
      ),
    );
  const markOne = useMutation({
    mutationFn: (notificationId: string) => apiClient.markNotificationRead(meetingId, notificationId),
    onMutate: (notificationId) => markLocally([notificationId]),
    onSettled: () => queryClient.invalidateQueries({ queryKey }),
  });
  const markAll = useMutation({
    mutationFn: () => apiClient.markAllNotificationsRead(meetingId),
    onMutate: () => markLocally("all"),
    onSettled: () => queryClient.invalidateQueries({ queryKey }),
  });

  if (notificationsQuery.isLoading) {
    return <div className="panel px-6 py-6 text-sm text-muted">알림 목록을 불러오는 중입니다.</div>;
  }

  if (notificationsQuery.error || !notificationsQuery.data) {
    return <ApiErrorPanel error={notificationsQuery.error} resourceName="알림 목록" />;
  }

  const notifications = notificationsQuery.data;
  if (notifications.length === 0) {
    return <div className="empty-state">현재 확인할 알림이 없습니다.</div>;
  }

  const unreadCount = notifications.filter((item) => !item.readAt).length;
  const groups = [
    { label: "오늘", items: notifications.filter((item) => isToday(item.createdAt)) },
    { label: "이전", items: notifications.filter((item) => !isToday(item.createdAt)) },
  ].filter((group) => group.items.length > 0);

  return (
    <div className="flex max-w-3xl flex-col gap-2">
      <div className="flex items-center justify-between px-1">
        <span className="num text-xs font-semibold text-accent-text">
          {unreadCount ? `안 읽음 ${unreadCount}` : "모두 읽었어요"}
        </span>
        <button
          type="button"
          onClick={() => markAll.mutate()}
          disabled={unreadCount === 0 || markAll.isPending}
          className="min-h-[44px] text-[13px] font-bold text-accent-text disabled:text-muted"
        >
          모두 읽음
        </button>
      </div>
      {groups.map((group) => (
        <section key={group.label} aria-label={group.label} className="flex flex-col gap-2">
          <h2 className="mono-label m-0 px-1 pt-1">{group.label}</h2>
          {group.items.map((notification) => {
            const kind = KIND_STYLE[notification.type] ?? { icon: "•", className: "bg-surface-2 text-ink" };
            const unread = !notification.readAt;
            return (
              <Link
                key={notification.id}
                href={notificationHref(meetingId, notification) as Route}
                onClick={() => {
                  if (unread) {
                    markOne.mutate(notification.id);
                  }
                }}
                className={`flex items-start gap-3 rounded-[14px] bg-surface px-3.5 py-3 text-ink transition hover:ring-1 hover:ring-accent/40 ${
                  unread ? "ring-1 ring-accent/40" : ""
                }`}
              >
                <span
                  aria-hidden="true"
                  className={`flex h-9 w-9 flex-none items-center justify-center rounded-[10px] text-sm font-extrabold ${kind.className}`}
                >
                  {kind.icon}
                </span>
                <span className={`min-w-0 flex-1 text-sm leading-[1.45] ${unread ? "font-bold" : "font-medium text-ink/80"}`}>
                  {notificationSummary(notification, (userId) => memberName(members, userId))}
                </span>
                <span className="flex flex-none flex-col items-end gap-1.5">
                  <span className="num text-[11px] text-muted">{formatShortDateTime(notification.createdAt)}</span>
                  {unread ? <span aria-label="안 읽음" className="h-2 w-2 rounded-full bg-accent" /> : null}
                </span>
              </Link>
            );
          })}
        </section>
      ))}
    </div>
  );
}

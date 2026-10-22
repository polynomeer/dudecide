"use client";

import Link from "next/link";
import type { Route } from "next";
import { useQuery } from "@tanstack/react-query";
import { ApiErrorPanel } from "@/components/api-error-panel";
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
  const notificationsQuery = useQuery({
    queryKey: ["meeting-notifications", meetingId],
    queryFn: () => apiClient.listNotifications(meetingId),
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

  const groups = [
    { label: "오늘", items: notifications.filter((item) => isToday(item.createdAt)) },
    { label: "이전", items: notifications.filter((item) => !isToday(item.createdAt)) },
  ].filter((group) => group.items.length > 0);

  return (
    <div className="flex max-w-3xl flex-col gap-2">
      {groups.map((group) => (
        <section key={group.label} aria-label={group.label} className="flex flex-col gap-2">
          <h2 className="mono-label m-0 px-1 pt-1">{group.label}</h2>
          {group.items.map((notification) => {
            const kind = KIND_STYLE[notification.type] ?? { icon: "•", className: "bg-surface-2 text-ink" };
            return (
              <Link
                key={notification.id}
                href={notificationHref(meetingId, notification) as Route}
                className="flex items-start gap-3 rounded-[14px] bg-surface px-3.5 py-3 text-ink transition hover:ring-1 hover:ring-accent/40"
              >
                <span
                  aria-hidden="true"
                  className={`flex h-9 w-9 flex-none items-center justify-center rounded-[10px] text-sm font-extrabold ${kind.className}`}
                >
                  {kind.icon}
                </span>
                <span className="min-w-0 flex-1 text-sm font-semibold leading-[1.45]">{notificationSummary(notification)}</span>
                <span className="num flex-none text-[11px] text-muted">{formatShortDateTime(notification.createdAt)}</span>
              </Link>
            );
          })}
        </section>
      ))}
    </div>
  );
}

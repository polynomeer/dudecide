"use client";

import { useQuery } from "@tanstack/react-query";
import { ApiErrorPanel } from "@/components/api-error-panel";
import { apiClient } from "@/lib/api/client";

export function NotificationsWorkspace({ meetingId }: { meetingId: string }) {
  const notificationsQuery = useQuery({
    queryKey: ["meeting-notifications", meetingId],
    queryFn: () => apiClient.listNotifications(meetingId),
  });

  if (notificationsQuery.isLoading) {
    return <div className="panel px-6 py-6 text-sm text-ink/60">알림 목록을 불러오는 중입니다.</div>;
  }

  if (notificationsQuery.error || !notificationsQuery.data) {
    return <ApiErrorPanel error={notificationsQuery.error} resourceName="알림 목록" />;
  }

  return (
    <div className="panel px-6 py-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <div className="info-chip">Notifications</div>
          <h2 className="mt-4 font-display text-4xl leading-[0.96]">
            회의 알림 피드
          </h2>
          <p className="section-copy mt-3">
            현재 로컬 사용자 기준으로 저장된 인앱 알림을 시간순으로 확인합니다.
          </p>
        </div>
        <div className="info-chip normal-case tracking-[0.02em]">총 {notificationsQuery.data.length}건</div>
      </div>

      <div className="mt-5 space-y-3">
        {notificationsQuery.data.length ? (
          notificationsQuery.data.map((notification) => (
            <article key={notification.id} className="soft-card">
              <div className="flex items-center justify-between gap-4">
                <span className="info-chip">
                  {notification.type}
                </span>
                <span className="text-xs text-ink/45">{formatDateTime(notification.createdAt)}</span>
              </div>
              <div className="mt-3 text-sm text-ink/64">
                {notification.sentAt ? `발송 처리 ${formatDateTime(notification.sentAt)}` : "발송 처리 전"}
              </div>
              <pre className="mt-3 overflow-x-auto rounded-[1.25rem] bg-surface-2 px-4 py-4 text-xs leading-6 text-ink/76">
                {JSON.stringify(notification.payload, null, 2)}
              </pre>
            </article>
          ))
        ) : (
          <div className="empty-state">현재 확인할 알림이 없습니다.</div>
        )}
      </div>
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

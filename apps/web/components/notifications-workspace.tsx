"use client";

import { useQuery } from "@tanstack/react-query";
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
    return (
      <div className="panel px-6 py-6 text-sm text-ember">
        알림 목록을 불러오지 못했습니다. 세션 상태와 백엔드 실행 상태를 확인해 주세요.
      </div>
    );
  }

  return (
    <div className="panel px-6 py-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <div className="eyebrow">Notifications</div>
          <h2 className="mt-2 font-[var(--font-display)] text-3xl font-semibold tracking-[-0.05em]">
            회의 알림 피드
          </h2>
          <p className="mt-3 text-sm leading-6 text-ink/66">
            현재 로컬 사용자 기준으로 저장된 인앱 알림을 시간순으로 확인합니다.
          </p>
        </div>
        <div className="rounded-full border border-[color:var(--color-line)] bg-white/70 px-4 py-2 text-sm">
          총 {notificationsQuery.data.length}건
        </div>
      </div>

      <div className="mt-5 space-y-3">
        {notificationsQuery.data.length ? (
          notificationsQuery.data.map((notification) => (
            <article
              key={notification.id}
              className="rounded-[1.5rem] border border-[color:var(--color-line)] bg-white/78 p-4"
            >
              <div className="flex items-center justify-between gap-4">
                <span className="rounded-full border border-[color:var(--color-line)] px-3 py-1 text-xs font-semibold uppercase tracking-[0.14em] text-ink/60">
                  {notification.type}
                </span>
                <span className="text-xs text-ink/45">{formatDateTime(notification.createdAt)}</span>
              </div>
              <div className="mt-3 text-sm text-ink/64">
                {notification.sentAt ? `발송 처리 ${formatDateTime(notification.sentAt)}` : "발송 처리 전"}
              </div>
              <pre className="mt-3 overflow-x-auto rounded-[1.25rem] bg-mist/85 px-4 py-4 text-xs leading-6 text-ink/76">
                {JSON.stringify(notification.payload, null, 2)}
              </pre>
            </article>
          ))
        ) : (
          <div className="rounded-[1.5rem] border border-dashed border-[color:var(--color-line)] bg-white/55 px-5 py-6 text-sm text-ink/58">
            현재 확인할 알림이 없습니다.
          </div>
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

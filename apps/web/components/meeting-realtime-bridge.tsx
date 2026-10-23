"use client";

import { useEffect, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL?.replace(/\/$/, "") ?? "http://localhost:8080";

type RealtimeStatus = "connecting" | "connected" | "reconnecting";

const STATUS_LABELS: Record<RealtimeStatus, string> = {
  connecting: "연결 중",
  connected: "연결됨",
  reconnecting: "다시 연결 중",
};

export function MeetingRealtimeBridge({
  meetingId,
  voteId,
}: {
  meetingId: string;
  voteId?: string;
}) {
  const queryClient = useQueryClient();
  const retryTimerRef = useRef<number | null>(null);
  const [status, setStatus] = useState<RealtimeStatus>("connecting");

  useEffect(() => {
    let eventSource: EventSource | null = null;
    let disposed = false;

    const clearRetryTimer = () => {
      if (retryTimerRef.current !== null) {
        window.clearTimeout(retryTimerRef.current);
        retryTimerRef.current = null;
      }
    };

    const scheduleReconnect = () => {
      if (disposed || retryTimerRef.current !== null) {
        return;
      }
      setStatus("reconnecting");
      retryTimerRef.current = window.setTimeout(() => {
        retryTimerRef.current = null;
        connect();
      }, 3000);
    };

    const invalidateMeetingQueries = async (eventType: string) => {
      const invalidations = [
        queryClient.invalidateQueries({ queryKey: ["meeting", meetingId] }),
        queryClient.invalidateQueries({ queryKey: ["meeting-members", meetingId] }),
        queryClient.invalidateQueries({ queryKey: ["meeting-decisions", meetingId] }),
        queryClient.invalidateQueries({ queryKey: ["meeting-search", meetingId] }),
        queryClient.invalidateQueries({ queryKey: ["meeting-report", meetingId] }),
        queryClient.invalidateQueries({ queryKey: ["meeting-tasks", meetingId] }),
        queryClient.invalidateQueries({ queryKey: ["meeting-notifications", meetingId] }),
      ];

      if (voteId) {
        invalidations.push(queryClient.invalidateQueries({ queryKey: ["vote", voteId] }));
      }

      await Promise.all(invalidations);

      if (
        eventType === "message.created" ||
        eventType === "message.updated" ||
        eventType === "thread.updated"
      ) {
        await queryClient.invalidateQueries({ queryKey: ["thread-messages"] });
      }

      if (eventType === "decision.updated") {
        await queryClient.invalidateQueries({ queryKey: ["decision-detail"] });
      }

      if (eventType.startsWith("import.")) {
        await Promise.all([
          queryClient.invalidateQueries({ queryKey: ["chat-import"] }),
          queryClient.invalidateQueries({ queryKey: ["meeting-imports", meetingId] }),
        ]);
      }
    };

    const connect = () => {
      clearRetryTimer();
      setStatus("connecting");

      eventSource = new EventSource(`${API_BASE_URL}/api/v1/meetings/${meetingId}/events`, {
        withCredentials: true,
      });

      const handleEvent = async (eventType: string) => {
        setStatus("connected");
        await invalidateMeetingQueries(eventType);
      };

      eventSource.addEventListener("open", () => {
        setStatus("connected");
      });
      eventSource.addEventListener("meeting.updated", () => void handleEvent("meeting.updated"));
      eventSource.addEventListener("thread.updated", () => void handleEvent("thread.updated"));
      eventSource.addEventListener("message.created", () => void handleEvent("message.created"));
      eventSource.addEventListener("message.updated", () => void handleEvent("message.updated"));
      eventSource.addEventListener("decision.updated", () => void handleEvent("decision.updated"));
      eventSource.addEventListener("vote.updated", () => void handleEvent("vote.updated"));
      eventSource.addEventListener("report.updated", () => void handleEvent("report.updated"));
      eventSource.addEventListener("notification.created", () =>
        void handleEvent("notification.created"),
      );
      ["import.progress", "import.completed", "import.failed", "import.deleted"].forEach((eventType) =>
        eventSource?.addEventListener(eventType, () => void handleEvent(eventType)),
      );
      eventSource.onerror = () => {
        eventSource?.close();
        scheduleReconnect();
      };
    };

    connect();

    return () => {
      disposed = true;
      clearRetryTimer();
      eventSource?.close();
    };
  }, [meetingId, queryClient, voteId]);

  return (
    <div
      role="status"
      className="num fixed bottom-[84px] right-4 z-50 lg:bottom-4 inline-flex items-center gap-1.5 rounded-full border border-line bg-surface px-3 py-1.5 text-[11px] font-semibold text-muted"
    >
      <span
        aria-hidden="true"
        className={`h-1.5 w-1.5 rounded-full ${status === "connected" ? "bg-[#2FA36B]" : "bg-[#F2B33D]"}`}
      />
      Realtime {STATUS_LABELS[status]}
    </div>
  );
}

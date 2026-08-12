"use client";

import { useEffect, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL?.replace(/\/$/, "") ?? "http://localhost:8080";

type RealtimeStatus = "connecting" | "connected" | "reconnecting";

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
    <div className="fixed bottom-4 right-4 z-50 rounded-full border border-[color:var(--color-line)] bg-white/88 px-3 py-2 text-xs font-semibold uppercase tracking-[0.14em] text-ink/58 shadow-[0_16px_30px_rgba(15,23,42,0.08)] backdrop-blur">
      Realtime {status}
    </div>
  );
}

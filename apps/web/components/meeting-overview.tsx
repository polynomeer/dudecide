"use client";

import Link from "next/link";
import type { Route } from "next";
import { useQuery } from "@tanstack/react-query";
import { ApiErrorPanel } from "@/components/api-error-panel";
import { apiClient } from "@/lib/api/client";
import { VoteStudio } from "@/components/vote-studio";

export function MeetingOverview({ meetingId }: { meetingId: string }) {
  const { data: meeting, isLoading, error } = useQuery({
    queryKey: ["meeting", meetingId],
    queryFn: () => apiClient.getMeeting(meetingId),
  });

  if (isLoading) {
    return <div className="panel px-6 py-6 text-sm text-ink/60">Loading meeting workspace...</div>;
  }

  if (error || !meeting) {
    return <ApiErrorPanel error={error} resourceName="회의 작업공간" />;
  }

  return (
    <div className="space-y-5">
      <div className="grid gap-5 lg:grid-cols-[1.2fr_0.8fr]">
        <section className="panel overflow-hidden px-6 py-6">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="info-chip">Meeting overview</div>
            <div className="info-chip">{meeting.status}</div>
          </div>
          <h2 className="mt-4 max-w-xl font-[var(--font-display)] text-4xl font-semibold leading-[0.96] tracking-[-0.055em]">
            {meeting.title}
          </h2>
          <p className="mt-3 max-w-lg text-sm leading-6 text-[color:rgba(13,21,38,0.68)]">
            Track the current planning state, jump into the hottest threads, and move the group toward a confirmed call.
          </p>
          <div className="mt-6 grid gap-3 sm:grid-cols-2">
            <Metric label="Status" value={meeting.status} />
            <Metric label="Meeting type" value={meeting.meetingType} />
            <Metric label="Members" value={String(meeting.memberCount)} />
            <Metric label="Open votes" value={String(meeting.openVoteCount)} />
          </div>
          <div className="section-rule my-6" />
          <div className="flex flex-wrap gap-2">
            <Link
              href={`/meetings/${meeting.id}/decisions` as Route}
              className="action-primary"
            >
              Decision board
            </Link>
            <Link
              href={`/meetings/${meeting.id}/search` as Route}
              className="action-secondary"
            >
              Search
            </Link>
            <Link
              href={`/meetings/${meeting.id}/reports/latest` as Route}
              className="action-secondary"
            >
              Report
            </Link>
            <Link
              href={`/meetings/${meeting.id}/tasks` as Route}
              className="action-secondary"
            >
              Tasks
            </Link>
            <Link
              href={`/meetings/${meeting.id}/notifications` as Route}
              className="action-secondary"
            >
              Notifications
            </Link>
          </div>
        </section>
        <section className="panel px-6 py-6">
          <div className="info-chip">Active threads</div>
          <ul className="mt-4 space-y-3">
            {meeting.threads.map((thread) => (
              <li key={thread.id} className="metric-card">
                <Link href={`/meetings/${meeting.id}/threads/${thread.id}`} className="block">
                  <div className="flex items-center justify-between gap-3">
                    <div className="text-xs font-semibold uppercase tracking-[0.16em] text-[color:var(--color-steel)]">
                      {thread.category}
                    </div>
                    <span className="info-chip">{thread.status}</span>
                  </div>
                  <div className="mt-3 text-xl font-semibold tracking-[-0.03em]">{thread.title}</div>
                  <div className="mt-2 text-sm text-[color:rgba(13,21,38,0.62)]">
                    Messages {thread.messageCount} · Decisions {thread.decisionCount} · Open votes{" "}
                    {thread.openVoteCount}
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        </section>
      </div>

      <VoteStudio meetingId={meetingId} />
    </div>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="metric-card">
      <div className="text-xs font-semibold uppercase tracking-[0.16em] text-[color:var(--color-steel)]">{label}</div>
      <div className="mt-2 font-[var(--font-display)] text-3xl font-semibold tracking-[-0.05em]">
        {value}
      </div>
    </div>
  );
}

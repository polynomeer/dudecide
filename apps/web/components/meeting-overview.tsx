"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
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
    return (
      <div className="panel px-6 py-6 text-sm text-ember">
        Failed to load meeting details. Make sure the backend is running and the current session has
        meeting access.
      </div>
    );
  }

  return (
    <div className="space-y-5">
      <div className="grid gap-5 lg:grid-cols-[1.2fr_0.8fr]">
        <section className="panel px-6 py-6">
          <div className="eyebrow">Overview</div>
          <h2 className="mt-2 font-[var(--font-display)] text-3xl font-semibold tracking-[-0.05em]">
            {meeting.title}
          </h2>
          <div className="mt-5 grid gap-3 sm:grid-cols-2">
            <Metric label="Status" value={meeting.status} />
            <Metric label="Meeting type" value={meeting.meetingType} />
            <Metric label="Members" value={String(meeting.memberCount)} />
            <Metric label="Open votes" value={String(meeting.openVoteCount)} />
          </div>
          <div className="mt-5 flex flex-wrap gap-2">
            <Link
              href={`/meetings/${meeting.id}/decisions`}
              className="rounded-full border border-[color:var(--color-line)] bg-white/72 px-4 py-2 text-sm font-semibold transition hover:bg-white/90"
            >
              Decision board
            </Link>
          </div>
        </section>
        <section className="panel px-6 py-6">
          <div className="eyebrow">Threads</div>
          <ul className="mt-4 space-y-3">
            {meeting.threads.map((thread) => (
              <li key={thread.id} className="rounded-[1.5rem] border border-[color:var(--color-line)] bg-white/70 p-4">
                <Link href={`/meetings/${meeting.id}/threads/${thread.id}`} className="block">
                  <div className="text-xs font-semibold uppercase tracking-[0.16em] text-ink/45">
                    {thread.category}
                  </div>
                  <div className="mt-1 text-lg font-semibold">{thread.title}</div>
                  <div className="mt-2 text-sm text-ink/62">
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
    <div className="rounded-[1.5rem] border border-[color:var(--color-line)] bg-white/70 p-4">
      <div className="text-xs font-semibold uppercase tracking-[0.16em] text-ink/45">{label}</div>
      <div className="mt-2 font-[var(--font-display)] text-2xl font-semibold tracking-[-0.04em]">
        {value}
      </div>
    </div>
  );
}

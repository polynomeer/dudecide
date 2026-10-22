"use client";

import Link from "next/link";
import type { Route } from "next";
import { useQuery } from "@tanstack/react-query";
import { ApiErrorPanel } from "@/components/api-error-panel";
import { InviteLinkButton } from "@/components/meeting/invite-link-button";
import { MeetingPageHeader } from "@/components/meeting/meeting-page-header";
import { VoteStudio } from "@/components/vote-studio";
import { apiClient, type DecisionResponse } from "@/lib/api/client";
import {
  DECISION_COLUMNS,
  decisionColumn,
  decisionProgressColor,
  decisionStatusLabel,
  decisionTypeLabel,
  formatShortDateTime,
  normalizeDecisionStatus,
  notificationSummary,
} from "@/lib/display";
import { useMeetingHostAccess } from "@/lib/session";

const MEETING_TYPE_LABELS: Record<string, string> = {
  trip: "여행",
  travel: "여행",
  dinner: "모임",
  meetup: "모임",
  workshop: "워크숍",
};

export function MeetingOverview({ meetingId }: { meetingId: string }) {
  const { isHost } = useMeetingHostAccess(meetingId);
  const { data: meeting, isLoading, error } = useQuery({
    queryKey: ["meeting", meetingId],
    queryFn: () => apiClient.getMeeting(meetingId),
  });
  const decisionsQuery = useQuery({
    queryKey: ["meeting-decisions", meetingId],
    queryFn: () => apiClient.listDecisions(meetingId),
  });
  const tasksQuery = useQuery({
    queryKey: ["meeting-tasks", meetingId],
    queryFn: () => apiClient.listTasks(meetingId),
  });
  const notificationsQuery = useQuery({
    queryKey: ["meeting-notifications", meetingId],
    queryFn: () => apiClient.listNotifications(meetingId),
  });

  if (isLoading) {
    return <div className="panel px-6 py-6 text-sm text-muted">모임을 불러오는 중이에요…</div>;
  }

  if (error || !meeting) {
    return <ApiErrorPanel error={error} resourceName="회의 작업공간" />;
  }

  const base = `/meetings/${meeting.id}`;
  const decisions = decisionsQuery.data ?? [];
  const confirmed = decisions.filter((decision) => normalizeDecisionStatus(decision.status) === "confirmed");
  const openTasks = (tasksQuery.data ?? []).filter((task) => !["done", "completed", "cancelled"].includes(task.status.toLowerCase()));
  const discussing = decisions.filter((decision) => normalizeDecisionStatus(decision.status) === "in_discussion");
  const percent = decisions.length ? Math.round((confirmed.length / decisions.length) * 100) : 0;
  const typeLabel = MEETING_TYPE_LABELS[meeting.meetingType.toLowerCase()] ?? meeting.meetingType;
  const kicker = [typeLabel, meeting.originRegion, `멤버 ${meeting.memberCount}명`].filter(Boolean).join(" · ");

  return (
    <>
      <MeetingPageHeader
        kicker={kicker}
        title={meeting.title}
        actions={
          <>
            {isHost ? <InviteLinkButton meetingId={meeting.id} /> : null}
            <Link href={`${base}/reports/latest` as Route} className="action-primary">
              리포트 공유
            </Link>
          </>
        }
      />

      <div className="grid grid-cols-[repeat(auto-fit,minmax(300px,1fr))] gap-4">
        <NextActionCard meetingId={meeting.id} decisions={decisions} />
        <section aria-label="진행 상황" className="panel flex flex-col gap-3.5 p-5">
          <div className="flex items-baseline justify-between">
            <b className="font-display text-xl">
              {decisions.length}개 중 <span className="text-accent-text">{confirmed.length}개</span> 확정
            </b>
            <span className="num text-xs text-muted">{percent}%</span>
          </div>
          {decisions.length ? (
            <div
              aria-hidden="true"
              className="grid gap-1"
              style={{ gridTemplateColumns: `repeat(${decisions.length}, minmax(0, 1fr))` }}
            >
              {sortForProgress(decisions).map((decision) => (
                <div key={decision.id} className={`h-2.5 rounded-[5px] ${decisionProgressColor(decision.status)}`} />
              ))}
            </div>
          ) : (
            <div aria-hidden="true" className="h-2.5 rounded-[5px] bg-line" />
          )}
          <div className="grid grid-cols-3 gap-2 text-xs text-muted">
            <Stat value={meeting.openVoteCount} label="진행 중인 투표" />
            <Stat value={discussing.length} label="논의 중" />
            <Stat value={openTasks.length} label="남은 할 일" />
          </div>
        </section>
      </div>

      <div className="flex flex-wrap items-start gap-4">
        <section aria-label="결정 보드" className="flex min-w-0 flex-[999_1_560px] flex-col gap-2.5">
          <div className="flex items-center justify-between">
            <h2 className="m-0 text-base font-bold">결정 보드</h2>
            <Link href={`${base}/decisions` as Route} className="link-accent text-[13px]">
              전체 보기 →
            </Link>
          </div>
          <div className="grid grid-cols-[repeat(auto-fit,minmax(190px,1fr))] gap-2.5">
            {DECISION_COLUMNS.map((column) => {
              const items = decisions.filter((decision) => decisionColumn(decision.status) === column.id);
              return (
                <div key={column.id} className="flex min-w-0 flex-col gap-2 rounded-[18px] bg-line/60 p-3">
                  <div className="flex items-center justify-between px-1 pb-1 pt-0.5 text-[13px] font-bold">
                    <span>{column.label}</span>
                    <span className={column.id === "confirmed" ? "chip-confirmed" : column.id === "review" ? "chip-voting" : "chip"}>
                      {items.length}
                    </span>
                  </div>
                  {items.map((decision) => (
                    <DecisionCard key={decision.id} base={base} decision={decision} />
                  ))}
                </div>
              );
            })}
          </div>
          {decisionsQuery.isSuccess && decisions.length === 0 ? (
            <div className="empty-state">
              아직 결정이 없어요.{" "}
              <Link href={`${base}/import` as Route} className="link-accent">
                단톡방 대화를 가져오면
              </Link>{" "}
              결정 후보를 찾아 드려요.
            </div>
          ) : null}
        </section>

        <aside aria-label="최근 활동" className="panel flex flex-[1_1_280px] flex-col gap-1.5 p-5">
          <h2 className="m-0 mb-1 text-base font-bold">최근 활동</h2>
          {(notificationsQuery.data ?? []).slice(0, 5).map((notification) => (
            <div key={notification.id} className="flex gap-2.5 border-t border-line py-2.5 text-[13px] leading-[1.45] first-of-type:border-t-0">
              <span className="flex h-[30px] w-[30px] flex-none items-center justify-center rounded-full bg-surface-2 text-[10px] font-bold">
                {notification.type.startsWith("invite") ? "+" : "✓"}
              </span>
              <span>
                {notificationSummary(notification)}
                <span className="num block text-[11px] text-muted">{formatShortDateTime(notification.createdAt)}</span>
              </span>
            </div>
          ))}
          {notificationsQuery.isSuccess && notificationsQuery.data.length === 0 ? (
            <p className="section-copy m-0">아직 활동이 없어요.</p>
          ) : null}
          {discussing[0] ? (
            <div className="mt-2 rounded-[14px] bg-warn-soft px-3.5 py-3 text-[13px] leading-normal text-warn-ink">
              <b>아직 논의 중</b>
              <br />
              {discussing[0].title}
            </div>
          ) : null}
        </aside>
      </div>

      <details className="panel group p-5">
        <summary className="flex cursor-pointer list-none items-center justify-between text-base font-bold">
          투표 만들기
          <span className="text-muted transition group-open:rotate-180" aria-hidden="true">
            ▾
          </span>
        </summary>
        <div className="mt-4">
          <VoteStudio meetingId={meetingId} />
        </div>
      </details>
    </>
  );
}

const PROGRESS_ORDER = ["confirmed", "review", "active", "blocked"] as const;

function sortForProgress(decisions: DecisionResponse[]) {
  return [...decisions].sort(
    (a, b) => PROGRESS_ORDER.indexOf(decisionColumn(a.status)) - PROGRESS_ORDER.indexOf(decisionColumn(b.status)),
  );
}

function NextActionCard({ meetingId, decisions }: { meetingId: string; decisions: DecisionResponse[] }) {
  const base = `/meetings/${meetingId}`;
  const byStatus = (status: string) => decisions.find((decision) => normalizeDecisionStatus(decision.status) === status);
  const review = byStatus("proposed");
  const voting = byStatus("voting");
  const reconsider = byStatus("needs_reconsideration");

  const next = review
    ? { title: `${review.title} 초안을 확정할지 정해 주세요`, note: "확인 대기 중인 결정", href: `${base}/decisions?decision=${review.id}`, cta: "초안 보러 가기 →" }
    : voting
      ? { title: `${voting.title} 투표가 진행 중이에요`, note: "결과를 확인하고 마감해요", href: `${base}/decisions?decision=${voting.id}`, cta: "투표 보러 가기 →" }
      : reconsider
        ? { title: `${reconsider.title}을 다시 정해야 해요`, note: "재검토가 필요한 결정", href: `${base}/decisions?decision=${reconsider.id}`, cta: "결정 보러 가기 →" }
        : decisions.length
          ? { title: "급한 일은 없어요", note: "리포트로 지금까지 정한 걸 공유해 보세요", href: `${base}/reports/latest`, cta: "리포트 보기 →" }
          : { title: "단톡방 대화부터 가져와요", note: "정해진 것과 남은 것을 찾아 정리해요", href: `${base}/import`, cta: "대화 가져오기 →" };

  return (
    <section aria-label="지금 할 일" className="flex flex-col gap-3 rounded-[22px] bg-accent p-6 text-on-accent">
      <span className="num text-[11px] font-semibold tracking-[0.08em] opacity-85">NEXT · 지금 할 일</span>
      <div className="font-display text-2xl leading-[1.3]">{next.title}</div>
      <div className="text-[13px] opacity-90">{next.note}</div>
      <Link
        href={next.href as Route}
        className="mt-1 inline-flex min-h-[44px] items-center self-start rounded-xl bg-white px-[18px] text-[15px] font-bold text-[#2D46F5]"
      >
        {next.cta}
      </Link>
    </section>
  );
}

function DecisionCard({ base, decision }: { base: string; decision: DecisionResponse }) {
  const status = normalizeDecisionStatus(decision.status);
  const onHold = status === "on_hold";
  return (
    <Link
      href={`${base}/decisions?decision=${decision.id}` as Route}
      className={`flex flex-col gap-1.5 rounded-[14px] px-3.5 py-3 text-ink transition hover:ring-1 hover:ring-accent/40 ${
        onHold ? "border-[1.5px] border-dashed border-muted/60" : "bg-surface"
      } ${status === "proposed" ? "ring-2 ring-accent" : ""}`}
    >
      <span className={`text-[11px] font-semibold ${status === "needs_reconsideration" ? "text-risk-ink" : "text-muted"}`}>
        {decisionTypeLabel(decision.decisionType)} · {decisionStatusLabel(decision.status)}
      </span>
      <b className="text-sm">{decision.title}</b>
      {decision.decidedAt ? (
        <span className="num text-xs text-muted">{formatShortDateTime(decision.decidedAt)}</span>
      ) : null}
    </Link>
  );
}

function Stat({ value, label }: { value: number; label: string }) {
  return (
    <div>
      <b className="num block text-lg text-ink">{value}</b>
      {label}
    </div>
  );
}

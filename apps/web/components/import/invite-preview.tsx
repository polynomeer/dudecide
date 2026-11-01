"use client";

import { useQuery } from "@tanstack/react-query";
import { EmptyRow, ReportItem, ReportSection, SummaryTile } from "@/components/report-workspace";
import { ApiError, apiClient, type InvitePreviewTask } from "@/lib/api/client";
import {
  dDayLabel,
  decisionColumn,
  decisionStatusChip,
  decisionStatusLabel,
  decisionTypeLabel,
  formatDateRange,
  formatShortDateTime,
  meetingTypeLabel,
} from "@/lib/display";

const lockIcon = (
  <svg aria-hidden="true" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="mt-0.5 flex-none">
    <rect x="5" y="11" width="14" height="10" rx="2" />
    <path d="M8 11V8a4 4 0 0 1 8 0v3" />
  </svg>
);

/**
 * The shared link's pre-join report (W-4 guest view): titles, statuses, dates, and counts.
 * Quotes and member names stay behind the join, so tasks only say whether someone took them.
 */
export function InvitePreview({ token }: { token: string }) {
  const previewQuery = useQuery({
    queryKey: ["invite-preview", token],
    queryFn: () => apiClient.previewInvite(token),
    retry: false,
  });

  if (previewQuery.isLoading) {
    return <div className="panel px-6 py-5 text-sm text-muted">모임 정보를 불러오는 중이에요…</div>;
  }
  if (previewQuery.error || !previewQuery.data) {
    const status = previewQuery.error instanceof ApiError ? previewQuery.error.status : null;
    return (
      <div role="alert" className="rounded-2xl bg-risk-soft px-6 py-5 text-sm text-risk-ink">
        {status === 410
          ? "만료된 초대 링크입니다. 링크를 보낸 사람에게 새 링크를 요청해 주세요."
          : status === 404
            ? "초대 링크를 찾을 수 없습니다. 주소를 다시 확인해 주세요."
            : "모임 정보를 불러오지 못했습니다. 참여는 그대로 진행할 수 있습니다."}
      </div>
    );
  }

  const preview = previewQuery.data;
  const waiting = preview.pendingDecisionCount + preview.openQuestionCount;
  const confirmed = preview.decisions.filter((decision) => decisionColumn(decision.status) === "confirmed");
  const remaining = preview.decisions.filter((decision) => decisionColumn(decision.status) !== "confirmed");
  const blocked = remaining.filter((decision) => decisionColumn(decision.status) === "blocked");
  const tasks = preview.tasks ?? [];
  const facts = [
    dDayLabel(preview.startsAt, preview.endsAt),
    formatDateRange(preview.startsAt, preview.endsAt),
    preview.meetingType ? meetingTypeLabel(preview.meetingType) : null,
    `${preview.memberCount}명 참여 중`,
  ].filter(Boolean);

  return (
    <section aria-label="모임 미리보기" className="flex flex-col gap-4">
      <div className="flex flex-col gap-5 rounded-[28px] bg-band p-[clamp(24px,4vw,40px)] text-white">
        <div className="flex flex-wrap justify-between gap-2">
          <span className="num text-xs font-semibold tracking-[0.08em] text-[#B8BCC6]">REPORT · 참여 전 미리보기</span>
          <span className="num text-xs text-[#B8BCC6]">{facts.join(" · ")}</span>
        </div>
        <h2 className="font-display m-0 text-[clamp(2rem,4.4vw,3.2rem)] leading-[1.12]">
          {preview.meetingTitle},
          <br />
          지금 여기까지 정했어요
        </h2>
        <div className="grid grid-cols-[repeat(auto-fit,minmax(130px,1fr))] gap-2.5">
          <SummaryTile value={preview.confirmedDecisionCount} label="확정" className="bg-white text-[#15171C]" />
          <SummaryTile value={remaining.length - blocked.length} label="아직 정하는 중" className="bg-[#2D46F5]" />
          <SummaryTile value={blocked.length} label="막힘 · 재검토" className="bg-band-card ring-1 ring-inset ring-white/20" />
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <a
            href="#join"
            className="inline-flex min-h-[48px] items-center rounded-xl bg-white px-[22px] text-[15px] font-bold text-[#15171C]"
          >
            참여하고 투표하기 →
          </a>
          {waiting > 0 ? (
            <span className="text-[13px] font-semibold">{waiting}개 항목이 의견을 기다리고 있어요.</span>
          ) : (
            <span className="text-[13px] text-[#C9CCD3]">가입 없이 이름만 정하면 돼요.</span>
          )}
        </div>
      </div>

      <div className="flex gap-2.5 rounded-2xl bg-accent-soft px-[18px] py-3.5 text-sm leading-normal text-accent-soft-ink">
        {lockIcon}
        <span>참여 전에는 항목 이름과 상태만 보여요. 대화 원문과 이름은 참여한 멤버에게만 보여요.</span>
      </div>

      <ReportSection label="확정" title="확정됐어요" count={confirmed.length} chipClass="chip-confirmed">
        {confirmed.length ? (
          confirmed.map((decision, index) => (
            <ReportItem key={`${decision.title}-${index}`} kind={decisionTypeLabel(decision.decisionType)}>
              <b className="text-[17px]">{decision.title}</b>
              {decision.decidedAt ? (
                <span className="block text-[13px] text-muted">{formatShortDateTime(decision.decidedAt)} 확정</span>
              ) : null}
              <span className="mt-2 flex items-center gap-1.5 text-xs text-muted">{lockIcon}근거는 참여하면 볼 수 있어요</span>
            </ReportItem>
          ))
        ) : (
          <EmptyRow>아직 확정된 결정이 없어요.</EmptyRow>
        )}
      </ReportSection>

      <ReportSection label="진행 중과 막힌 것" title="아직 남았어요" count={remaining.length} chipClass="chip-voting">
        {remaining.length ? (
          remaining.map((decision, index) => {
            const isBlocked = decisionColumn(decision.status) === "blocked";
            return (
              <ReportItem
                key={`${decision.title}-${index}`}
                kind={isBlocked ? "막힘" : decisionTypeLabel(decision.decisionType)}
                risk={isBlocked}
                aside={<span className={`${decisionStatusChip(decision.status)} self-start`}>{decisionStatusLabel(decision.status)}</span>}
              >
                <b className="text-[17px]">{decision.title}</b>
              </ReportItem>
            );
          })
        ) : (
          <EmptyRow>남은 결정이 없어요.</EmptyRow>
        )}
      </ReportSection>

      {tasks.length ? <GuestTasks tasks={tasks} /> : null}
    </section>
  );
}

function GuestTasks({ tasks }: { tasks: InvitePreviewTask[] }) {
  return (
    <section aria-label="할 일" className="rounded-[22px] bg-surface px-6 pb-4 pt-2">
      <div className="pb-2 pt-4">
        <h3 className="m-0 text-[17px] font-bold">누가 뭘 하나</h3>
      </div>
      <ul className="m-0 list-none p-0">
        {tasks.map((task, index) => {
          const done = task.status === "done";
          return (
            <li key={`${task.title}-${index}`} className="flex flex-wrap items-center justify-between gap-2 border-t border-line py-3 text-sm">
              <span className={done ? "text-muted line-through" : "font-bold"}>{task.title}</span>
              {done ? (
                <span className="chip-confirmed">완료</span>
              ) : task.hasOwner ? (
                <span className="chip-voting">담당 있음</span>
              ) : (
                <span className="chip-discussion">맡을 사람 필요</span>
              )}
            </li>
          );
        })}
      </ul>
    </section>
  );
}

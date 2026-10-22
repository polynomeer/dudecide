"use client";

import Link from "next/link";
import type { Route } from "next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiErrorPanel } from "@/components/api-error-panel";
import { InviteLinkButton } from "@/components/meeting/invite-link-button";
import { getApiErrorMessage } from "@/lib/api/error-display";
import { apiClient, type TaskView } from "@/lib/api/client";
import {
  decisionColumn,
  decisionStatusChip,
  decisionStatusLabel,
  decisionTypeLabel,
  formatShortDateTime,
} from "@/lib/display";
import { useCurrentActor, useMeetingHostAccess } from "@/lib/session";

type SnapshotDecision = {
  id?: string;
  title: string;
  decisionType: string;
  status: string;
  decidedAt?: string | null;
};

/** Reads the report snapshot defensively; older snapshots may miss fields. */
function readSnapshot(snapshot: Record<string, unknown>) {
  const meeting = (snapshot.meeting ?? {}) as { title?: unknown };
  const decisions = Array.isArray(snapshot.decisions)
    ? (snapshot.decisions as Array<Record<string, unknown>>)
        .filter((item) => typeof item.title === "string")
        .map(
          (item): SnapshotDecision => ({
            id: typeof item.id === "string" ? item.id : undefined,
            title: item.title as string,
            decisionType: typeof item.decisionType === "string" ? item.decisionType : "other",
            status: typeof item.status === "string" ? item.status : "proposed",
            decidedAt: typeof item.decidedAt === "string" ? item.decidedAt : null,
          }),
        )
    : [];
  return {
    title: typeof meeting.title === "string" ? meeting.title : null,
    summary: typeof snapshot.summary === "string" ? snapshot.summary : null,
    decisions,
  };
}

const DONE_TASK_STATUSES = ["done", "completed"];

export function ReportWorkspace({ meetingId }: { meetingId: string }) {
  const queryClient = useQueryClient();
  const access = useMeetingHostAccess(meetingId);
  const reportQuery = useQuery({
    queryKey: ["meeting-report", meetingId],
    queryFn: () => apiClient.getLatestReport(meetingId),
  });
  const tasksQuery = useQuery({
    queryKey: ["meeting-tasks", meetingId],
    queryFn: () => apiClient.listTasks(meetingId),
  });

  const refreshMutation = useMutation({
    mutationFn: () => apiClient.refreshReport(meetingId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["meeting-report", meetingId] });
    },
  });

  if (reportQuery.isLoading) {
    return <div className="panel px-6 py-6 text-sm text-muted">리포트를 불러오는 중이에요…</div>;
  }

  if (reportQuery.error || !reportQuery.data) {
    return <ApiErrorPanel error={reportQuery.error} resourceName="최신 회의 리포트" />;
  }

  const report = reportQuery.data;
  const snapshot = readSnapshot(report.snapshot);
  const title = snapshot.title ?? access.meetingQuery.data?.title ?? "우리 모임";
  const confirmed = snapshot.decisions.filter((item) => decisionColumn(item.status) === "confirmed");
  const blocked = snapshot.decisions.filter((item) => decisionColumn(item.status) === "blocked");
  const remaining = snapshot.decisions.filter((item) => !confirmed.includes(item));
  const decisionHref = (item: SnapshotDecision) =>
    (item.id ? `/meetings/${meetingId}/decisions?decision=${item.id}` : `/meetings/${meetingId}/decisions`) as Route;

  return (
    <div className="mx-auto flex w-full max-w-[920px] flex-col gap-4">
      <section aria-label="리포트 요약" className="flex flex-col gap-5 rounded-[28px] bg-band p-[clamp(24px,4vw,40px)] text-white">
        <div className="flex flex-wrap justify-between gap-2">
          <span className="num text-xs font-semibold tracking-[0.08em] text-[#B8BCC6]">
            REPORT · {formatShortDateTime(report.generatedAt)} 기준
          </span>
          <span className="num text-xs text-[#B8BCC6]">v{report.version}</span>
        </div>
        <h2 className="font-display m-0 text-[clamp(2rem,4.4vw,3.2rem)] leading-[1.12]">
          {title},
          <br />
          지금 여기까지 정했어요
        </h2>
        {snapshot.summary ? <p className="m-0 text-[15px] leading-relaxed text-[#C9CCD3]">{snapshot.summary}</p> : null}
        <div className="grid grid-cols-[repeat(auto-fit,minmax(150px,1fr))] gap-2.5">
          <SummaryTile value={confirmed.length} label="확정" className="bg-white text-[#15171C]" />
          <SummaryTile value={remaining.length - blocked.length} label="아직 정하는 중" className="bg-[#2D46F5]" />
          <SummaryTile value={blocked.length} label="막힘 · 재검토" className="bg-band-card ring-1 ring-inset ring-white/20" />
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {access.isHost ? (
            <>
              <InviteLinkButton
                meetingId={meetingId}
                className="inline-flex min-h-[48px] items-center rounded-xl bg-white px-5 text-[15px] font-bold text-[#15171C] disabled:opacity-60"
              />
              <button
                type="button"
                onClick={() => refreshMutation.mutate()}
                disabled={refreshMutation.isPending}
                className="inline-flex min-h-[48px] items-center rounded-xl border border-white/30 px-5 text-[15px] font-semibold text-white disabled:opacity-60"
              >
                {refreshMutation.isPending ? "갱신 중…" : "리포트 새로고침"}
              </button>
            </>
          ) : (
            <span className="text-[13px] text-[#C9CCD3]">리포트는 호스트가 새로 만들 수 있어요.</span>
          )}
        </div>
        {refreshMutation.isError ? (
          <p role="alert" className="m-0 text-sm text-[#FFB38A]">
            {getApiErrorMessage(refreshMutation.error, {
              resourceName: "리포트 새로고침",
              conflictMessage: "현재 리포트 상태에서는 새로고침을 바로 실행할 수 없습니다.",
            })}
          </p>
        ) : null}
      </section>

      <ReportSection label="확정" title="확정됐어요" count={confirmed.length} chipClass="chip-confirmed">
        {confirmed.length ? (
          confirmed.map((item, index) => (
            <ReportItem key={item.id ?? index} kind={decisionTypeLabel(item.decisionType)}>
              <b className="text-[17px]">{item.title}</b>
              {item.decidedAt ? (
                <span className="block text-[13px] text-muted">{formatShortDateTime(item.decidedAt)} 확정</span>
              ) : null}
              <Link href={decisionHref(item)} className="link-accent num mt-1 inline-flex min-h-[32px] items-center text-xs">
                근거 보기 →
              </Link>
            </ReportItem>
          ))
        ) : (
          <EmptyRow>아직 확정된 결정이 없어요.</EmptyRow>
        )}
      </ReportSection>

      <ReportSection label="진행 중과 막힌 것" title="아직 남았어요" count={remaining.length} chipClass="chip-voting">
        {remaining.length ? (
          remaining.map((item, index) => (
            <ReportItem
              key={item.id ?? index}
              kind={decisionColumn(item.status) === "blocked" ? "막힘" : decisionTypeLabel(item.decisionType)}
              risk={decisionColumn(item.status) === "blocked"}
              aside={<span className={`${decisionStatusChip(item.status)} self-start`}>{decisionStatusLabel(item.status)}</span>}
            >
              <Link href={decisionHref(item)} className="text-[17px] font-bold hover:underline">
                {item.title}
              </Link>
            </ReportItem>
          ))
        ) : (
          <EmptyRow>남은 결정이 없어요. 다 정했어요!</EmptyRow>
        )}
      </ReportSection>

      <TasksTable tasks={tasksQuery.data ?? []} loading={tasksQuery.isLoading} />

      <p className="m-0 mt-2 text-center text-xs text-muted">
        이 리포트는 dudecide에서 대화와 투표를 바탕으로 만들었어요 · AI가 제안하고 호스트가 확정했어요
      </p>
    </div>
  );
}

function SummaryTile({ value, label, className }: { value: number; label: string; className: string }) {
  return (
    <div className={`rounded-2xl px-[18px] py-4 ${className}`}>
      <div className="font-display text-[34px]">{value}</div>
      <div className="text-[13px] font-semibold">{label}</div>
    </div>
  );
}

function ReportSection({
  label,
  title,
  count,
  chipClass,
  children,
}: {
  label: string;
  title: string;
  count?: number;
  chipClass?: string;
  children: React.ReactNode;
}) {
  return (
    <section aria-label={label} className="rounded-[22px] bg-surface px-6 pb-2 pt-2">
      <div className="flex items-center justify-between pb-2 pt-4">
        <h3 className="m-0 text-[17px] font-bold">{title}</h3>
        {count !== undefined && chipClass ? <span className={chipClass}>{count}</span> : null}
      </div>
      {children}
    </section>
  );
}

function ReportItem({
  kind,
  risk = false,
  aside,
  children,
}: {
  kind: string;
  risk?: boolean;
  aside?: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <div className="flex flex-wrap gap-x-4 gap-y-2 border-t border-line py-4">
      <span className={`w-14 flex-none pt-[3px] text-xs font-semibold ${risk ? "text-risk-ink" : "text-muted"}`}>{kind}</span>
      <div className="min-w-0 flex-[1_1_360px]">{children}</div>
      {aside}
    </div>
  );
}

function EmptyRow({ children }: { children: React.ReactNode }) {
  return <p className="section-copy m-0 border-t border-line py-4">{children}</p>;
}

function TasksTable({ tasks, loading }: { tasks: TaskView[]; loading: boolean }) {
  const { data: actor } = useCurrentActor();
  return (
    <section aria-label="할 일" className="rounded-[22px] bg-surface px-6 pb-4 pt-2">
      <div className="pb-2 pt-4">
        <h3 className="m-0 text-[17px] font-bold">누가 뭘 하나</h3>
      </div>
      {loading ? <p className="section-copy">할 일을 불러오는 중이에요…</p> : null}
      {!loading && tasks.length === 0 ? <EmptyRow>아직 할 일이 없어요.</EmptyRow> : null}
      {tasks.length ? (
        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr>
                {["할 일", "담당", "기한", "상태"].map((heading) => (
                  <th key={heading} scope="col" className="whitespace-nowrap border-b border-line px-2 py-2.5 text-left text-xs font-semibold text-muted">
                    {heading}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {tasks.map((task) => {
                const done = DONE_TASK_STATUSES.includes(task.status.toLowerCase());
                return (
                  <tr key={task.id}>
                    <td className={`border-b border-line px-2 py-3 align-top ${done ? "text-muted line-through" : "font-bold"}`}>{task.title}</td>
                    <td className={`border-b border-line px-2 py-3 align-top ${task.ownerUserId ? "" : "text-risk-ink"}`}>
                      {task.ownerUserId ? (task.ownerUserId === actor?.userId ? "나" : "멤버") : "담당 없음"}
                    </td>
                    <td className="num border-b border-line px-2 py-3 align-top">
                      {task.dueAt ? new Intl.DateTimeFormat("ko-KR", { month: "2-digit", day: "2-digit" }).format(new Date(task.dueAt)) : "—"}
                    </td>
                    <td className="border-b border-line px-2 py-3 align-top">
                      <TaskStatusChip task={task} done={done} />
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      ) : null}
    </section>
  );
}

function TaskStatusChip({ task, done }: { task: TaskView; done: boolean }) {
  if (done) return <span className="chip-confirmed">완료</span>;
  if (!task.ownerUserId) return <span className="chip-discussion">맡을 사람 필요</span>;
  return <span className="chip-voting">진행 중</span>;
}

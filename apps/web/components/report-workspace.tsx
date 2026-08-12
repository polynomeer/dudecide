"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiErrorPanel } from "@/components/api-error-panel";
import { HostOnlyNotice } from "@/components/host-only-notice";
import { getApiErrorMessage } from "@/lib/api/error-display";
import { apiClient } from "@/lib/api/client";
import { useMeetingHostAccess } from "@/lib/session";

export function ReportWorkspace({ meetingId }: { meetingId: string }) {
  const queryClient = useQueryClient();
  const access = useMeetingHostAccess(meetingId);
  const reportQuery = useQuery({
    queryKey: ["meeting-report", meetingId],
    queryFn: () => apiClient.getLatestReport(meetingId),
  });

  const refreshMutation = useMutation({
    mutationFn: () => apiClient.refreshReport(meetingId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["meeting-report", meetingId] });
    },
  });

  if (reportQuery.isLoading) {
    return <div className="panel px-6 py-6 text-sm text-ink/60">최신 리포트를 불러오는 중입니다.</div>;
  }

  if (reportQuery.error || !reportQuery.data) {
    return <ApiErrorPanel error={reportQuery.error} resourceName="최신 회의 리포트" />;
  }

  const report = reportQuery.data;

  return (
    <div className="grid gap-5 xl:grid-cols-[0.82fr_1.18fr]">
      <section className="panel px-6 py-6">
        <div className="eyebrow">Snapshot</div>
        <h2 className="mt-2 font-[var(--font-display)] text-3xl font-semibold tracking-[-0.05em]">
          최신 회의 리포트
        </h2>
        <div className="mt-5 grid gap-3 sm:grid-cols-2">
          <Metric label="Version" value={String(report.version)} />
          <Metric label="Generated" value={formatDateTime(report.generatedAt)} />
        </div>

        <div className="mt-5 rounded-[1.5rem] border border-[color:var(--color-line)] bg-white/72 p-4 text-sm leading-6 text-ink/68">
          리포트는 백엔드가 materialized snapshot 형태로 저장한 회의 요약본입니다. 수동 갱신 시 최신 메시지,
          결정, 투표 결과가 다시 반영됩니다.
        </div>

        {refreshMutation.isError ? (
          <div className="mt-4 text-sm text-ember">
            {getApiErrorMessage(refreshMutation.error, {
              resourceName: "리포트 새로고침",
              conflictMessage: "현재 리포트 상태에서는 새로고침을 바로 실행할 수 없습니다.",
            })}
          </div>
        ) : null}

        <div className="mt-5">
          {access.isHost ? (
            <div className="flex justify-end">
              <button
                type="button"
                onClick={() => refreshMutation.mutate()}
                disabled={refreshMutation.isPending}
                className="rounded-full bg-ink px-5 py-3 text-sm font-semibold text-mist transition hover:bg-pine disabled:cursor-not-allowed disabled:bg-ink/40"
              >
                {refreshMutation.isPending ? "갱신 중..." : "리포트 새로고침"}
              </button>
            </div>
          ) : (
            <HostOnlyNotice message="현재 프론트엔드에서는 회의 소유자에게만 리포트 새로고침 액션을 노출합니다." />
          )}
        </div>
      </section>

      <section className="panel px-6 py-6">
        <div className="eyebrow">Payload</div>
        <h3 className="mt-2 font-[var(--font-display)] text-3xl font-semibold tracking-[-0.05em]">
          Snapshot JSON
        </h3>
        <pre className="mt-5 overflow-x-auto rounded-[1.5rem] bg-ink px-5 py-5 text-xs leading-6 text-mist">
          {JSON.stringify(report.snapshot, null, 2)}
        </pre>
      </section>
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

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

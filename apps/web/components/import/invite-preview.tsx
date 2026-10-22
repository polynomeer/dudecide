"use client";

import { useQuery } from "@tanstack/react-query";
import { ApiError, apiClient } from "@/lib/api/client";
import { decisionStatusChip, decisionStatusLabel, decisionTypeLabel } from "@/lib/display";

const lockIcon = (
  <svg aria-hidden="true" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="mt-0.5 flex-none">
    <rect x="5" y="11" width="14" height="10" rx="2" />
    <path d="M8 11V8a4 4 0 0 1 8 0v3" />
  </svg>
);

/** What an invitee sees before joining: titles, statuses, counts. Quotes and names stay behind the join. */
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

  return (
    <section aria-label="모임 미리보기" className="flex flex-col gap-4">
      <div className="flex flex-col gap-5 rounded-[28px] bg-band p-[clamp(24px,4vw,40px)] text-white">
        <div className="flex flex-wrap justify-between gap-2">
          <span className="num text-xs font-semibold tracking-[0.08em] text-[#B8BCC6]">INVITE · 참여 전 미리보기</span>
          <span className="num text-xs text-[#B8BCC6]">{preview.memberCount}명 참여 중</span>
        </div>
        <h2 className="font-display m-0 text-[clamp(2rem,4.4vw,3rem)] leading-[1.12]">{preview.meetingTitle}</h2>
        {waiting > 0 ? (
          <p className="m-0 text-[15px] font-semibold">{waiting}개 항목이 의견을 기다리고 있어요.</p>
        ) : (
          <p className="m-0 text-[15px] text-[#C9CCD3]">지금까지 정리된 내용을 확인해 보세요.</p>
        )}
        <dl className="m-0 grid grid-cols-2 gap-2.5 sm:grid-cols-4">
          <Metric label="확정" value={preview.confirmedDecisionCount} className="bg-white text-[#15171C]" />
          <Metric label="결정 대기" value={preview.pendingDecisionCount} className="bg-[#2D46F5]" />
          <Metric label="할 일" value={preview.openTaskCount} className="bg-band-card ring-1 ring-inset ring-white/20" />
          <Metric label="답 없는 질문" value={preview.openQuestionCount} className="bg-band-card ring-1 ring-inset ring-white/20" />
        </dl>
      </div>

      <div className="flex gap-2.5 rounded-2xl bg-accent-soft px-[18px] py-3.5 text-sm leading-normal text-accent-soft-ink">
        {lockIcon}
        <span>참여 전에는 항목 이름과 상태만 보여요. 대화 원문과 이름은 참여한 멤버에게만 보여요.</span>
      </div>

      {preview.decisions.length > 0 ? (
        <div className="rounded-[22px] bg-surface px-6 py-2">
          {preview.decisions.map((decision, index) => (
            <div key={`${decision.title}-${index}`} className="flex flex-wrap items-center gap-x-4 gap-y-1 border-t border-line py-4 first:border-t-0">
              <span className="w-14 flex-none text-xs font-semibold text-muted">{decisionTypeLabel(decision.decisionType)}</span>
              <b className="min-w-0 flex-1 text-base">{decision.title}</b>
              <span className={decisionStatusChip(decision.status)}>{decisionStatusLabel(decision.status)}</span>
            </div>
          ))}
        </div>
      ) : null}
    </section>
  );
}

function Metric({ label, value, className }: { label: string; value: number; className: string }) {
  return (
    <div className={`flex flex-col-reverse rounded-2xl px-4 py-3.5 ${className}`}>
      <dt className="text-[13px] font-semibold">{label}</dt>
      <dd className="font-display m-0 text-[28px]">{value}</dd>
    </div>
  );
}

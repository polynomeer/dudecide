"use client";

import { useQuery } from "@tanstack/react-query";
import { ApiError, apiClient } from "@/lib/api/client";

const DECISION_STATUS: Record<string, string> = {
  proposed: "제안됨",
  in_discussion: "논의 중",
  voting: "투표 중",
  confirmed: "확정",
  on_hold: "보류",
  needs_reconsideration: "재검토",
};

/** What an invitee sees before joining: titles, statuses, counts. Quotes and names stay behind the join. */
export function InvitePreview({ token }: { token: string }) {
  const previewQuery = useQuery({
    queryKey: ["invite-preview", token],
    queryFn: () => apiClient.previewInvite(token),
    retry: false,
  });

  if (previewQuery.isLoading) {
    return <div className="panel mb-5 max-w-2xl px-6 py-5 text-sm text-ink/60">모임 정보를 불러오는 중입니다.</div>;
  }
  if (previewQuery.error || !previewQuery.data) {
    const status = previewQuery.error instanceof ApiError ? previewQuery.error.status : null;
    return (
      <div role="alert" className="panel mb-5 max-w-2xl px-6 py-5 text-sm">
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
    <section className="panel mb-5 max-w-2xl px-6 py-6" aria-label="모임 미리보기">
      <div className="info-chip">{preview.memberCount}명 참여 중</div>
      <h2 className="mt-4 text-2xl font-semibold tracking-[-0.03em]">{preview.meetingTitle}</h2>
      {waiting > 0 ? (
        <p className="mt-2 text-sm font-semibold">{waiting}개 항목이 의견을 기다리고 있어요.</p>
      ) : (
        <p className="mt-2 text-sm text-ink/60">지금까지 정리된 내용을 확인해 보세요.</p>
      )}
      <dl className="mt-4 grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
        <Metric label="결정 대기" value={preview.pendingDecisionCount} />
        <Metric label="확정" value={preview.confirmedDecisionCount} />
        <Metric label="할 일" value={preview.openTaskCount} />
        <Metric label="답 없는 질문" value={preview.openQuestionCount} />
      </dl>
      {preview.decisions.length > 0 ? (
        <ul className="mt-4 space-y-2 text-sm">
          {preview.decisions.map((decision, index) => (
            <li key={`${decision.title}-${index}`} className="flex items-center justify-between gap-3">
              <span>{decision.title}</span>
              <span className="info-chip">{DECISION_STATUS[decision.status] ?? decision.status}</span>
            </li>
          ))}
        </ul>
      ) : null}
      <p className="section-copy mt-4">대화 원문과 이름은 참여한 뒤에 볼 수 있습니다.</p>
    </section>
  );
}

function Metric({ label, value }: { label: string; value: number }) {
  return (
    <div className="metric-card px-4 py-3">
      <dt className="text-ink/50">{label}</dt>
      <dd className="text-lg font-semibold">{value}</dd>
    </div>
  );
}

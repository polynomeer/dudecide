import type { NotificationView } from "@/lib/api/client";

export type DecisionStatus =
  | "proposed"
  | "in_discussion"
  | "voting"
  | "confirmed"
  | "on_hold"
  | "needs_reconsideration";

export type DecisionColumn = "review" | "active" | "blocked" | "confirmed";

const STATUS_META: Record<DecisionStatus, { label: string; chip: string; column: DecisionColumn }> = {
  proposed: { label: "확인 필요", chip: "chip-voting", column: "review" },
  in_discussion: { label: "논의 중", chip: "chip-discussion", column: "active" },
  voting: { label: "투표 중", chip: "chip-voting", column: "active" },
  confirmed: { label: "확정", chip: "chip-confirmed", column: "confirmed" },
  on_hold: { label: "보류", chip: "chip-hold", column: "blocked" },
  needs_reconsideration: { label: "재검토", chip: "chip-reconsider", column: "blocked" },
};

export function normalizeDecisionStatus(status: string): DecisionStatus | null {
  const normalized = status.toLowerCase();
  return normalized in STATUS_META ? (normalized as DecisionStatus) : null;
}

export function decisionStatusLabel(status: string): string {
  const key = normalizeDecisionStatus(status);
  return key ? STATUS_META[key].label : status;
}

export function decisionStatusChip(status: string): string {
  const key = normalizeDecisionStatus(status);
  return key ? STATUS_META[key].chip : "chip";
}

export function decisionColumn(status: string): DecisionColumn {
  const key = normalizeDecisionStatus(status);
  return key ? STATUS_META[key].column : "active";
}

export const DECISION_COLUMNS: Array<{ id: DecisionColumn; label: string }> = [
  { id: "review", label: "확인 필요" },
  { id: "active", label: "진행 중" },
  { id: "blocked", label: "재검토 · 보류" },
  { id: "confirmed", label: "확정" },
];

/** Bar color for one decision in the dashboard progress strip. */
export function decisionProgressColor(status: string): string {
  switch (decisionColumn(status)) {
    case "confirmed":
      return "bg-ink";
    case "review":
      return "bg-accent";
    case "blocked":
      return "bg-risk-ink/60";
    default:
      return normalizeDecisionStatus(status) === "voting" ? "bg-accent" : "bg-[#F2B33D]";
  }
}

const DECISION_TYPE_LABELS: Record<string, string> = {
  date: "날짜",
  destination: "장소",
  place: "장소",
  location: "장소",
  lodging: "숙소",
  accommodation: "숙소",
  transport: "교통",
  transportation: "교통",
  budget: "예산",
  departure: "출발",
  schedule: "일정",
  task: "할 일",
  other: "기타",
  general: "일반",
};

export function decisionTypeLabel(type: string): string {
  return DECISION_TYPE_LABELS[type.toLowerCase()] ?? type;
}

export function notificationSummary(notification: NotificationView): string {
  const payload = notification.payload ?? {};
  switch (notification.type) {
    case "decision_confirmed":
      return typeof payload.title === "string" ? `‘${payload.title}’ 결정이 확정됐어요` : "결정이 확정됐어요";
    case "vote_closed":
      return "투표가 마감됐어요";
    case "report_ready":
      return typeof payload.version === "number" ? `리포트 v${payload.version}이 준비됐어요` : "리포트가 준비됐어요";
    case "invite_created":
      return "초대 링크를 만들었어요";
    case "invite_joined":
      return "새 멤버가 참여했어요";
    default:
      return notification.type.replace(/_/g, " ");
  }
}

export function formatShortDateTime(value?: string | null): string {
  if (!value) {
    return "";
  }
  return new Intl.DateTimeFormat("ko-KR", {
    month: "numeric",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date(value));
}

export function initialOf(name?: string | null): string {
  return name?.trim().charAt(0) || "?";
}

const AVATAR_COLORS = ["#DCD6FF", "#FFD8C2", "#CFE9DE", "#C9D0FF", "#FFE7A8", "#F5D3E6", "#D6E6F5"];

/** Stable pastel avatar background for a name. Avatar text stays dark in both themes. */
export function avatarColor(seed: string): string {
  let hash = 0;
  for (const char of seed) {
    hash = (hash * 31 + char.charCodeAt(0)) >>> 0;
  }
  return AVATAR_COLORS[hash % AVATAR_COLORS.length];
}

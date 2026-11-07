import type { BuildPayloadError, ImportFormat } from "@/lib/import";

export const CONSENT_VERSION = "chat-import-consent-v1";

export const FORMAT_LABELS: Record<ImportFormat, string> = {
  kakao_pc: "카카오톡 PC 내보내기",
  kakao_android: "카카오톡 Android 내보내기",
  kakao_ios: "카카오톡 iOS 내보내기",
  kakao_csv: "카카오톡 Mac CSV",
  free_text: "붙여넣은 텍스트",
};

export const EXPORT_GUIDES = [
  { id: "android", label: "Android", steps: ["채팅방 오른쪽 위 메뉴(≡)", "설정(톱니바퀴)", "대화 내용 내보내기", "텍스트만 보내기 → 파일로 저장"] },
  { id: "ios", label: "iPhone", steps: ["채팅방 오른쪽 위 메뉴(≡)", "설정(톱니바퀴)", "대화 내용 내보내기", "텍스트만 보내기 → 파일에 저장"] },
  { id: "pc", label: "Windows", steps: ["채팅방 오른쪽 위 메뉴(≡)", "대화 내용", "대화 내보내기 → .txt 저장"] },
  { id: "mac", label: "Mac", steps: ["채팅방 오른쪽 위 메뉴(≡)", "대화 내용", "대화 내보내기 → .csv 저장"] },
] as const;

export function buildErrorMessage(error: BuildPayloadError): string {
  switch (error.code) {
    case "consent_required":
      return "대화 참여자 확인에 동의해야 가져올 수 있습니다.";
    case "range_too_long":
      return `기간은 최대 ${error.limit}일까지 고를 수 있습니다. 기간을 줄여 주세요.`;
    case "no_messages":
      return "선택한 기간에 가져올 메시지가 없습니다.";
    case "too_many_messages":
      return `선택한 기간의 메시지가 ${error.count.toLocaleString()}개입니다. ${error.limit.toLocaleString()}개 이하가 되도록 기간을 줄여 주세요.`;
    case "too_many_speakers":
      return `참여자가 ${error.count}명입니다. ${error.limit}명 이하인 대화만 가져올 수 있습니다.`;
  }
}

export const STAGE_LABELS: Record<string, string> = {
  claimed: "분석을 준비하고 있습니다",
  prefilter: "잡담을 걸러내고 있습니다",
  map: "대화 구간별로 읽고 있습니다",
  reduce: "같은 주제를 합치고 있습니다",
  rules: "결정 상태를 판정하고 있습니다",
  done: "분석이 끝났습니다",
};

export const DECISION_STATUS_LABELS: Record<string, string> = {
  likely_decided: "결정된 것으로 보임",
  contested: "의견이 갈림",
  open: "논의 중",
  abandoned: "흐지부지된 제안",
};

export const EVIDENCE_ROLE_LABELS: Record<string, string> = {
  proposal: "제안",
  support: "찬성",
  opposition: "반대",
  confirmation: "확정",
  condition: "조건",
  withdrawal: "철회",
  question: "질문",
  task: "할 일",
};

export function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat("ko-KR", {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

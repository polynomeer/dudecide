import { ApiError } from "@/lib/api/client";

type ErrorMessageOptions = {
  resourceName?: string;
  conflictMessage?: string;
  fallbackMessage?: string;
};

export function getApiErrorMessage(
  error: unknown,
  {
    resourceName = "요청한 정보",
    conflictMessage,
    fallbackMessage = "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.",
  }: ErrorMessageOptions = {},
) {
  if (!(error instanceof ApiError)) {
    return fallbackMessage;
  }

  if (error.status === 401) {
    return "세션이 없거나 만료되었습니다. /start 에서 이름을 정하고 다시 시작해 주세요.";
  }

  if (error.status === 403) {
    return `${resourceName}에 접근할 권한이 없습니다. 회의 멤버십과 역할을 확인해 주세요.`;
  }

  if (error.status === 404) {
    return `${resourceName}을(를) 찾을 수 없습니다. 초대 링크나 회의 식별자가 유효한지 확인해 주세요.`;
  }

  if (error.status === 409) {
    return conflictMessage ?? `현재 ${resourceName} 상태에서는 이 작업을 수행할 수 없습니다. 새로고침 후 다시 시도해 주세요.`;
  }

  return error.body || fallbackMessage;
}

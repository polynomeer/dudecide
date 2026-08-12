import { describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/client";
import { getApiErrorMessage } from "@/lib/api/error-display";

describe("getApiErrorMessage", () => {
  it("maps 401 to session recovery guidance", () => {
    const error = new ApiError(401, "unauthorized");

    expect(getApiErrorMessage(error)).toContain("/start");
  });

  it("maps 403 to permission guidance with resource name", () => {
    const error = new ApiError(403, "forbidden");

    expect(getApiErrorMessage(error, { resourceName: "결정 보드" })).toContain(
      "결정 보드에 접근할 권한이 없습니다.",
    );
  });

  it("uses conflict message for 409 responses", () => {
    const error = new ApiError(409, "vote is not open");

    expect(
      getApiErrorMessage(error, {
        resourceName: "투표 응답 저장",
        conflictMessage: "투표가 이미 마감되었거나 현재 상태에서는 응답을 저장할 수 없습니다.",
      }),
    ).toBe("투표가 이미 마감되었거나 현재 상태에서는 응답을 저장할 수 없습니다.");
  });

  it("falls back for unknown errors", () => {
    expect(getApiErrorMessage(new Error("boom"))).toBe(
      "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.",
    );
  });
});

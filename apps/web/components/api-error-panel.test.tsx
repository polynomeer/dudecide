import React from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ApiErrorPanel } from "@/components/api-error-panel";
import { ApiError } from "@/lib/api/client";

describe("ApiErrorPanel", () => {
  it("renders resource-specific 404 guidance", () => {
    render(<ApiErrorPanel error={new ApiError(404, "meeting not found")} resourceName="회의 작업공간" />);

    expect(screen.getByText(/회의 작업공간을\(를\) 찾을 수 없습니다/)).toBeInTheDocument();
  });

  it("hides server failure details and shows a code to report", () => {
    render(<ApiErrorPanel error={new ApiError(500, '{"trace":"internal detail"}', "req-12345678")} resourceName="회의 작업공간" />);

    expect(screen.queryByText(/internal detail/)).toBeNull();
    expect(screen.getByText(/요청을 처리하지 못했습니다/)).toBeInTheDocument();
    expect(screen.getByText("오류 코드 req-12345678")).toBeInTheDocument();
  });

  it("renders conflict message when provided", () => {
    render(
      <ApiErrorPanel
        error={new ApiError(409, "vote is already closed")}
        resourceName="투표 마감"
        conflictMessage="이미 마감된 투표이거나 현재 상태에서는 마감할 수 없습니다."
      />,
    );

    expect(
      screen.getByText("이미 마감된 투표이거나 현재 상태에서는 마감할 수 없습니다."),
    ).toBeInTheDocument();
  });
});

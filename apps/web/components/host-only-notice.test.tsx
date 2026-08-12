import React from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { HostOnlyNotice } from "@/components/host-only-notice";

describe("HostOnlyNotice", () => {
  it("renders the provided host-only guidance message", () => {
    render(<HostOnlyNotice message="현재 프론트엔드에서는 회의 소유자에게만 투표 생성 액션을 노출합니다." />);

    expect(
      screen.getByText("현재 프론트엔드에서는 회의 소유자에게만 투표 생성 액션을 노출합니다."),
    ).toBeInTheDocument();
  });
});

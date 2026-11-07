import React from "react";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ReportWorkspace } from "@/components/report-workspace";
import { apiClient } from "@/lib/api/client";
import { renderWithQueryClient } from "@/test-utils/render-with-query-client";

vi.mock("@/lib/api/client", async () => {
  const actual = await vi.importActual<typeof import("@/lib/api/client")>("@/lib/api/client");
  return {
    ...actual,
    apiClient: {
      ...actual.apiClient,
      getLatestReport: vi.fn(),
      refreshReport: vi.fn(),
      listTasks: vi.fn(),
      listMembers: vi.fn(),
      createInviteLink: vi.fn(),
    },
  };
});

vi.mock("@/lib/session", () => ({
  useCurrentActor: () => ({ data: { userId: "host-1" } }),
  useMeetingHostAccess: () => ({
    isHost: true,
    actorQuery: { data: { userId: "host-1" } },
    meetingQuery: { data: { ownerUserId: "host-1" } },
  }),
}));

describe("ReportWorkspace", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders report snapshot and refreshes on host action", async () => {
    vi.mocked(apiClient.getLatestReport).mockResolvedValue({
      id: "report-1",
      meetingId: "meeting-1",
      version: 3,
      snapshot: {
        summary: "최신 투표 결과 반영",
        meeting: { title: "제주 2박 3일" },
        decisions: [
          { id: "d-1", title: "11.14 금 – 16 일", decisionType: "date", status: "confirmed", decidedAt: "2026-08-10T10:00:00Z" },
          { id: "d-2", title: "애월 독채", decisionType: "lodging", status: "proposed" },
          { id: "d-3", title: "렌트 vs 택시", decisionType: "transport", status: "on_hold" },
        ],
      },
      generatedAt: "2026-08-12T10:00:00Z",
    });
    vi.mocked(apiClient.listTasks).mockResolvedValue([
      { id: "t-1", meetingId: "meeting-1", ownerUserId: null, title: "렌트카 알아보기", status: "todo", createdAt: "2026-08-11T10:00:00Z" },
      { id: "t-2", meetingId: "meeting-1", ownerUserId: "user-2", title: "항공권 비교", status: "in_progress", createdAt: "2026-08-11T10:00:00Z" },
      { id: "t-3", meetingId: "meeting-1", ownerUserId: "user-2", title: "취소된 일", status: "cancelled", createdAt: "2026-08-11T10:00:00Z" },
    ]);
    vi.mocked(apiClient.listMembers).mockResolvedValue([
      {
        id: "m-2",
        meetingId: "meeting-1",
        userId: "user-2",
        displayName: "서연",
        role: "participant",
        attendanceStatus: "accepted",
        joinedAt: "2026-08-10T10:00:00Z",
      },
    ]);
    vi.mocked(apiClient.refreshReport).mockResolvedValue({
      id: "report-2",
      meetingId: "meeting-1",
      version: 4,
      snapshot: { summary: "새로고침 완료" },
      generatedAt: "2026-08-12T10:05:00Z",
    });

    renderWithQueryClient(<ReportWorkspace meetingId="meeting-1" />);

    expect(await screen.findByRole("heading", { name: /제주 2박 3일/ })).toBeInTheDocument();
    expect(screen.getByText("v3")).toBeInTheDocument();
    expect(screen.getByText(/최신 투표 결과 반영/)).toBeInTheDocument();
    const confirmed = screen.getByRole("region", { name: "확정" });
    expect(confirmed).toHaveTextContent("11.14 금 – 16 일");
    const remaining = screen.getByRole("region", { name: "진행 중과 막힌 것" });
    expect(remaining).toHaveTextContent("애월 독채");
    expect(remaining).toHaveTextContent("보류");
    expect(await screen.findByText("맡을 사람 필요")).toBeInTheDocument();
    expect(await screen.findByText("서연")).toBeInTheDocument();
    expect(screen.queryByText("취소된 일")).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "리포트 새로고침" }));

    await waitFor(() =>
      expect(apiClient.refreshReport).toHaveBeenCalledWith("meeting-1"),
    );
  });
});

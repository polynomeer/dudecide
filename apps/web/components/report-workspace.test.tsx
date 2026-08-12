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
    },
  };
});

vi.mock("@/lib/session", () => ({
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
      snapshot: { summary: "최신 투표 결과 반영" },
      generatedAt: "2026-08-12T10:00:00Z",
    });
    vi.mocked(apiClient.refreshReport).mockResolvedValue({
      id: "report-2",
      meetingId: "meeting-1",
      version: 4,
      snapshot: { summary: "새로고침 완료" },
      generatedAt: "2026-08-12T10:05:00Z",
    });

    renderWithQueryClient(<ReportWorkspace meetingId="meeting-1" />);

    expect(await screen.findByText("최신 회의 리포트")).toBeInTheDocument();
    expect(screen.getByText("3")).toBeInTheDocument();
    expect(screen.getByText(/최신 투표 결과 반영/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "리포트 새로고침" }));

    await waitFor(() =>
      expect(apiClient.refreshReport).toHaveBeenCalledWith("meeting-1"),
    );
  });
});

import React from "react";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { SearchWorkspace } from "@/components/search-workspace";
import { apiClient } from "@/lib/api/client";
import { renderWithQueryClient } from "@/test-utils/render-with-query-client";

vi.mock("@/lib/api/client", async () => {
  const actual = await vi.importActual<typeof import("@/lib/api/client")>("@/lib/api/client");
  return {
    ...actual,
    apiClient: {
      ...actual.apiClient,
      searchMeeting: vi.fn(),
    },
  };
});

describe("SearchWorkspace", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("submits a query and renders grouped search results", async () => {
    vi.mocked(apiClient.searchMeeting).mockResolvedValue({
      meetingId: "meeting-1",
      query: "가평",
      messages: [
        {
          id: "msg-1",
          threadId: "thread-1",
          body: "가평 숙소 예산을 정리했습니다.",
          createdAt: "2026-08-12T09:00:00Z",
        },
      ],
      decisions: [
        {
          id: "decision-1",
          threadId: "thread-1",
          title: "숙소 지역 확정",
          status: "VOTING",
        },
      ],
      tasks: [
        {
          id: "task-1",
          meetingId: "meeting-1",
          title: "가평 후보 비교표 정리",
          status: "todo",
          createdAt: "2026-08-12T09:30:00Z",
          dueAt: null,
        },
      ],
    });

    renderWithQueryClient(<SearchWorkspace meetingId="meeting-1" />);

    await userEvent.type(screen.getByPlaceholderText("예: 가평, 일정, 확정"), "가평");
    await userEvent.click(screen.getByRole("button", { name: "검색 실행" }));

    await waitFor(() =>
      expect(apiClient.searchMeeting).toHaveBeenCalledWith("meeting-1", "가평"),
    );

    expect(await screen.findByText("숙소 지역 확정")).toBeInTheDocument();
    expect(screen.getByText("가평 숙소 예산을 정리했습니다.")).toBeInTheDocument();
    expect(screen.getByText("가평 후보 비교표 정리")).toBeInTheDocument();
  });
});

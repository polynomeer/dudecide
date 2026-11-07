import React from "react";
import { cleanup, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { DecisionSuggestionCard } from "@/components/thread/decision-suggestion-card";
import { apiClient, type DecisionSuggestionView } from "@/lib/api/client";
import { renderWithQueryClient } from "@/test-utils/render-with-query-client";

const push = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));

vi.mock("@/lib/api/client", async () => {
  const actual = await vi.importActual<typeof import("@/lib/api/client")>("@/lib/api/client");
  return {
    ...actual,
    apiClient: {
      ...actual.apiClient,
      acceptDecisionSuggestion: vi.fn(),
      dismissDecisionSuggestion: vi.fn(),
    },
  };
});

const suggestion: DecisionSuggestionView = {
  id: "s-1",
  meetingId: "meeting-1",
  threadId: "thread-1",
  status: "pending",
  analysisStatus: "likely_decided",
  decisionType: "lodging",
  title: "Lodging",
  proposedResult: { option: "애월 독채" },
  evidence: [
    { messageId: "m-1", role: "proposal" },
    { messageId: "m-2", role: "support" },
    { messageId: "m-3", role: "opposition" },
  ],
  createdAt: "2026-11-01T10:00:00Z",
};

describe("DecisionSuggestionCard", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("lets a host turn the suggestion into a draft and opens it", async () => {
    vi.mocked(apiClient.acceptDecisionSuggestion).mockResolvedValue({
      id: "d-1",
      meetingId: "meeting-1",
      threadId: "thread-1",
      decisionType: "lodging",
      title: "Lodging",
      status: "proposed",
      proposedResult: {},
      confirmedResult: {},
    });
    renderWithQueryClient(<DecisionSuggestionCard meetingId="meeting-1" suggestion={suggestion} canManage />);

    const card = screen.getByRole("region", { name: "결정 후보" });
    expect(card).toHaveTextContent("숙소: 애월 독채");
    expect(card).toHaveTextContent("근거 메시지 2개와 반대 의견 1개");

    await userEvent.click(screen.getByRole("button", { name: "초안 만들기" }));

    await waitFor(() => expect(push).toHaveBeenCalledWith("/meetings/meeting-1/decisions?decision=d-1"));
    expect(apiClient.acceptDecisionSuggestion).toHaveBeenCalledWith("s-1");
  });

  it("only explains the next step to members who cannot act", () => {
    renderWithQueryClient(<DecisionSuggestionCard meetingId="meeting-1" suggestion={suggestion} canManage={false} />);

    expect(screen.queryByRole("button", { name: "초안 만들기" })).toBeNull();
    expect(screen.getByText("호스트가 확인하면 결정 보드에 올라가요.")).toBeInTheDocument();
  });
});

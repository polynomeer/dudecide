import React from "react";
import { cleanup, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ImportReview } from "@/components/import/import-review";
import { apiClient, type ChatImportView } from "@/lib/api/client";
import { renderWithQueryClient } from "@/test-utils/render-with-query-client";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock("@/lib/session", () => ({
  useMeetingHostAccess: () => ({
    isHost: true,
    actorQuery: { data: { userId: "host-1" } },
    meetingQuery: { data: { ownerUserId: "host-1" } },
  }),
}));

vi.mock("@/lib/api/client", async () => {
  const actual = await vi.importActual<typeof import("@/lib/api/client")>("@/lib/api/client");
  return {
    ...actual,
    apiClient: {
      ...actual.apiClient,
      getChatImport: vi.fn(),
      getChatImportEvidence: vi.fn(),
      applyChatImport: vi.fn(),
      createInviteLink: vi.fn(),
    },
  };
});

const baseView: ChatImportView = {
  id: "import-1",
  meetingId: "meeting-1",
  threadId: "thread-1",
  uploadedByUserId: "host-1",
  sourceType: "kakao_export",
  sourceFormat: "kakao_android",
  sourceRoomName: "제주 여행",
  rangeStart: "2026-10-01T21:00:00+09:00",
  rangeEnd: "2026-10-02T09:10:00+09:00",
  messageCount: 6,
  duplicateCount: 0,
  status: "ready",
  progress: { stage: "done" },
  createdAt: "2026-10-02T10:00:00+09:00",
};

const result = {
  digest: "1 likely decided, 1 contested, 0 still open, 1 open questions, 1 tasks.",
  appliedItemIds: [],
  items: [
    {
      itemId: "it_1",
      kind: "decision",
      status: "likely_decided",
      applicable: true,
      decisionType: "date",
      title: "Date",
      proposedResult: { option: "18일" },
      options: [{ label: "18일", supporters: 3, objectors: 0, superseded: false }],
      evidence: [{ messageId: "m-1", role: "proposal" }],
    },
    {
      itemId: "it_2",
      kind: "decision",
      status: "contested",
      applicable: true,
      decisionType: "lodging",
      title: "Lodging",
      options: [
        { label: "애월", supporters: 2, objectors: 1, superseded: false },
        { label: "서귀포", supporters: 2, objectors: 0, superseded: false },
      ],
      evidence: [],
    },
    { itemId: "it_3", kind: "open_question", title: "숙소는 어디로 하지?", evidence: [] },
    { itemId: "it_4", kind: "task", title: "배편은 제가 예약할게요", evidence: [] },
    { itemId: "it_5", kind: "decision", status: "abandoned", applicable: false, title: "Budget", evidence: [] },
  ],
};

describe("ImportReview", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("shows analysis progress while the import is analyzing", async () => {
    vi.mocked(apiClient.getChatImport).mockResolvedValue({
      ...baseView,
      status: "analyzing",
      progress: { stage: "map", done: 3, total: 6 },
    });

    renderWithQueryClient(<ImportReview meetingId="meeting-1" importId="import-1" />);

    expect(await screen.findByText("대화 구간별로 읽고 있습니다")).toBeInTheDocument();
    expect(screen.getByRole("progressbar")).toHaveAttribute("aria-valuenow", "50");
  });

  it("groups staged items, preselects likely decisions and tasks, and applies the selection", async () => {
    vi.mocked(apiClient.getChatImport).mockResolvedValue({ ...baseView, result });
    vi.mocked(apiClient.getChatImportEvidence).mockResolvedValue([
      {
        id: "m-1",
        threadId: "thread-1",
        senderUserId: null,
        origin: "imported",
        speaker: { displayName: "Participant A", pseudonymized: true },
        body: "18일 어때?",
        attachments: [],
        aiLabels: {},
        createdAt: "2026-10-01T21:00:00+09:00",
      },
    ]);
    vi.mocked(apiClient.applyChatImport).mockResolvedValue({
      importId: "import-1",
      appliedItemIds: ["it_1", "it_4"],
      skippedItemIds: [],
      decisionIds: ["decision-1"],
      taskIds: ["task-1"],
      meetingStatus: "active",
    });
    const user = userEvent.setup();

    renderWithQueryClient(<ImportReview meetingId="meeting-1" importId="import-1" />);

    const decided = await screen.findByRole("region", { name: "결정된 것으로 보이는 항목" });
    expect(within(decided).getByRole("checkbox", { name: "Date: 18일 반영" })).toBeChecked();
    await waitFor(() => expect(within(decided).getByText("18일 어때?")).toBeInTheDocument());
    expect(within(decided).getByText(/Participant A/)).toBeInTheDocument();

    const contested = screen.getByRole("region", { name: "의견이 갈린 항목" });
    expect(within(contested).getByRole("checkbox")).not.toBeChecked();
    expect(within(contested).getByText(/서귀포 · 찬성 2 · 반대 0/)).toBeInTheDocument();

    expect(within(screen.getByRole("region", { name: "답이 없는 질문" })).queryByRole("checkbox")).toBeNull();
    expect(within(screen.getByRole("region", { name: "흐지부지된 제안" })).queryByRole("checkbox")).toBeNull();
    expect(within(screen.getByRole("region", { name: "할 일" })).getByRole("checkbox")).toBeChecked();

    await user.click(screen.getByRole("button", { name: "2개 항목 반영" }));

    await waitFor(() =>
      expect(apiClient.applyChatImport).toHaveBeenCalledWith("import-1", ["it_1", "it_4"], expect.any(String)),
    );
    expect(await screen.findByRole("status")).toHaveTextContent("결정 1건, 할 일 1건을 반영했습니다.");

    vi.mocked(apiClient.createInviteLink).mockResolvedValue({
      id: "invite-1",
      meetingId: "meeting-1",
      token: "tok-123",
      status: "active",
      createdAt: "2026-10-02T10:00:00+09:00",
    });
    await user.click(screen.getByRole("button", { name: "초대 링크 만들기" }));
    expect(await screen.findByLabelText("초대 링크")).toHaveValue(`${window.location.origin}/invite/tok-123`);
  });

  it("explains a failed analysis", async () => {
    vi.mocked(apiClient.getChatImport).mockResolvedValue({
      ...baseView,
      status: "failed",
      failureReason: "map stage failed for 2 of 2 episodes",
    });

    renderWithQueryClient(<ImportReview meetingId="meeting-1" importId="import-1" />);

    expect(await screen.findByRole("alert")).toHaveTextContent("map stage failed");
    expect(screen.getByRole("link", { name: "다시 가져오기" })).toHaveAttribute("href", "/meetings/meeting-1/import");
  });
});

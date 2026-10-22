import React from "react";
import { cleanup, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { InvitePreview } from "@/components/import/invite-preview";
import { SpeakerClaimCard } from "@/components/import/speaker-claim-card";
import { ApiError, apiClient, type ImportSpeakerView } from "@/lib/api/client";
import { renderWithQueryClient } from "@/test-utils/render-with-query-client";

vi.mock("@/lib/api/client", async () => {
  const actual = await vi.importActual<typeof import("@/lib/api/client")>("@/lib/api/client");
  return {
    ...actual,
    apiClient: {
      ...actual.apiClient,
      previewInvite: vi.fn(),
      listImportSpeakers: vi.fn(),
      claimImportSpeaker: vi.fn(),
    },
  };
});

const speaker = (overrides: Partial<ImportSpeakerView>): ImportSpeakerView => ({
  id: "speaker-1",
  threadId: "thread-1",
  label: "Participant B",
  mapped: false,
  mine: false,
  ...overrides,
});

describe("InvitePreview", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("shows titles and counts that wait for the invitee", async () => {
    vi.mocked(apiClient.previewInvite).mockResolvedValue({
      meetingTitle: "제주 여행",
      meetingStatus: "active",
      memberCount: 3,
      decisions: [{ title: "Date", decisionType: "date", status: "proposed" }],
      pendingDecisionCount: 1,
      confirmedDecisionCount: 0,
      openTaskCount: 1,
      openQuestionCount: 1,
    });

    renderWithQueryClient(<InvitePreview token="token-1" />);

    const preview = await screen.findByRole("region", { name: "모임 미리보기" });
    expect(preview).toHaveTextContent("제주 여행");
    expect(preview).toHaveTextContent("2개 항목이 의견을 기다리고 있어요.");
    expect(preview).toHaveTextContent("Date");
    expect(preview).toHaveTextContent("확인 필요");
  });

  it("explains an expired link", async () => {
    vi.mocked(apiClient.previewInvite).mockRejectedValue(new ApiError(410, "gone"));

    renderWithQueryClient(<InvitePreview token="token-1" />);

    expect(await screen.findByRole("alert")).toHaveTextContent("만료된 초대 링크");
  });
});

describe("SpeakerClaimCard", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    window.localStorage.clear();
  });

  afterEach(() => {
    cleanup();
  });

  it("stays hidden when the member already claimed a speaker", async () => {
    vi.mocked(apiClient.listImportSpeakers).mockResolvedValue([speaker({ mapped: true, mine: true }), speaker({ id: "s2" })]);

    renderWithQueryClient(<SpeakerClaimCard meetingId="meeting-1" />);

    await waitFor(() => expect(apiClient.listImportSpeakers).toHaveBeenCalled());
    expect(screen.queryByRole("complementary", { name: "대화 속 내 이름 연결" })).toBeNull();
  });

  it("claims by typed name and explains a miss", async () => {
    vi.mocked(apiClient.listImportSpeakers).mockResolvedValue([speaker({})]);
    vi.mocked(apiClient.claimImportSpeaker)
      .mockRejectedValueOnce(new ApiError(404, "not found"))
      .mockResolvedValueOnce({ claimedSpeakerIds: ["speaker-1"] });
    const user = userEvent.setup();

    renderWithQueryClient(<SpeakerClaimCard meetingId="meeting-1" />);

    const input = await screen.findByLabelText("카카오톡 이름");
    expect(screen.queryByText("Participant B")).toBeNull();
    await user.type(input, "준오");
    await user.click(screen.getByRole("button", { name: "연결" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("찾지 못했어요");

    await user.clear(input);
    await user.type(input, " 준호 ");
    await user.click(screen.getByRole("button", { name: "연결" }));
    await waitFor(() => expect(apiClient.claimImportSpeaker).toHaveBeenLastCalledWith("meeting-1", "준호"));
  });

  it("remembers a dismissal per meeting", async () => {
    vi.mocked(apiClient.listImportSpeakers).mockResolvedValue([speaker({})]);
    const user = userEvent.setup();

    renderWithQueryClient(<SpeakerClaimCard meetingId="meeting-1" />);
    await user.click(await screen.findByRole("button", { name: "나중에 할게요" }));

    expect(screen.queryByLabelText("카카오톡 이름")).toBeNull();
    expect(window.localStorage.getItem("dudecide:speaker-claim-dismissed:meeting-1")).toBe("1");
  });
});

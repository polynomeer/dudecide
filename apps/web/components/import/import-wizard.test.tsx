import React from "react";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { cleanup, fireEvent, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ImportWizard } from "@/components/import/import-wizard";
import { apiClient } from "@/lib/api/client";
import { renderWithQueryClient } from "@/test-utils/render-with-query-client";

const push = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push }),
}));

vi.mock("@/lib/api/client", async () => {
  const actual = await vi.importActual<typeof import("@/lib/api/client")>("@/lib/api/client");
  return {
    ...actual,
    apiClient: {
      ...actual.apiClient,
      createChatImport: vi.fn(),
      createMeetingChatImport: vi.fn(),
      listChatImports: vi.fn(),
    },
  };
});

const androidExport = readFileSync(resolve(__dirname, "../../lib/import/__fixtures__/kakao-android-ko.txt"), "utf8");

describe("ImportWizard", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("previews a pasted export and submits only the masked payload", async () => {
    vi.mocked(apiClient.createChatImport).mockResolvedValue({
      importId: "import-1",
      meetingId: "meeting-1",
      threadId: "thread-1",
      status: "submitted",
      messageCount: 6,
      duplicateCount: 0,
    });
    const user = userEvent.setup();
    renderWithQueryClient(<ImportWizard />);

    fireEvent.change(screen.getByLabelText("대화 붙여넣기"), { target: { value: androidExport } });

    const preview = screen.getByRole("region", { name: "가져오기 미리보기" });
    expect(preview).toHaveTextContent("카카오톡 Android 내보내기");
    expect(preview).toHaveTextContent("제주 여행");
    expect(preview).toHaveTextContent("6개");
    expect(preview).toHaveTextContent("2건");
    expect(preview).toHaveTextContent("입금은 카카오뱅크 [account] 으로 해줘");

    const submit = screen.getByRole("button", { name: "분석 시작" });
    expect(submit).toBeDisabled();

    await user.click(screen.getByRole("radio", { name: /민지/ }));
    await user.click(screen.getByRole("checkbox"));
    await user.click(submit);

    await waitFor(() => expect(push).toHaveBeenCalledWith("/meetings/meeting-1/import/import-1"));
    const [payload, idempotencyKey] = vi.mocked(apiClient.createChatImport).mock.calls[0];
    expect(idempotencyKey).toEqual(expect.any(String));
    expect(payload.speakers[0]).toMatchObject({ displayName: "민지", isSelf: true });
    expect(payload.consent).toEqual({ isParticipant: true, version: "chat-import-consent-v1" });
    expect(JSON.stringify(payload)).not.toContain("010-1234-5678");
    expect(JSON.stringify(payload)).not.toContain("3333-01-2345678");
  });

  it("reports text without readable messages", () => {
    renderWithQueryClient(<ImportWizard />);

    fireEvent.change(screen.getByLabelText("대화 붙여넣기"), { target: { value: "   \n  " } });
    expect(screen.queryByRole("region", { name: "가져오기 미리보기" })).not.toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("대화 붙여넣기"), { target: { value: "사진\n사진" } });
    expect(screen.getByRole("alert")).toHaveTextContent("읽을 수 있는 메시지를 찾지 못했습니다");
  });

  it("imports into an existing meeting starting after the previous import", async () => {
    vi.mocked(apiClient.listChatImports).mockResolvedValue([
      {
        id: "import-0",
        meetingId: "meeting-1",
        threadId: "thread-1",
        uploadedByUserId: "host-1",
        sourceType: "kakao_export",
        sourceFormat: "kakao_android",
        sourceRoomName: "제주 여행",
        rangeStart: "2026-09-01T00:00:00+09:00",
        rangeEnd: "2026-10-02T00:00:00+09:00",
        messageCount: 4,
        duplicateCount: 0,
        status: "applied",
        progress: {},
        createdAt: "2026-10-02T00:10:00+09:00",
      },
    ]);
    vi.mocked(apiClient.createMeetingChatImport).mockResolvedValue({
      importId: "import-2",
      meetingId: "meeting-1",
      threadId: "thread-1",
      status: "submitted",
      messageCount: 2,
      duplicateCount: 0,
    });
    const user = userEvent.setup();
    renderWithQueryClient(<ImportWizard meetingId="meeting-1" />);

    fireEvent.change(screen.getByLabelText("대화 붙여넣기"), { target: { value: androidExport } });
    await screen.findByRole("button", { name: "지난번 이후" });
    await user.click(screen.getByRole("checkbox"));
    await user.click(screen.getByRole("button", { name: "분석 시작" }));

    await waitFor(() => expect(apiClient.createMeetingChatImport).toHaveBeenCalled());
    const [meetingId, payload] = vi.mocked(apiClient.createMeetingChatImport).mock.calls[0];
    expect(meetingId).toBe("meeting-1");
    expect(payload.messages).toHaveLength(2);
  });
});

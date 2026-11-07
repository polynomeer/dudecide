import React from "react";
import { cleanup, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { CreateGuestSessionForm } from "@/components/forms/create-guest-session-form";
import { JoinInviteForm } from "@/components/forms/join-invite-form";
import { ApiError, apiClient } from "@/lib/api/client";
import { renderWithQueryClient } from "@/test-utils/render-with-query-client";

const push = vi.fn();
const replace = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push, replace }) }));

vi.mock("@/lib/api/client", async () => {
  const actual = await vi.importActual<typeof import("@/lib/api/client")>("@/lib/api/client");
  return {
    ...actual,
    apiClient: {
      ...actual.apiClient,
      getCurrentActor: vi.fn(),
      createGuestSession: vi.fn(),
      joinInvite: vi.fn(),
    },
  };
});

const actor = { userId: "user-1", displayName: "지수", authSource: "session" };

describe("forms with an existing session", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("skips guest start when a session already exists", async () => {
    vi.mocked(apiClient.getCurrentActor).mockResolvedValue(actor);

    renderWithQueryClient(<CreateGuestSessionForm next="/import" />);

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/import"));
    expect(apiClient.createGuestSession).not.toHaveBeenCalled();
  });

  it("shows a readable message when guest start is rate limited", async () => {
    vi.mocked(apiClient.getCurrentActor).mockRejectedValue(new ApiError(401, "session required"));
    vi.mocked(apiClient.createGuestSession).mockRejectedValue(new ApiError(429, '{"status":429}'));

    renderWithQueryClient(<CreateGuestSessionForm />);
    await userEvent.type(screen.getByLabelText("모임에서 보일 이름"), "지수");
    await userEvent.click(screen.getByRole("button", { name: "시작하기" }));

    expect(await screen.findByText("요청이 너무 많아요. 잠시 후 다시 시도해 주세요.")).toBeInTheDocument();
  });

  it("joins an invite as the current user without asking for a name", async () => {
    vi.mocked(apiClient.getCurrentActor).mockResolvedValue(actor);
    vi.mocked(apiClient.joinInvite).mockResolvedValue({
      id: "m-1",
      meetingId: "meeting-1",
      userId: "user-1",
      displayName: "지수",
      role: "guest",
      attendanceStatus: "accepted",
      joinedAt: "2026-11-04T10:00:00Z",
    });

    renderWithQueryClient(<JoinInviteForm token="token-1" />);
    await screen.findByText("이름으로 참여해요.");
    await userEvent.click(screen.getByRole("button", { name: "참여하기" }));

    expect(screen.queryByLabelText("모임에서 보일 이름")).toBeNull();
    await waitFor(() => expect(push).toHaveBeenCalledWith("/meetings/meeting-1"));
    expect(vi.mocked(apiClient.joinInvite).mock.calls[0]).toEqual(["token-1", { displayName: "지수" }]);
  });
});

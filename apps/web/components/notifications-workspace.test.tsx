import React from "react";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { NotificationsWorkspace } from "@/components/notifications-workspace";
import { apiClient, type NotificationView } from "@/lib/api/client";
import { renderWithQueryClient } from "@/test-utils/render-with-query-client";

vi.mock("@/lib/api/client", async () => {
  const actual = await vi.importActual<typeof import("@/lib/api/client")>("@/lib/api/client");
  return {
    ...actual,
    apiClient: {
      ...actual.apiClient,
      listNotifications: vi.fn(),
      listMembers: vi.fn(),
      markNotificationRead: vi.fn(),
      markAllNotificationsRead: vi.fn(),
    },
  };
});

const base = { meetingId: "meeting-1", recipientUserId: "user-1", createdAt: "2026-10-20T10:00:00Z" };
const notifications: NotificationView[] = [
  { ...base, id: "n-1", type: "invite_joined", payload: { userId: "user-2" }, readAt: null },
  { ...base, id: "n-2", type: "decision_confirmed", payload: { decisionId: "d-1", title: "날짜" }, readAt: null },
  { ...base, id: "n-3", type: "report_ready", payload: { version: 2 }, readAt: "2026-10-20T11:00:00Z" },
];

describe("NotificationsWorkspace", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(apiClient.listNotifications).mockResolvedValue(notifications);
    vi.mocked(apiClient.listMembers).mockResolvedValue([
      {
        id: "m-2",
        meetingId: "meeting-1",
        userId: "user-2",
        displayName: "도윤",
        role: "guest",
        attendanceStatus: "accepted",
        joinedAt: "2026-10-20T09:00:00Z",
      },
    ]);
    vi.mocked(apiClient.markAllNotificationsRead).mockResolvedValue({ updatedCount: 2 });
  });

  it("names the member who joined and counts unread notifications", async () => {
    renderWithQueryClient(<NotificationsWorkspace meetingId="meeting-1" />);

    expect(await screen.findByText("도윤님이 참여했어요")).toBeInTheDocument();
    expect(screen.getByText("안 읽음 2")).toBeInTheDocument();
    expect(screen.getAllByLabelText("안 읽음")).toHaveLength(2);
  });

  it("marks everything read at once", async () => {
    let allRead = false;
    vi.mocked(apiClient.listNotifications).mockImplementation(async () =>
      allRead ? notifications.map((item) => ({ ...item, readAt: item.readAt ?? "2026-10-21T09:00:00Z" })) : notifications,
    );
    vi.mocked(apiClient.markAllNotificationsRead).mockImplementation(async () => {
      allRead = true;
      return { updatedCount: 2 };
    });
    renderWithQueryClient(<NotificationsWorkspace meetingId="meeting-1" />);

    await userEvent.click(await screen.findByRole("button", { name: "모두 읽음" }));

    await waitFor(() => expect(apiClient.markAllNotificationsRead).toHaveBeenCalledWith("meeting-1"));
    expect(await screen.findByText("모두 읽었어요")).toBeInTheDocument();
  });
});

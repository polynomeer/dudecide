import React from "react";
import { cleanup, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { SessionGate } from "@/components/session-gate";
import { ApiError, apiClient } from "@/lib/api/client";
import { renderWithQueryClient } from "@/test-utils/render-with-query-client";

const replace = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace }),
  usePathname: () => "/meetings/new",
}));

vi.mock("@/lib/api/client", async () => {
  const actual = await vi.importActual<typeof import("@/lib/api/client")>("@/lib/api/client");
  return {
    ...actual,
    apiClient: { ...actual.apiClient, getCurrentActor: vi.fn() },
  };
});

describe("SessionGate", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("redirects to guest start and remembers where the visitor was going", async () => {
    vi.mocked(apiClient.getCurrentActor).mockRejectedValue(new ApiError(401, "session required"));

    renderWithQueryClient(<SessionGate>내용</SessionGate>);

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/start?next=%2Fmeetings%2Fnew"));
    expect(screen.queryByText("내용")).toBeNull();
  });

  it("keeps the page for signed-in visitors and for other failures", async () => {
    vi.mocked(apiClient.getCurrentActor).mockRejectedValue(new ApiError(500, "down"));

    renderWithQueryClient(<SessionGate>내용</SessionGate>);

    expect(await screen.findByText("내용")).toBeInTheDocument();
    await waitFor(() => expect(apiClient.getCurrentActor).toHaveBeenCalled());
    expect(replace).not.toHaveBeenCalled();
  });
});

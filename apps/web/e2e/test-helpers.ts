import type { Page, Route } from "@playwright/test";

const API_BASE_URL = "http://localhost:8080";

export async function stubRealtimeBridge(page: Page) {
  await page.addInitScript(() => {
    class MockEventSource implements EventSource {
      readonly CLOSED = 2;
      readonly CONNECTING = 0;
      readonly OPEN = 1;
      onerror: ((this: EventSource, ev: Event) => unknown) | null = null;
      onmessage: ((this: EventSource, ev: MessageEvent) => unknown) | null = null;
      onopen: ((this: EventSource, ev: Event) => unknown) | null = null;
      readyState = 1;
      url: string;
      withCredentials: boolean;

      constructor(url: string | URL, eventSourceInitDict?: EventSourceInit) {
        this.url = String(url);
        this.withCredentials = eventSourceInitDict?.withCredentials ?? false;
        window.setTimeout(() => {
          this.onopen?.call(this, new Event("open"));
        }, 0);
      }

      addEventListener() {}
      removeEventListener() {}
      dispatchEvent() {
        return true;
      }
      close() {
        this.readyState = this.CLOSED;
      }
    }

    Object.defineProperty(window, "EventSource", {
      configurable: true,
      writable: true,
      value: MockEventSource,
    });
  });
}

export async function stubCurrentActor(
  page: Page,
  actor: { userId: string; displayName: string } = {
    userId: "user-host-1",
    displayName: "Host One",
  },
) {
  await page.route(`${API_BASE_URL}/api/v1/auth/me`, async (route) => {
    await fulfillJson(route, {
      userId: actor.userId,
      displayName: actor.displayName,
      authSource: "guest_session",
    });
  });
}

/** `/auth/me` answers 401 until the page creates a guest session or joins an invite, like a first-time visitor. */
export async function stubCurrentActorAfterSignIn(
  page: Page,
  actor: { userId: string; displayName: string } = {
    userId: "user-host-1",
    displayName: "Host One",
  },
) {
  let signedIn = false;
  page.on("request", (request) => {
    if (request.method() === "POST" && /\/auth\/guest-sessions$|\/invite-links\/[^/]+\/join$/.test(request.url())) {
      signedIn = true;
    }
  });
  await page.route(`${API_BASE_URL}/api/v1/auth/me`, async (route) => {
    if (signedIn) {
      await fulfillJson(route, { ...actor, authSource: "session" });
    } else {
      await fulfillJson(route, { status: 401 }, 401);
    }
  });
}

export async function stubGuestSessionCreation(page: Page) {
  await page.route(`${API_BASE_URL}/api/v1/auth/guest-sessions`, async (route) => {
    const body = route.request().postDataJSON() as { displayName?: string };
    await fulfillJson(route, {
      userId: "user-host-1",
      displayName: body.displayName ?? "Host One",
      authSource: "guest_session",
    });
  });
}

export async function stubMeetingCreation(page: Page, meetingId = "meeting-e2e-1") {
  await page.route(`${API_BASE_URL}/api/v1/meetings`, async (route) => {
    const body = route.request().postDataJSON() as {
      title?: string;
      meetingType?: string;
    };
    await fulfillJson(route, {
      id: meetingId,
      title: body.title ?? "Autumn Cabin Trip",
      meetingType: body.meetingType ?? "trip",
      status: "draft",
    });
  });
}

export async function stubMeetingOverview(
  page: Page,
  meetingId = "meeting-e2e-1",
  actorUserId = "user-host-1",
) {
  await page.route(`${API_BASE_URL}/api/v1/meetings/${meetingId}`, async (route) => {
    await fulfillJson(route, {
      id: meetingId,
      title: "2026 Autumn Cabin Trip",
      meetingType: "trip",
      status: "draft",
      originRegion: "Seoul",
      expectedHeadcount: 8,
      ownerUserId: actorUserId,
      memberCount: 4,
      decisionCount: 1,
      openVoteCount: 1,
      threads: [
        {
          id: "thread-1",
          category: "agenda",
          title: "Destination shortlist",
          status: "open",
          sortOrder: 0,
          messageCount: 3,
          decisionCount: 1,
          openVoteCount: 1,
          lastActivityAt: "2026-08-12T10:00:00Z",
        },
      ],
    });
  });
}

export async function stubDecisionList(page: Page, meetingId = "meeting-e2e-1") {
  await page.route(`${API_BASE_URL}/api/v1/meetings/${meetingId}/decisions`, async (route) => {
    await fulfillJson(route, [
      {
        id: "decision-1",
        meetingId,
        threadId: "thread-1",
        decisionType: "destination",
        title: "Pick shortlist",
        status: "proposed",
        proposedResult: {},
        confirmedResult: {},
        decidedAt: null,
      },
    ]);
  });
}

export async function stubInviteJoin(page: Page, token = "invite-token-1") {
  await page.route(`${API_BASE_URL}/api/v1/invite-links/${token}/join`, async (route) => {
    const body = route.request().postDataJSON() as { displayName?: string };
    await fulfillJson(route, {
      id: "membership-1",
      meetingId: "meeting-invite-1",
      userId: "user-guest-1",
      displayName: body.displayName ?? "Guest One",
      role: "member",
      attendanceStatus: "attending",
      joinedAt: "2026-08-12T10:00:00Z",
    });
  });
}

export async function stubInviteMeetingOverview(page: Page) {
  await stubMeetingOverview(page, "meeting-invite-1", "user-host-1");
  await stubDecisionList(page, "meeting-invite-1");
}

async function fulfillJson(route: Route, body: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: "application/json",
    body: JSON.stringify(body),
  });
}

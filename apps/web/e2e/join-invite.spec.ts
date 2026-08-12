import { expect, test } from "@playwright/test";
import {
  stubCurrentActor,
  stubDecisionList,
  stubInviteJoin,
  stubInviteMeetingOverview,
  stubRealtimeBridge,
} from "./test-helpers";

test("invite join redirects into the target meeting workspace", async ({ page }) => {
  await stubRealtimeBridge(page);
  await stubCurrentActor(page, {
    userId: "user-guest-1",
    displayName: "Guest One",
  });
  await stubInviteJoin(page, "invite-token-1");
  await stubInviteMeetingOverview(page);
  await stubDecisionList(page, "meeting-invite-1");

  await page.goto("/invite/invite-token-1");

  await page.getByPlaceholder("e.g. Cabin Driver").fill("Guest One");
  await page.getByRole("button", { name: "Join meeting" }).click();

  await expect(page).toHaveURL(/\/meetings\/meeting-invite-1$/);
  await expect(page.getByRole("heading", { name: "2026 Autumn Cabin Trip" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Decision board" })).toBeVisible();
  await expect(page.getByText(/Realtime/i)).toBeVisible();
});

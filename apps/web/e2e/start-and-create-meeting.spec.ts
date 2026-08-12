import { expect, test } from "@playwright/test";
import {
  stubCurrentActor,
  stubDecisionList,
  stubGuestSessionCreation,
  stubMeetingCreation,
  stubMeetingOverview,
  stubRealtimeBridge,
} from "./test-helpers";

test("guest session creation redirects into meeting setup and overview", async ({ page }) => {
  await stubRealtimeBridge(page);
  await stubCurrentActor(page);
  await stubGuestSessionCreation(page);
  await stubMeetingCreation(page, "meeting-e2e-1");
  await stubMeetingOverview(page, "meeting-e2e-1");
  await stubDecisionList(page, "meeting-e2e-1");

  await page.goto("/start");

  await page.getByPlaceholder("e.g. Summer Trip Host").fill("Planning Host");
  await page.getByRole("button", { name: "Create guest session" }).click();

  await expect(page).toHaveURL(/\/meetings\/new$/);
  await expect(page.getByRole("heading", { name: "Create the planning room" })).toBeVisible();

  await page.getByPlaceholder("e.g. 2026 Autumn Cabin Trip").fill("2026 Autumn Cabin Trip");
  await page.getByPlaceholder("8").fill("8");
  await page.getByPlaceholder("Seoul").fill("Seoul");
  await page.getByRole("button", { name: "Create meeting" }).click();

  await expect(page).toHaveURL(/\/meetings\/meeting-e2e-1$/);
  await expect(page.getByRole("heading", { name: "2026 Autumn Cabin Trip" })).toBeVisible();
  await expect(page.getByRole("link", { name: /Destination shortlist/i })).toBeVisible();
  await expect(page.getByText(/Realtime/i)).toBeVisible();
});

import { expect, test } from "@playwright/test";
import {
  stubCurrentActorAfterSignIn,
  stubDecisionList,
  stubGuestSessionCreation,
  stubMeetingCreation,
  stubMeetingOverview,
  stubRealtimeBridge,
} from "./test-helpers";

test("guest session creation redirects into meeting setup and overview", async ({ page }) => {
  await stubRealtimeBridge(page);
  await stubCurrentActorAfterSignIn(page);
  await stubGuestSessionCreation(page);
  await stubMeetingCreation(page, "meeting-e2e-1");
  await stubMeetingOverview(page, "meeting-e2e-1");
  await stubDecisionList(page, "meeting-e2e-1");

  await page.goto("/start");

  await page.getByLabel("모임에서 보일 이름").fill("Planning Host");
  await page.getByRole("button", { name: "시작하기" }).click();

  await expect(page).toHaveURL(/\/meetings\/new$/);
  await expect(page.getByRole("heading", { name: "어떤 모임이에요?" })).toBeVisible();

  await page.getByLabel("모임 이름").fill("2026 Autumn Cabin Trip");
  await page.getByRole("button", { name: "한 명 늘리기" }).click();
  await page.getByLabel("출발 지역").fill("Seoul");
  await page.getByRole("button", { name: "모임 만들기" }).click();

  await expect(page).toHaveURL(/\/meetings\/meeting-e2e-1$/);
  await expect(page.getByRole("heading", { name: "2026 Autumn Cabin Trip" })).toBeVisible();
  await expect(page.getByRole("link", { name: /Destination shortlist/i })).toBeVisible();
  await expect(page.getByText(/Realtime/i)).toBeVisible();
});

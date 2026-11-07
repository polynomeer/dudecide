import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { buildImportPayload, defaultRange, parseChat } from "@/lib/import";

const KST = { utcOffsetMinutes: 540 };
const CONSENT = { isParticipant: true, version: "chat-import-consent-v1" };

const android = parseChat(
  readFileSync(resolve(__dirname, "__fixtures__", "kakao-android-ko.txt"), "utf8"),
  KST,
);

describe("defaultRange", () => {
  it("covers the export when it is shorter than 30 days", () => {
    expect(defaultRange(android.messages)).toEqual({
      start: "2026-10-01T21:00:00+09:00",
      end: "2026-10-02T09:10:00+09:00",
    });
  });

  it("starts at the previous import when that is later", () => {
    expect(defaultRange(android.messages, "2026-10-02T00:00:00+09:00")?.start).toBe("2026-10-02T00:05:00+09:00");
    expect(defaultRange(android.messages, "2026-10-03T00:00:00+09:00")).toBeNull();
  });
});

describe("buildImportPayload", () => {
  it("uploads masked text messages only, with speaker keys and media counts", () => {
    const range = defaultRange(android.messages);
    const result = buildImportPayload(android, { range: range!, selfSpeaker: "민지", consent: CONSENT });

    expect(result.ok).toBe(true);
    if (!result.ok) return;
    const { payload } = result;
    expect(payload.source).toEqual({
      type: "kakao_export",
      format: "kakao_android",
      formatVersion: "android-ko-1",
      roomName: "제주 여행",
    });
    expect(payload.speakers).toEqual([
      { key: "s1", displayName: "민지", isSelf: true, membershipId: null },
      { key: "s2", displayName: "준호", isSelf: false, membershipId: null },
      { key: "s3", displayName: "서연", isSelf: false, membershipId: null },
    ]);
    expect(payload.messages).toHaveLength(6);
    expect(payload.messages.map((message) => message.body)).toContain("입금은 카카오뱅크 [account] 으로 해줘");
    expect(payload.messages.map((message) => message.body)).toContain("제 번호 [phone]");
    expect(payload.mediaCounts).toEqual([{ date: "2026-10-01", count: 1 }]);
    expect(payload.stats).toEqual({ parsedCount: 7, uploadedCount: 6, maskedCount: 2, unparsedLineCount: 0 });
    expect(JSON.stringify(payload)).not.toContain("010-1234-5678");
  });

  it("drops messages outside the chosen range", () => {
    const result = buildImportPayload(android, {
      range: { start: "2026-10-02T00:00:00+09:00", end: "2026-10-02T23:59:00+09:00" },
      consent: CONSENT,
    });

    expect(result.ok && result.payload.messages.map((message) => message.speakerKey)).toEqual(["s1", "s2"]);
  });

  it("rejects missing consent, long ranges, empty ranges, and oversized chats", () => {
    const range = { start: "2026-10-01T00:00:00+09:00", end: "2026-10-02T23:59:00+09:00" };

    expect(buildImportPayload(android, { range, consent: { ...CONSENT, isParticipant: false } })).toEqual({
      ok: false,
      error: { code: "consent_required" },
    });
    expect(
      buildImportPayload(android, {
        range: { start: "2026-01-01T00:00:00+09:00", end: "2026-10-02T00:00:00+09:00" },
        consent: CONSENT,
      }),
    ).toEqual({ ok: false, error: { code: "range_too_long", limit: 90 } });
    expect(
      buildImportPayload(android, {
        range: { start: "2026-09-01T00:00:00+09:00", end: "2026-09-02T00:00:00+09:00" },
        consent: CONSENT,
      }),
    ).toEqual({ ok: false, error: { code: "no_messages" } });

    const now = new Date("2026-10-18T12:00:00Z");
    const big = parseChat(Array.from({ length: 3_001 }, (_, index) => `민지: 메모 ${index}`).join("\n"), { now });
    const bigRange = defaultRange(big.messages)!;
    expect(buildImportPayload(big, { range: bigRange, consent: CONSENT })).toEqual({
      ok: false,
      error: { code: "too_many_messages", limit: 3_000, count: 3_001 },
    });
  });
});

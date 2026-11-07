import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { parseChat } from "@/lib/import";

const KST = { utcOffsetMinutes: 540 };

function fixture(name: string): string {
  return readFileSync(resolve(__dirname, "__fixtures__", name), "utf8");
}

const KOREAN_EXPORTS = [
  { file: "kakao-pc-ko.txt", format: "kakao_pc", formatVersion: "pc-ko-1" },
  { file: "kakao-android-ko.txt", format: "kakao_android", formatVersion: "android-ko-1" },
  { file: "kakao-ios-ko.txt", format: "kakao_ios", formatVersion: "ios-ko-1" },
] as const;

describe("parseChat with KakaoTalk text exports", () => {
  it.each(KOREAN_EXPORTS)("parses $file as $format", ({ file, format, formatVersion }) => {
    const result = parseChat(fixture(file), KST);

    expect(result.format).toBe(format);
    expect(result.formatVersion).toBe(formatVersion);
    expect(result.roomName).toBe("제주 여행");
    expect(result.unparsedLineCount).toBe(0);
    expect(result.messages.map((message) => message.speaker)).toEqual(["민지", "준호", "서연", "서연", "민지", "준호", "서연"]);
    expect(result.messages[0]).toMatchObject({ sentAt: "2026-10-01T21:00:00+09:00", body: "18일 어때?", kind: "text" });
    expect(result.messages[2].kind).toBe("media");
    expect(result.messages[3].body).toBe("숙소 후보 정리했어\n1. 애월 펜션\n2. 서귀포 호텔");
    expect(result.messages[4].sentAt).toBe("2026-10-01T23:59:00+09:00");
    expect(result.messages[5].sentAt).toBe("2026-10-02T00:05:00+09:00");
  });

  it("parses the English PC export", () => {
    const result = parseChat(fixture("kakao-pc-en.txt"), KST);

    expect(result.format).toBe("kakao_pc");
    expect(result.formatVersion).toBe("pc-en-1");
    expect(result.roomName).toBe("Jeju trip");
    expect(result.messages.map((message) => message.sentAt)).toEqual([
      "2026-10-01T21:00:00+09:00",
      "2026-10-01T21:01:00+09:00",
      "2026-10-01T21:02:00+09:00",
      "2026-10-01T23:59:00+09:00",
      "2026-10-02T00:05:00+09:00",
    ]);
    expect(result.messages[2].kind).toBe("media");
  });

  it("parses iOS exports with a 24-hour clock", () => {
    const result = parseChat(
      ["2026년 10월 1일 수요일", "2026. 10. 1. 21:00, 민지 : 18일 어때?", "2026. 10. 1. 21:01, 준호 : 콜"].join("\n"),
      KST,
    );

    expect(result.format).toBe("kakao_ios");
    expect(result.formatVersion).toBe("ios-24h-1");
    expect(result.messages[1]).toMatchObject({ speaker: "준호", sentAt: "2026-10-01T21:01:00+09:00" });
  });

  it("parses the Mac CSV export with quoted multi-line fields", () => {
    const result = parseChat(fixture("kakao-mac.csv"), KST);

    expect(result.format).toBe("kakao_csv");
    expect(result.messages).toHaveLength(4);
    expect(result.messages[1].body).toBe("ㅇㅇ 좋아, 콜");
    expect(result.messages[2].body).toBe("숙소 후보 정리했어\n1. 애월 펜션");
    expect(result.messages[3].kind).toBe("media");
  });
});

describe("parseChat fallback", () => {
  it("treats name-prefixed paste as free text with consecutive minutes", () => {
    const now = new Date("2026-10-18T12:00:30Z");
    const result = parseChat(fixture("free-text-named.txt"), { now });

    expect(result.format).toBe("free_text");
    expect(result.formatVersion).toBe("free-text-named-1");
    expect(result.messages.map((message) => message.speaker)).toEqual(["민지", "준호", "서연"]);
    expect(result.messages[2].body).toBe("숙소는 어디로 하지?\n그리고 렌트도 필요해");
    expect(result.messages.map((message) => message.sentAt)).toEqual([
      "2026-10-18T11:58:00.000Z",
      "2026-10-18T11:59:00.000Z",
      "2026-10-18T12:00:00.000Z",
    ]);
  });

  it("uses one anonymous speaker for unstructured paste", () => {
    const result = parseChat("18일 어때\n좋아\n숙소는 애월", { now: new Date("2026-10-18T12:00:00Z") });

    expect(result.formatVersion).toBe("free-text-anonymous-1");
    expect(new Set(result.messages.map((message) => message.speaker))).toEqual(new Set(["Unknown"]));
    expect(result.messages).toHaveLength(3);
  });

  it("falls back when only a few lines look like an export", () => {
    const text = ["[민지] [오후 9:00] 어때?", "random note", "another note", "third note"].join("\n");

    expect(parseChat(text, KST).format).toBe("free_text");
  });
});

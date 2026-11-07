import { describe, expect, it } from "vitest";
import { buildParseTelemetry, lineSignature } from "@/lib/import/signature";
import { parseChat } from "@/lib/import";

const SERVER_SIGNATURE = /^[DHW* .,:;\-[\]()/~!?']{1,80}$/;

describe("lineSignature", () => {
  it.each([
    ["2026년 10월 1일 오후 9:00, 민지 : 18일 어때?", "DDDDH DDH DH H D:DD, H : DDH H?"],
    ["[Minji] [9:00 PM] How about the 18th?", "[W] [D:DD W] W W W DDW?"],
    ["2026. 10. 1. 오후 9:00, 서연 : 사진 😂😂", "DDDD. DD. D. H D:DD, H : H *"],
  ])("reduces %s to its shape", (line, shape) => {
    expect(lineSignature(line)).toBe(shape);
  });

  it("produces shapes the server accepts and that carry no content", () => {
    const shapes = ["민지: 계좌 3333-01-2345678 로 보내줘", "minji.kim@example.com 010-1234-5678"].map(lineSignature);

    shapes.forEach((shape) => {
      expect(shape).toMatch(SERVER_SIGNATURE);
      expect(shape).not.toMatch(/[가-힣a-z]/);
    });
  });
});

describe("buildParseTelemetry", () => {
  it("attaches shapes only when parsing fell back", () => {
    const fallback = buildParseTelemetry("hello there\nsecond line", parseChat("hello there\nsecond line"));
    const parsed = parseChat("2026년 10월 1일 수요일\n2026년 10월 1일 오후 9:00, 민지 : 안녕", { utcOffsetMinutes: 540 });

    expect(fallback).toMatchObject({ format: "free_text", signatures: ["W W"] });
    expect(buildParseTelemetry("ignored", parsed)).toMatchObject({ format: "kakao_android", signatures: [] });
  });
});

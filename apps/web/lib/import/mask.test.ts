import { describe, expect, it } from "vitest";
import { maskSensitive } from "@/lib/import";

describe("maskSensitive", () => {
  it.each([
    ["제 번호 010-1234-5678", "제 번호 [phone]"],
    ["연락은 01012345678로", "연락은 [phone]로"],
    ["+82 10 1234 5678", "[phone]"],
    ["사무실 02-123-4567", "사무실 [phone]"],
    ["메일 minji.kim+trip@example.co.kr 로", "메일 [email] 로"],
    ["주민번호 900101-1234567", "주민번호 [rrn]"],
    ["카드 4111 1111 1111 1111", "카드 [card]"],
    ["입금은 카카오뱅크 3333-01-2345678 으로", "입금은 카카오뱅크 [account] 으로"],
    ["신한 110123456789 여기로 보내줘", "신한 [account] 여기로 보내줘"],
  ])("masks %s", (input, expected) => {
    expect(maskSensitive(input).text).toBe(expected);
  });

  it("keeps dates, times, money, and invalid card-like numbers", () => {
    const input = "2026-10-18 오후 9:30, 35,000원씩, 번호표 4111 1111 1111 1112";

    expect(maskSensitive(input)).toMatchObject({ text: input, total: 0 });
  });

  it("counts every masked value by type", () => {
    const result = maskSensitive("010-1111-2222 / 010-3333-4444 / a@b.io");

    expect(result.counts).toMatchObject({ phone: 2, email: 1 });
    expect(result.total).toBe(3);
  });

  it("leaves no digit run of ten or more next to account words", () => {
    for (let seed = 1; seed <= 50; seed++) {
      const digits = String(seed * 7_919_191_919).padStart(12, "3").slice(0, 10 + (seed % 5));
      const grouped = seed % 2 === 0 ? digits : digits.replace(/(\d{3})(?=\d)/g, "$1-");
      const masked = maskSensitive(`계좌 ${grouped} 입금 부탁`).text;

      expect(masked).not.toMatch(/\d(?:[ -]?\d){9,}/);
    }
  });
});

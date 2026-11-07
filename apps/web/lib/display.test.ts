import { describe, expect, it } from "vitest";
import { budgetLabel, dDayLabel, formatDateRange } from "@/lib/display";

describe("dDayLabel", () => {
  const now = new Date(2026, 9, 18, 21, 0);

  it("counts calendar days until the start", () => {
    expect(dDayLabel(new Date(2026, 10, 14, 9, 0).toISOString(), null, now)).toBe("D-27");
  });

  it("marks the first day, the event itself, and the end", () => {
    expect(dDayLabel(new Date(2026, 9, 18, 23, 0).toISOString(), null, now)).toBe("D-DAY");
    expect(dDayLabel(new Date(2026, 9, 17).toISOString(), new Date(2026, 9, 19).toISOString(), now)).toBe("진행 중");
    expect(dDayLabel(new Date(2026, 9, 10).toISOString(), new Date(2026, 9, 12).toISOString(), now)).toBe("끝남");
  });

  it("returns null without a start date", () => {
    expect(dDayLabel(null, null, now)).toBeNull();
  });
});

describe("formatDateRange", () => {
  it("drops the month on the end when it matches", () => {
    expect(formatDateRange(new Date(2026, 10, 14).toISOString(), new Date(2026, 10, 16).toISOString())).toBe("11.14 토 – 16 월");
  });

  it("keeps the month when the range crosses months", () => {
    expect(formatDateRange(new Date(2026, 9, 31).toISOString(), new Date(2026, 10, 2).toISOString())).toBe("10.31 토 – 11.02 월");
  });
});

describe("budgetLabel", () => {
  it("formats open and closed ranges in 만원", () => {
    expect(budgetLabel({ min: 300000, max: 500000 })).toBe("1인 30–50만원");
    expect(budgetLabel({ min: null, max: 100000 })).toBe("1인 10만원 이하");
    expect(budgetLabel({ min: 500000 })).toBe("1인 50만원 이상");
    expect(budgetLabel(null)).toBeNull();
  });
});

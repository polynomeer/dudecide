import { MONTHS, to24Hour } from "../shared";
import type { CalendarDate, LineMatch, LineRules } from "./line-engine";

// Line shapes below are reconstructed from KakaoTalk exports and must stay backed by fixtures in __fixtures__.

const KOREAN_DAY_SEPARATOR = /^(\d{4})년 (\d{1,2})월 (\d{1,2})일 \S+요일$/;

function koreanDaySeparator(line: string): CalendarDate | null {
  const match = KOREAN_DAY_SEPARATOR.exec(line.trim());
  return match ? { year: Number(match[1]), month: Number(match[2]), day: Number(match[3]) } : null;
}

const PC_SEPARATOR_KO = /^-{3,}\s*(\d{4})년 (\d{1,2})월 (\d{1,2})일 \S+\s*-{3,}$/;
const PC_SEPARATOR_EN = /^-{3,}\s*\w+, (\w+) (\d{1,2}), (\d{4})\s*-{3,}$/;
const PC_MESSAGE = /^\[(.+?)\] \[(오전|오후)? ?(\d{1,2}):(\d{2})(?: ?(AM|PM))?\] ?(.*)$/i;

/** Windows PC export: `[name] [오후 9:00] body` under `----- 2026년 10월 1일 수요일 -----` separators. */
export const kakaoPcRules: LineRules = {
  format: "kakao_pc",
  separator: (line) => {
    const korean = PC_SEPARATOR_KO.exec(line.trim());
    if (korean) {
      return { year: Number(korean[1]), month: Number(korean[2]), day: Number(korean[3]) };
    }
    const english = PC_SEPARATOR_EN.exec(line.trim());
    const month = english ? MONTHS[english[1].toLowerCase()] : undefined;
    return english && month ? { year: Number(english[3]), month, day: Number(english[2]) } : null;
  },
  message: (line, currentDate) => {
    const match = PC_MESSAGE.exec(line);
    if (!match || !currentDate) {
      return null;
    }
    const meridiem = match[2] ?? match[5];
    return {
      at: { ...currentDate, hour: to24Hour(Number(match[3]), meridiem), minute: Number(match[4]) },
      speaker: match[1],
      body: match[6],
      variant: match[2] ? "pc-ko-1" : "pc-en-1",
    };
  },
};

const ANDROID_PREFIX = /^(\d{4})년 (\d{1,2})월 (\d{1,2})일 (오전|오후) (\d{1,2}):(\d{2}), (.+)$/;

/** Android export: `2026년 10월 1일 오후 9:00, name : body`. */
export const kakaoAndroidRules: LineRules = {
  format: "kakao_android",
  separator: koreanDaySeparator,
  message: (line) => {
    const match = ANDROID_PREFIX.exec(line);
    if (!match) {
      return null;
    }
    return speakerLine(
      {
        year: Number(match[1]),
        month: Number(match[2]),
        day: Number(match[3]),
        hour: to24Hour(Number(match[5]), match[4]),
        minute: Number(match[6]),
      },
      match[7],
      "android-ko-1",
    );
  },
};

const IOS_PREFIX = /^(\d{4})\. (\d{1,2})\. (\d{1,2})\. (?:(오전|오후) )?(\d{1,2}):(\d{2})(?: (AM|PM))?, (.+)$/i;

/** iOS export: `2026. 10. 1. 오후 9:00, name : body` (12- or 24-hour clock). */
export const kakaoIosRules: LineRules = {
  format: "kakao_ios",
  separator: koreanDaySeparator,
  message: (line) => {
    const match = IOS_PREFIX.exec(line);
    if (!match) {
      return null;
    }
    const meridiem = match[4] ?? match[7];
    return speakerLine(
      {
        year: Number(match[1]),
        month: Number(match[2]),
        day: Number(match[3]),
        hour: to24Hour(Number(match[5]), meridiem),
        minute: Number(match[6]),
      },
      match[8],
      meridiem ? "ios-ko-1" : "ios-24h-1",
    );
  },
};

// `name : body`; a timestamped line without the separator is a system line (join, leave, invite).
function speakerLine(at: LineMatch["at"], rest: string, variant: string): LineMatch {
  const separatorIndex = rest.indexOf(" : ");
  if (separatorIndex <= 0) {
    return { at, speaker: null, body: rest, variant };
  }
  return { at, speaker: rest.slice(0, separatorIndex), body: rest.slice(separatorIndex + 3), variant };
}

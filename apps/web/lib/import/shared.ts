import type { ParsedMessageKind, ParseOptions } from "./types";

export type LocalDateTime = {
  year: number;
  month: number;
  day: number;
  hour: number;
  minute: number;
  second?: number;
};

/** Converts a 12-hour clock reading with a Korean or English meridiem to 24-hour time. */
export function to24Hour(hour: number, meridiem: string | undefined): number {
  if (!meridiem) {
    return hour;
  }
  const afternoon = meridiem === "오후" || meridiem.toUpperCase() === "PM";
  if (afternoon) {
    return hour % 12 + 12;
  }
  return hour % 12;
}

/** Formats a wall-clock time from the export as ISO 8601 with the exporting device's offset. */
export function toIso(value: LocalDateTime, options: ParseOptions = {}): string {
  const second = value.second ?? 0;
  const offset =
    options.utcOffsetMinutes ??
    -new Date(value.year, value.month - 1, value.day, value.hour, value.minute, second).getTimezoneOffset();
  const sign = offset >= 0 ? "+" : "-";
  const absolute = Math.abs(offset);
  return (
    `${value.year}-${pad(value.month)}-${pad(value.day)}T${pad(value.hour)}:${pad(value.minute)}:${pad(second)}` +
    `${sign}${pad(Math.floor(absolute / 60))}:${pad(absolute % 60)}`
  );
}

export const MONTHS: Record<string, number> = {
  january: 1,
  february: 2,
  march: 3,
  april: 4,
  may: 5,
  june: 6,
  july: 7,
  august: 8,
  september: 9,
  october: 10,
  november: 11,
  december: 12,
};

const MEDIA_BODIES = new Set([
  "사진",
  "동영상",
  "이모티콘",
  "음성메시지",
  "샵검색",
  "photo",
  "photos",
  "video",
  "emoticon",
  "emoticons",
  "voice message",
]);
const MEDIA_PATTERNS = [/^사진 \d+장$/, /^파일: .+/, /^\d+ photos$/i, /^file: .+/i];

const SYSTEM_PATTERNS = [
  /님이 들어왔습니다\.?$/,
  /님이 나갔습니다\.?$/,
  /님을 초대했습니다\.?$/,
  /님을 내보냈습니다\.?$/,
  /^삭제된 메시지입니다\.?$/,
  /^메시지가 삭제되었습니다\.?$/,
  /joined this chatroom\.?$/i,
  /left this chatroom\.?$/i,
  /invited .+ to this chatroom\.?$/i,
  /^this message was deleted\.?$/i,
];

export function classifyBody(body: string): ParsedMessageKind {
  const trimmed = body.trim();
  if (SYSTEM_PATTERNS.some((pattern) => pattern.test(trimmed))) {
    return "system";
  }
  if (MEDIA_BODIES.has(trimmed.toLowerCase()) || MEDIA_PATTERNS.some((pattern) => pattern.test(trimmed))) {
    return "media";
  }
  return "text";
}

export function isSystemLine(line: string): boolean {
  return SYSTEM_PATTERNS.some((pattern) => pattern.test(line.trim()));
}

const ROOM_HEADERS = [/^(.+?) 님과 카카오톡 대화$/, /^KakaoTalk Chats with (.+)$/i];
const SAVED_AT = [/^저장한 날짜\s*:/, /^Date Saved\s*:/i];

export function matchRoomName(line: string): string | null {
  for (const pattern of ROOM_HEADERS) {
    const match = pattern.exec(line.trim());
    if (match) {
      return match[1].trim();
    }
  }
  return null;
}

export function isSavedAtLine(line: string): boolean {
  return SAVED_AT.some((pattern) => pattern.test(line.trim()));
}

export function splitLines(text: string): string[] {
  return text.replace(/^﻿/, "").split(/\r\n|\r|\n/);
}

function pad(value: number): string {
  return String(value).padStart(2, "0");
}

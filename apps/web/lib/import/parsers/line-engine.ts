import {
  classifyBody,
  isSavedAtLine,
  isSystemLine,
  matchRoomName,
  splitLines,
  toIso,
  type LocalDateTime,
} from "../shared";
import type { ImportFormat, ParsedMessage, ParseOptions, ParseResult } from "../types";

export type CalendarDate = { year: number; month: number; day: number };

export type LineMatch = {
  at: LocalDateTime;
  /** Null for timestamped system lines without a speaker. */
  speaker: string | null;
  body: string;
  variant: string;
};

export type LineRules = {
  format: ImportFormat;
  /** Date separator lines set the current day for formats whose message lines carry only a time. */
  separator?: (line: string) => CalendarDate | null;
  message: (line: string, currentDate: CalendarDate | null) => LineMatch | null;
};

export type LineParseResult = ParseResult & {
  /** Share of recognized lines among lines that were not message continuations; used for format detection. */
  matchRatio: number;
};

/**
 * Shared line parser for messenger text exports: lines that do not start a message continue the previous one.
 */
export function parseLines(text: string, rules: LineRules, options: ParseOptions = {}): LineParseResult {
  const messages: ParsedMessage[] = [];
  const variants = new Map<string, number>();
  let roomName: string | null = null;
  let currentDate: CalendarDate | null = null;
  let current: ParsedMessage | null = null;
  let recognized = 0;
  let unparsed = 0;

  for (const rawLine of splitLines(text)) {
    const line = rawLine.trimEnd();
    if (line.trim() === "") {
      if (current) {
        current.body += "\n";
      }
      continue;
    }

    const room = messages.length === 0 ? matchRoomName(line) : null;
    if (room) {
      roomName = room;
      recognized++;
      continue;
    }
    if (isSavedAtLine(line)) {
      recognized++;
      continue;
    }

    const separator = rules.separator?.(line);
    if (separator) {
      currentDate = separator;
      current = null;
      recognized++;
      continue;
    }

    const match = rules.message(line, currentDate);
    if (match) {
      recognized++;
      variants.set(match.variant, (variants.get(match.variant) ?? 0) + 1);
      currentDate = { year: match.at.year, month: match.at.month, day: match.at.day };
      if (match.speaker === null) {
        current = null;
        continue;
      }
      current = {
        index: messages.length,
        speaker: match.speaker.trim(),
        sentAt: toIso(match.at, options),
        body: match.body,
        kind: "text",
      };
      messages.push(current);
      continue;
    }

    if (isSystemLine(line)) {
      recognized++;
      current = null;
      continue;
    }
    if (current) {
      current.body += `\n${line}`;
      continue;
    }
    unparsed++;
  }

  const finalized = messages.map((message) => {
    const body = message.body.replace(/\s+$/, "");
    return { ...message, body, kind: classifyBody(body) };
  });
  const formatVersion = [...variants.entries()].sort((a, b) => b[1] - a[1])[0]?.[0] ?? `${rules.format}-unknown`;
  const considered = recognized + unparsed;

  return {
    format: rules.format,
    formatVersion,
    roomName,
    messages: finalized,
    unparsedLineCount: unparsed,
    matchRatio: finalized.length === 0 || considered === 0 ? 0 : recognized / considered,
  };
}

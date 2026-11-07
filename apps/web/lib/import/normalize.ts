import type { ChatImportSpeakerInput, CreateChatImportRequest } from "@/lib/api/client";
import { maskSensitive } from "./mask";
import { prefilterForUpload } from "./prefilter";
import type { ParsedMessage, ParseResult } from "./types";

// Mirrors ChatImportValidator on the server; the wizard narrows the range instead of failing on these.
export const IMPORT_LIMITS = {
  maxMessages: 3_000,
  maxSpeakers: 200,
  maxRangeDays: 90,
  defaultRangeDays: 30,
  maxBodyChars: 4_000,
} as const;

const DAY_MS = 24 * 60 * 60 * 1000;

export type ImportRange = { start: string; end: string };

export type BuildPayloadOptions = {
  range: ImportRange;
  /** Exported name of the uploader, mapped to their own membership. */
  selfSpeaker?: string | null;
  membershipBySpeaker?: Record<string, string>;
  consent: { isParticipant: boolean; version: string };
  meetingTitle?: string | null;
};

export type BuildPayloadError =
  | { code: "no_messages" }
  | { code: "range_too_long"; limit: number }
  | { code: "too_many_messages"; limit: number; count: number }
  | { code: "too_many_speakers"; limit: number; count: number }
  | { code: "consent_required" };

export type BuildPayloadResult =
  | { ok: true; payload: CreateChatImportRequest; maskedCount: number }
  | { ok: false; error: BuildPayloadError };

/**
 * Default import window: the last 30 days of the export, or since the previous import when that is later.
 * Returns null when the export has nothing newer than the previous import.
 */
export function defaultRange(messages: ParsedMessage[], lastImportedAt?: string | null): ImportRange | null {
  const sorted = [...messages].sort((a, b) => Date.parse(a.sentAt) - Date.parse(b.sentAt));
  const last = sorted.at(-1);
  if (!last) {
    return null;
  }
  const lastTime = Date.parse(last.sentAt);
  const since = lastImportedAt ? Date.parse(lastImportedAt) : Number.NEGATIVE_INFINITY;
  if (since > lastTime) {
    return null;
  }
  const threshold = Math.max(lastTime - IMPORT_LIMITS.defaultRangeDays * DAY_MS, since);
  const first = sorted.find((message) => Date.parse(message.sentAt) >= threshold) ?? last;
  return { start: first.sentAt, end: last.sentAt };
}

/** Builds the normalized, masked upload. The raw export never leaves the browser. */
export function buildImportPayload(parse: ParseResult, options: BuildPayloadOptions): BuildPayloadResult {
  if (!options.consent.isParticipant) {
    return { ok: false, error: { code: "consent_required" } };
  }
  const start = Date.parse(options.range.start);
  const end = Date.parse(options.range.end);
  if (end - start > IMPORT_LIMITS.maxRangeDays * DAY_MS) {
    return { ok: false, error: { code: "range_too_long", limit: IMPORT_LIMITS.maxRangeDays } };
  }

  const inRange = parse.messages.filter((message) => {
    const time = Date.parse(message.sentAt);
    return time >= start && time <= end;
  });
  const { textMessages, mediaCounts } = prefilterForUpload(inRange);
  if (textMessages.length === 0) {
    return { ok: false, error: { code: "no_messages" } };
  }
  if (textMessages.length > IMPORT_LIMITS.maxMessages) {
    return {
      ok: false,
      error: { code: "too_many_messages", limit: IMPORT_LIMITS.maxMessages, count: textMessages.length },
    };
  }

  const speakerKeys = new Map<string, string>();
  textMessages.forEach((message) => {
    if (!speakerKeys.has(message.speaker)) {
      speakerKeys.set(message.speaker, `s${speakerKeys.size + 1}`);
    }
  });
  if (speakerKeys.size > IMPORT_LIMITS.maxSpeakers) {
    return {
      ok: false,
      error: { code: "too_many_speakers", limit: IMPORT_LIMITS.maxSpeakers, count: speakerKeys.size },
    };
  }

  let maskedCount = 0;
  const messages = textMessages.map((message) => {
    const masked = maskSensitive(message.body);
    maskedCount += masked.total;
    return {
      speakerKey: speakerKeys.get(message.speaker) as string,
      sentAt: message.sentAt,
      body: truncate(masked.text),
    };
  });
  const speakers: ChatImportSpeakerInput[] = [...speakerKeys.entries()].map(([displayName, key]) => ({
    key,
    displayName,
    isSelf: displayName === options.selfSpeaker,
    membershipId: options.membershipBySpeaker?.[displayName] ?? null,
  }));

  return {
    ok: true,
    maskedCount,
    payload: {
      source: {
        type: parse.format === "free_text" ? "paste" : "kakao_export",
        format: parse.format,
        formatVersion: parse.formatVersion,
        roomName: parse.roomName,
      },
      range: options.range,
      speakers,
      messages,
      consent: options.consent,
      mediaCounts,
      stats: {
        parsedCount: parse.messages.length,
        uploadedCount: messages.length,
        maskedCount,
        unparsedLineCount: parse.unparsedLineCount,
      },
      meetingTitle: options.meetingTitle ?? null,
    },
  };
}

function truncate(body: string): string {
  if (body.length <= IMPORT_LIMITS.maxBodyChars) {
    return body;
  }
  const cut = body.slice(0, IMPORT_LIMITS.maxBodyChars);
  const last = cut.charCodeAt(cut.length - 1);
  return last >= 0xd800 && last <= 0xdbff ? cut.slice(0, -1) : cut;
}

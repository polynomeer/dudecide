import { classifyBody, splitLines } from "../shared";
import type { ParsedMessage, ParseOptions, ParseResult } from "../types";

const NAMED_LINE = /^([^:\n]{1,40}?)\s*:\s+(.+)$/;
export const ANONYMOUS_SPEAKER = "Unknown";

/**
 * Fallback for pasted text without a known export shape: `name: body` lines, or one anonymous speaker.
 * There are no timestamps, so messages get consecutive minutes ending at `now`.
 */
export function parseFreeText(text: string, options: ParseOptions = {}): ParseResult {
  const lines = splitLines(text).map((line) => line.trimEnd()).filter((line) => line.trim() !== "");
  const named = lines.filter((line) => NAMED_LINE.test(line)).length;
  const useNames = lines.length > 0 && named / lines.length >= 0.5;

  const drafts: Array<{ speaker: string; body: string }> = [];
  lines.forEach((line) => {
    const match = useNames ? NAMED_LINE.exec(line) : null;
    if (match) {
      drafts.push({ speaker: match[1].trim(), body: match[2] });
    } else if (useNames && drafts.length > 0) {
      drafts[drafts.length - 1].body += `\n${line}`;
    } else {
      drafts.push({ speaker: ANONYMOUS_SPEAKER, body: line });
    }
  });

  const now = options.now ?? new Date();
  const end = Math.floor(now.getTime() / 60_000) * 60_000;
  const messages: ParsedMessage[] = drafts.map((draft, index) => ({
    index,
    speaker: draft.speaker,
    sentAt: new Date(end - (drafts.length - 1 - index) * 60_000).toISOString(),
    body: draft.body,
    kind: classifyBody(draft.body),
  }));

  return {
    format: "free_text",
    formatVersion: useNames ? "free-text-named-1" : "free-text-anonymous-1",
    roomName: null,
    messages,
    unparsedLineCount: 0,
  };
}

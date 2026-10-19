import { parseFreeText } from "./parsers/free-text";
import { looksLikeKakaoCsv, parseKakaoCsv } from "./parsers/kakao-csv";
import { kakaoAndroidRules, kakaoIosRules, kakaoPcRules } from "./parsers/kakao";
import { parseLines, type LineParseResult } from "./parsers/line-engine";
import type { ParseOptions, ParseResult } from "./types";

const LINE_FORMATS = [kakaoPcRules, kakaoAndroidRules, kakaoIosRules];
export const MIN_MATCH_RATIO = 0.6;

/**
 * Picks the export parser that recognizes the most lines. Below the threshold the text is treated as a free paste,
 * so an unknown format degrades instead of blocking the import.
 */
export function parseChat(text: string, options: ParseOptions = {}): ParseResult {
  if (looksLikeKakaoCsv(text)) {
    return parseKakaoCsv(text, options);
  }

  const best = LINE_FORMATS.map((rules) => parseLines(text, rules, options)).reduce<LineParseResult | null>(
    (winner, candidate) => (!winner || candidate.matchRatio > winner.matchRatio ? candidate : winner),
    null,
  );

  if (best && best.matchRatio >= MIN_MATCH_RATIO) {
    return {
      format: best.format,
      formatVersion: best.formatVersion,
      roomName: best.roomName,
      messages: best.messages,
      unparsedLineCount: best.unparsedLineCount,
    };
  }
  return parseFreeText(text, options);
}

import { splitLines } from "./shared";
import type { ParseResult } from "./types";

const KEPT_PUNCTUATION = new Set([" ", ".", ",", ":", ";", "-", "[", "]", "(", ")", "/", "~", "!", "?", "'"]);
const MAX_SIGNATURE_LENGTH = 80;
const MAX_SIGNATURES = 5;

/**
 * Reduces a line to its shape: digits become D, Hangul runs H, Latin runs W, anything else *. Names and words
 * collapse to one letter, so the shape carries the export layout but no content.
 */
export function lineSignature(line: string): string {
  let shape = "";
  for (const char of line.trim()) {
    const symbol = classify(char);
    if ((symbol === "H" || symbol === "W" || symbol === "*") && shape.endsWith(symbol)) {
      continue;
    }
    shape += symbol;
  }
  return shape.replace(/ {2,}/g, " ").slice(0, MAX_SIGNATURE_LENGTH).trim();
}

export type ParseTelemetry = {
  format: ParseResult["format"];
  formatVersion: string;
  parsedCount: number;
  unparsedLineCount: number;
  signatures: string[];
};

/** Telemetry for one parse. Line shapes are attached only when parsing fell back or missed lines. */
export function buildParseTelemetry(text: string, result: ParseResult): ParseTelemetry {
  const needsShapes = result.format === "free_text" || result.unparsedLineCount > 0;
  const signatures = needsShapes
    ? [
        ...new Set(
          splitLines(text)
            .filter((line) => line.trim() !== "")
            .slice(0, 20)
            .map(lineSignature)
            .filter((shape) => shape !== ""),
        ),
      ].slice(0, MAX_SIGNATURES)
    : [];
  return {
    format: result.format,
    formatVersion: result.formatVersion,
    parsedCount: result.messages.length,
    unparsedLineCount: result.unparsedLineCount,
    signatures,
  };
}

function classify(char: string): string {
  if (/\d/.test(char)) return "D";
  if (/[가-힣ㄱ-ㆎ]/.test(char)) return "H";
  if (/[A-Za-z]/.test(char)) return "W";
  if (/\s/.test(char)) return " ";
  if (KEPT_PUNCTUATION.has(char)) return char;
  return "*";
}

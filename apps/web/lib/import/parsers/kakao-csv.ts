import { classifyBody, toIso } from "../shared";
import type { ParsedMessage, ParseOptions, ParseResult } from "../types";

const HEADER = /^﻿?"?Date"?,"?User"?,"?Message"?\s*$/i;
const DATE_TIME = /^(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2})(?::(\d{2}))?$/;

export function looksLikeKakaoCsv(text: string): boolean {
  const firstLine = text.split(/\r\n|\r|\n/, 1)[0] ?? "";
  return HEADER.test(firstLine);
}

/** Mac export: `Date,User,Message` CSV with quoted, possibly multi-line fields. */
export function parseKakaoCsv(text: string, options: ParseOptions = {}): ParseResult {
  const rows = readCsv(text.replace(/^﻿/, ""));
  const messages: ParsedMessage[] = [];
  let unparsed = 0;

  rows.slice(1).forEach((row) => {
    if (row.length === 1 && row[0].trim() === "") {
      return;
    }
    const match = row.length >= 3 ? DATE_TIME.exec(row[0].trim()) : null;
    if (!match) {
      unparsed++;
      return;
    }
    const body = row.slice(2).join(",").replace(/\s+$/, "");
    messages.push({
      index: messages.length,
      speaker: row[1].trim(),
      sentAt: toIso(
        {
          year: Number(match[1]),
          month: Number(match[2]),
          day: Number(match[3]),
          hour: Number(match[4]),
          minute: Number(match[5]),
          second: match[6] ? Number(match[6]) : 0,
        },
        options,
      ),
      body,
      kind: classifyBody(body),
    });
  });

  return {
    format: "kakao_csv",
    formatVersion: "csv-1",
    roomName: null,
    messages,
    unparsedLineCount: unparsed,
  };
}

function readCsv(text: string): string[][] {
  const rows: string[][] = [];
  let row: string[] = [];
  let field = "";
  let quoted = false;

  for (let index = 0; index < text.length; index++) {
    const char = text[index];
    if (quoted) {
      if (char === '"' && text[index + 1] === '"') {
        field += '"';
        index++;
      } else if (char === '"') {
        quoted = false;
      } else {
        field += char;
      }
      continue;
    }
    if (char === '"') {
      quoted = true;
    } else if (char === ",") {
      row.push(field);
      field = "";
    } else if (char === "\n" || char === "\r") {
      if (char === "\r" && text[index + 1] === "\n") {
        index++;
      }
      row.push(field);
      rows.push(row);
      row = [];
      field = "";
    } else {
      field += char;
    }
  }
  if (field !== "" || row.length > 0) {
    row.push(field);
    rows.push(row);
  }
  return rows;
}

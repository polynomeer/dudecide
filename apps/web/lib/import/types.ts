export type ImportFormat = "kakao_pc" | "kakao_android" | "kakao_ios" | "kakao_csv" | "free_text";

export type ParsedMessageKind = "text" | "media" | "system";

export type ParsedMessage = {
  /** Order in the source. */
  index: number;
  /** Display name as exported. */
  speaker: string;
  /** ISO 8601 with offset; minute precision is normal for exports. */
  sentAt: string;
  body: string;
  kind: ParsedMessageKind;
};

export type ParseResult = {
  format: ImportFormat;
  /** Parser-internal pattern id, sent as telemetry. */
  formatVersion: string;
  roomName: string | null;
  messages: ParsedMessage[];
  unparsedLineCount: number;
};

export type ParseOptions = {
  /** Offset of the exporting device from UTC, in minutes. Defaults to the browser offset at each message's date. */
  utcOffsetMinutes?: number;
  /** Reference time for free text without timestamps. Defaults to now. */
  now?: Date;
};

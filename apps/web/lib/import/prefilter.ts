import type { ParsedMessage } from "./types";

export type ClientPrefilterResult = {
  textMessages: ParsedMessage[];
  /** Media placeholders are uploaded only as counts per export date. */
  mediaCounts: Array<{ date: string; count: number }>;
  systemCount: number;
};

/** Drops system lines and media placeholders before upload; the server prefilters chatter for the LLM. */
export function prefilterForUpload(messages: ParsedMessage[]): ClientPrefilterResult {
  const media = new Map<string, number>();
  let systemCount = 0;
  const textMessages = messages.filter((message) => {
    if (message.kind === "system") {
      systemCount++;
      return false;
    }
    if (message.kind === "media") {
      const date = message.sentAt.slice(0, 10);
      media.set(date, (media.get(date) ?? 0) + 1);
      return false;
    }
    return true;
  });

  return {
    textMessages,
    mediaCounts: [...media.entries()].sort(([a], [b]) => a.localeCompare(b)).map(([date, count]) => ({ date, count })),
    systemCount,
  };
}

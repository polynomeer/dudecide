"use client";

import { useMutation, useQuery } from "@tanstack/react-query";
import type { Route } from "next";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useRef, useState } from "react";
import { getApiErrorMessage } from "@/lib/api/error-display";
import { ApiError, apiClient } from "@/lib/api/client";
import {
  buildImportPayload,
  defaultRange,
  IMPORT_LIMITS,
  parseChat,
  type ImportRange,
  type ParsedMessage,
  type ParseResult,
} from "@/lib/import";
import { buildParseTelemetry } from "@/lib/import/signature";
import {
  buildErrorMessage,
  CONSENT_VERSION,
  EXPORT_GUIDES,
  FORMAT_LABELS,
  formatDateTime,
} from "@/components/import/import-copy";

const MAX_FILE_BYTES = 20 * 1024 * 1024;
const DAY_MS = 24 * 60 * 60 * 1000;
const MASK_TOKEN = /\[(phone|account|card|rrn|email)\]/;
const TELEMETRY_DELAY_MS = 1500;

type RangePreset = "default" | "week" | "all" | "custom";

/**
 * Chat import wizard. The raw export stays in this component's state: only the masked, range-limited payload
 * built by lib/import is sent (ADR 0005).
 */
export function ImportWizard({ meetingId }: { meetingId?: string }) {
  const router = useRouter();
  const [rawText, setRawText] = useState("");
  const [parse, setParse] = useState<ParseResult | null>(null);
  const [inputError, setInputError] = useState<string | null>(null);
  const [preset, setPreset] = useState<RangePreset>("default");
  const [custom, setCustom] = useState({ start: "", end: "" });
  const [selfSpeaker, setSelfSpeaker] = useState<string | null>(null);
  const [consented, setConsented] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const lastTelemetryKey = useRef<string | null>(null);

  // Anonymous parse telemetry (format, counts, line shapes) once the input settles; never message content.
  useEffect(() => {
    if (!parse) {
      return;
    }
    const timer = window.setTimeout(() => {
      const telemetry = buildParseTelemetry(rawText, parse);
      const key = JSON.stringify(telemetry);
      if (key === lastTelemetryKey.current) {
        return;
      }
      lastTelemetryKey.current = key;
      apiClient.reportImportParse(telemetry).catch(() => undefined);
    }, TELEMETRY_DELAY_MS);
    return () => window.clearTimeout(timer);
  }, [parse, rawText]);

  const importsQuery = useQuery({
    queryKey: ["meeting-imports", meetingId],
    queryFn: () => apiClient.listChatImports(meetingId!),
    enabled: Boolean(meetingId),
  });
  const lastImportedAt = useMemo(() => {
    const ends = (importsQuery.data ?? [])
      .filter((item) => item.status !== "deleted" && item.sourceRoomName === (parse?.roomName ?? null))
      .map((item) => item.rangeEnd)
      .sort();
    return ends.at(-1) ?? null;
  }, [importsQuery.data, parse?.roomName]);

  const range = useMemo(
    () => (parse ? computeRange(parse.messages, preset, custom, lastImportedAt) : null),
    [parse, preset, custom, lastImportedAt],
  );
  const preview = useMemo(
    () =>
      parse && range
        ? buildImportPayload(parse, {
            range,
            selfSpeaker,
            consent: { isParticipant: true, version: CONSENT_VERSION },
          })
        : null,
    [parse, range, selfSpeaker],
  );
  const idempotencyKey = useMemo(() => (preview?.ok ? crypto.randomUUID() : null), [preview]);

  const submitMutation = useMutation({
    mutationFn: async () => {
      if (!parse || !range) {
        throw new Error("no import prepared");
      }
      const built = buildImportPayload(parse, {
        range,
        selfSpeaker,
        consent: { isParticipant: consented, version: CONSENT_VERSION },
      });
      if (!built.ok) {
        throw new BuildError(buildErrorMessage(built.error));
      }
      return meetingId
        ? apiClient.createMeetingChatImport(meetingId, built.payload, idempotencyKey ?? undefined)
        : apiClient.createChatImport(built.payload, idempotencyKey ?? undefined);
    },
    onSuccess: (created) => {
      setSubmitError(null);
      router.push(`/meetings/${created.meetingId}/import/${created.importId}` as Route);
    },
    onError: (error: unknown) => {
      setSubmitError(
        error instanceof BuildError
          ? error.message
          : error instanceof ApiError && error.status === 429
            ? "오늘 가져올 수 있는 횟수를 모두 썼어요. 내일 다시 시도하거나, 기간을 넓혀 한 번에 가져와 주세요."
            : error instanceof ApiError && error.status === 413
              ? "보낼 내용이 너무 큽니다. 기간을 줄여 주세요."
              : getApiErrorMessage(error, {
              resourceName: "대화 가져오기",
              conflictMessage: "이미 가져온 메시지뿐입니다. 새 대화가 생긴 뒤 다시 가져와 주세요.",
            }),
      );
    },
  });

  const loadText = (text: string) => {
    setRawText(text);
    setSelfSpeaker(null);
    setSubmitError(null);
    if (text.trim() === "") {
      setParse(null);
      setInputError(null);
      return;
    }
    const result = parseChat(text);
    const textMessages = result.messages.filter((message) => message.kind === "text");
    if (textMessages.length === 0) {
      setParse(null);
      setInputError("읽을 수 있는 메시지를 찾지 못했습니다. 내보낸 파일 그대로 올려 주세요.");
      return;
    }
    setInputError(null);
    setParse(result);
  };

  const onFile = async (file: File | undefined) => {
    if (!file) {
      return;
    }
    if (file.size > MAX_FILE_BYTES) {
      setInputError("파일이 너무 큽니다. 20MB 이하의 텍스트 내보내기 파일을 올려 주세요.");
      return;
    }
    loadText(await file.text());
  };

  const speakersInRange = useMemo(() => {
    if (!parse || !range) {
      return [];
    }
    const counts = new Map<string, number>();
    inRange(parse.messages, range)
      .filter((message) => message.kind === "text")
      .forEach((message) => counts.set(message.speaker, (counts.get(message.speaker) ?? 0) + 1));
    return [...counts.entries()];
  }, [parse, range]);

  const maskedSamples = preview?.ok
    ? preview.payload.messages.filter((message) => MASK_TOKEN.test(message.body)).slice(0, 5)
    : [];

  return (
    <div className="grid gap-5 xl:grid-cols-[1.1fr_0.9fr]">
      <section className="panel space-y-6 px-6 py-6">
        <div>
          <div className="info-chip">1. 대화 넣기</div>
          <h2 className="mt-4 text-2xl font-semibold tracking-[-0.03em]">카카오톡 대화를 올리거나 붙여넣으세요</h2>
          <p className="section-copy mt-2">
            파일 원본은 이 브라우저 밖으로 나가지 않습니다. 고른 기간의 메시지만, 전화번호·계좌번호 등을 가린 뒤 보냅니다.
            형식 인식을 개선하기 위해 줄 모양(숫자·글자 종류)과 개수만 익명으로 보냅니다.
          </p>
        </div>
        <label className="block">
          <span className="mb-2 block text-sm font-semibold">내보내기 파일 (.txt, .csv)</span>
          <input
            type="file"
            accept=".txt,.csv,text/plain,text/csv"
            aria-label="내보내기 파일"
            onChange={(event) => void onFile(event.target.files?.[0])}
            className="field"
          />
        </label>
        <label className="block">
          <span className="mb-2 block text-sm font-semibold">또는 대화 붙여넣기</span>
          <textarea
            aria-label="대화 붙여넣기"
            value={rawText}
            onChange={(event) => loadText(event.target.value)}
            rows={8}
            placeholder={"민지: 18일 어때?\n준호: ㅇㅇ 좋아"}
            className="field font-mono text-sm"
          />
        </label>
        {inputError ? (
          <p role="alert" className="text-sm text-[color:var(--color-danger,#b42318)]">
            {inputError}
          </p>
        ) : null}

        {parse && range ? (
          <>
            <div className="section-rule" />
            <div>
              <div className="info-chip">2. 기간</div>
              <div className="mt-3 flex flex-wrap gap-2">
                {(
                  [
                    ["default", lastImportedAt ? "지난번 이후" : "최근 30일"],
                    ["week", "최근 7일"],
                    ["all", "전체"],
                    ["custom", "직접 고르기"],
                  ] as const
                ).map(([value, label]) => (
                  <button
                    key={value}
                    type="button"
                    aria-pressed={preset === value}
                    onClick={() => setPreset(value)}
                    className={preset === value ? "action-primary" : "action-secondary"}
                  >
                    {label}
                  </button>
                ))}
              </div>
              {preset === "custom" ? (
                <div className="mt-3 grid gap-3 sm:grid-cols-2">
                  <label className="block text-sm">
                    시작일
                    <input
                      type="date"
                      aria-label="시작일"
                      value={custom.start}
                      onChange={(event) => setCustom((current) => ({ ...current, start: event.target.value }))}
                      className="field mt-1"
                    />
                  </label>
                  <label className="block text-sm">
                    종료일
                    <input
                      type="date"
                      aria-label="종료일"
                      value={custom.end}
                      onChange={(event) => setCustom((current) => ({ ...current, end: event.target.value }))}
                      className="field mt-1"
                    />
                  </label>
                </div>
              ) : null}
              <p className="section-copy mt-3">
                {formatDateTime(range.start)} ~ {formatDateTime(range.end)} · 최대 {IMPORT_LIMITS.maxRangeDays}일,{" "}
                {IMPORT_LIMITS.maxMessages.toLocaleString()}개 메시지
              </p>
            </div>

            <div>
              <div className="info-chip">3. 나는 누구인가요?</div>
              <fieldset className="mt-3 grid gap-2 sm:grid-cols-2">
                <legend className="sr-only">대화 속 내 이름</legend>
                {speakersInRange.map(([speaker, count]) => (
                  <label key={speaker} className="soft-card flex items-center gap-3 px-4 py-3 text-sm">
                    <input
                      type="radio"
                      name="self-speaker"
                      checked={selfSpeaker === speaker}
                      onChange={() => setSelfSpeaker(speaker)}
                    />
                    <span className="font-semibold">{speaker}</span>
                    <span className="ml-auto text-ink/50">{count}개</span>
                  </label>
                ))}
                <label className="soft-card flex items-center gap-3 px-4 py-3 text-sm">
                  <input
                    type="radio"
                    name="self-speaker"
                    checked={selfSpeaker === null}
                    onChange={() => setSelfSpeaker(null)}
                  />
                  <span>목록에 없음</span>
                </label>
              </fieldset>
            </div>
          </>
        ) : null}
      </section>

      <aside className="space-y-5">
        {parse ? (
          <section className="panel px-6 py-6" aria-label="가져오기 미리보기">
            <div className="info-chip">미리보기</div>
            <dl className="mt-4 grid grid-cols-2 gap-3 text-sm">
              <div className="metric-card px-4 py-3">
                <dt className="text-ink/50">형식</dt>
                <dd className="font-semibold">{FORMAT_LABELS[parse.format]}</dd>
              </div>
              <div className="metric-card px-4 py-3">
                <dt className="text-ink/50">채팅방</dt>
                <dd className="font-semibold">{parse.roomName ?? "알 수 없음"}</dd>
              </div>
              <div className="metric-card px-4 py-3">
                <dt className="text-ink/50">보낼 메시지</dt>
                <dd className="font-semibold">{preview?.ok ? preview.payload.messages.length.toLocaleString() : 0}개</dd>
              </div>
              <div className="metric-card px-4 py-3">
                <dt className="text-ink/50">가린 개인정보</dt>
                <dd className="font-semibold">{preview?.ok ? preview.maskedCount : 0}건</dd>
              </div>
            </dl>
            {parse.format === "free_text" ? (
              <p className="section-copy mt-3">
                붙여넣은 텍스트에는 시간이 없어 붙여넣은 시각 기준으로 1분 간격의 시간을 붙입니다.
              </p>
            ) : null}
            {parse.unparsedLineCount > 0 ? (
              <p className="section-copy mt-3">읽지 못한 줄 {parse.unparsedLineCount}개는 제외했습니다.</p>
            ) : null}
            {preview && !preview.ok ? (
              <p role="alert" className="mt-3 text-sm font-semibold text-[color:var(--color-danger,#b42318)]">
                {buildErrorMessage(preview.error)}
              </p>
            ) : null}
            {maskedSamples.length > 0 ? (
              <div className="mt-4">
                <h3 className="text-sm font-semibold">가려진 내용 예시</h3>
                <ul className="mono-panel mt-2 space-y-1 px-4 py-3 text-xs">
                  {maskedSamples.map((message) => (
                    <li key={`${message.sentAt}-${message.body}`}>{message.body}</li>
                  ))}
                </ul>
              </div>
            ) : null}
          </section>
        ) : (
          <section className="panel px-6 py-6">
            <div className="info-chip">내보내는 방법</div>
            <ul className="mt-4 space-y-3 text-sm">
              {EXPORT_GUIDES.map((guide) => (
                <li key={guide.id}>
                  <span className="font-semibold">{guide.label}</span>
                  <span className="text-ink/60"> · {guide.steps.join(" → ")}</span>
                </li>
              ))}
            </ul>
          </section>
        )}

        {parse ? (
          <section className="panel px-6 py-6">
            <div className="info-chip">4. 확인</div>
            <label className="mt-4 flex items-start gap-3 text-sm">
              <input type="checkbox" checked={consented} onChange={(event) => setConsented(event.target.checked)} />
              <span>
                이 대화의 참여자이며, 대화 내용을 이 모임을 정리하는 데에만 사용하는 것에 동의합니다. 다른 참여자의
                메시지가 포함되므로 언제든 가져온 대화를 삭제할 수 있습니다.
              </span>
            </label>
            {submitError ? (
              <p role="alert" className="mt-3 text-sm text-[color:var(--color-danger,#b42318)]">
                {submitError}
              </p>
            ) : null}
            <button
              type="button"
              onClick={() => submitMutation.mutate()}
              disabled={!consented || !preview?.ok || submitMutation.isPending}
              className="action-primary mt-4 w-full disabled:cursor-not-allowed disabled:bg-[rgba(13,21,38,0.4)]"
            >
              {submitMutation.isPending ? "보내는 중..." : "분석 시작"}
            </button>
          </section>
        ) : null}
      </aside>
    </div>
  );
}

class BuildError extends Error {}

function inRange(messages: ParsedMessage[], range: ImportRange): ParsedMessage[] {
  const start = Date.parse(range.start);
  const end = Date.parse(range.end);
  return messages.filter((message) => {
    const time = Date.parse(message.sentAt);
    return time >= start && time <= end;
  });
}

function computeRange(
  messages: ParsedMessage[],
  preset: RangePreset,
  custom: { start: string; end: string },
  lastImportedAt: string | null,
): ImportRange | null {
  const sorted = [...messages].sort((a, b) => Date.parse(a.sentAt) - Date.parse(b.sentAt));
  const first = sorted[0];
  const last = sorted.at(-1);
  if (!first || !last) {
    return null;
  }
  switch (preset) {
    case "default":
      return defaultRange(messages, lastImportedAt) ?? { start: last.sentAt, end: last.sentAt };
    case "week": {
      const threshold = Date.parse(last.sentAt) - 7 * DAY_MS;
      const start = sorted.find((message) => Date.parse(message.sentAt) >= threshold) ?? last;
      return { start: start.sentAt, end: last.sentAt };
    }
    case "all":
      return { start: first.sentAt, end: last.sentAt };
    case "custom": {
      if (!custom.start || !custom.end) {
        return defaultRange(messages, lastImportedAt);
      }
      const start = new Date(`${custom.start}T00:00:00`);
      const end = new Date(`${custom.end}T23:59:59`);
      return { start: start.toISOString(), end: end.toISOString() };
    }
  }
}

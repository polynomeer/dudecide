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
import { ImportSteps } from "@/components/import/import-steps";
import { avatarColor, initialOf } from "@/lib/display";

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

  const textCount = parse ? parse.messages.filter((message) => message.kind === "text").length : 0;
  const rangeDays = range ? Math.max(1, Math.round((Date.parse(range.end) - Date.parse(range.start)) / DAY_MS) + 1) : 0;
  const step = parse ? 2 : 1;


  return (
    <div className="flex flex-col gap-5">
      <ImportSteps current={step} />

      <div className="flex flex-wrap items-start gap-5">
        <div className="flex min-w-0 flex-[999_1_600px] flex-col gap-4">
          <h1 className="font-display m-0 text-[clamp(1.7rem,2.6vw,2.2rem)]">
            {parse ? "이 대화가 맞는지 확인해 주세요" : "단톡방 대화를 가져와요"}
          </h1>

          {parse ? null : (
            <p className="section-copy m-0">
              파일 원본은 이 브라우저 밖으로 나가지 않습니다. 고른 기간의 메시지만, 전화번호·계좌번호 등을 가린 뒤 보냅니다.
              형식 인식을 개선하기 위해 줄 모양(숫자·글자 종류)과 개수만 익명으로 보냅니다.
              {meetingId ? " 같은 채팅방은 지난번 이후의 대화만 기본으로 고르고, 이미 가져온 메시지는 건너뜁니다." : null}
            </p>
          )}

          <details open={!parse} className="panel group p-5 [&:not([open])]:p-0">
            <summary className={parse ? "cursor-pointer list-none px-5 py-3 text-sm font-semibold text-accent-text" : "hidden"}>
              다른 파일로 바꾸거나 붙여넣은 내용 고치기
            </summary>
            <div className={`flex flex-col gap-4 ${parse ? "px-5 pb-5" : ""}`}>
              <label className="flex min-h-[120px] cursor-pointer flex-col items-center justify-center gap-1.5 rounded-2xl border-[1.5px] border-dashed border-accent/50 bg-accent-soft/40 px-4 py-6 text-center">
                <span className="text-[15px] font-bold text-accent-text">내보내기 파일 고르기 (.txt, .csv)</span>
                <span className="text-xs text-muted">카톡 ‘대화 내보내기’로 저장한 파일 · 이 기기에서만 읽어요</span>
                <input
                  type="file"
                  accept=".txt,.csv,text/plain,text/csv"
                  aria-label="내보내기 파일"
                  onChange={(event) => void onFile(event.target.files?.[0])}
                  className="sr-only"
                />
              </label>
              <label className="block">
                <span className="mb-2 block text-sm font-semibold">또는 대화 붙여넣기</span>
                <textarea
                  aria-label="대화 붙여넣기"
                  value={rawText}
                  onChange={(event) => loadText(event.target.value)}
                  rows={parse ? 4 : 8}
                  placeholder={"민지: 18일 어때?\n준호: ㅇㅇ 좋아"}
                  className="field num text-sm"
                />
              </label>
            </div>
          </details>

          {inputError ? (
            <p role="alert" className="m-0 rounded-xl bg-risk-soft px-4 py-3 text-sm text-risk-ink">
              {inputError}
            </p>
          ) : null}

          {parse && range ? (
            <section aria-label="가져오기 미리보기" className="flex flex-col gap-4">
              <div className="panel flex flex-wrap items-center gap-x-7 gap-y-4 p-5">
                <div className="flex min-w-0 flex-[1_1_260px] items-center gap-3">
                  <span className="flex h-11 w-11 flex-none items-center justify-center rounded-xl bg-accent-soft text-accent-soft-ink">
                    <svg aria-hidden="true" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z" />
                    </svg>
                  </span>
                  <span className="min-w-0">
                    <b className="block text-base">{parse.roomName ?? "알 수 없음"}</b>
                    <span className="num block truncate text-xs text-muted">{FORMAT_LABELS[parse.format]} · 이 기기에서 읽음</span>
                  </span>
                </div>
                <dl className="m-0 flex flex-wrap gap-7">
                  <SummaryStat label="보낼 메시지" value={`${preview?.ok ? preview.payload.messages.length.toLocaleString() : 0}개`} />
                  <SummaryStat label="참여자" value={`${speakersInRange.length}명`} />
                  <SummaryStat label={`전체 ${textCount.toLocaleString()}개 중`} value={`${rangeDays}일`} />
                </dl>
              </div>

              {parse.format === "free_text" ? (
                <p className="section-copy m-0">붙여넣은 텍스트에는 시간이 없어 붙여넣은 시각 기준으로 1분 간격의 시간을 붙입니다.</p>
              ) : null}
              {parse.unparsedLineCount > 0 ? (
                <p className="section-copy m-0">읽지 못한 줄 {parse.unparsedLineCount}개는 제외했습니다.</p>
              ) : null}

              <div className="flex flex-wrap items-stretch gap-4">
                <section aria-label="가져올 기간" className="panel flex flex-[1_1_300px] flex-col gap-3 p-5">
                  <div className="flex items-baseline justify-between">
                    <h2 className="m-0 text-base font-bold">가져올 기간</h2>
                    <span className="num text-[13px] font-semibold text-accent-text">
                      {preview?.ok ? preview.payload.messages.length.toLocaleString() : 0}개 분석
                    </span>
                  </div>
                  <div className="grid grid-cols-2 gap-1.5 sm:grid-cols-4">
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
                        className={`min-h-[44px] rounded-[10px] px-2 text-[13px] font-bold transition ${
                          preset === value ? "bg-ink text-on-ink" : "border border-line bg-surface text-ink hover:bg-surface-2"
                        }`}
                      >
                        {label}
                      </button>
                    ))}
                  </div>
                  {preset === "custom" ? (
                    <div className="grid gap-2 sm:grid-cols-2">
                      <label className="block text-xs font-semibold text-muted">
                        시작일
                        <input
                          type="date"
                          aria-label="시작일"
                          value={custom.start}
                          onChange={(event) => setCustom((current) => ({ ...current, start: event.target.value }))}
                          className="field mt-1"
                        />
                      </label>
                      <label className="block text-xs font-semibold text-muted">
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
                  <p className="m-0 text-xs leading-normal text-muted">
                    {formatDateTime(range.start)} ~ {formatDateTime(range.end)} · 최대 {IMPORT_LIMITS.maxRangeDays}일,{" "}
                    {IMPORT_LIMITS.maxMessages.toLocaleString()}개 메시지. 기간이 짧을수록 빠르고, 덜 공유돼요.
                  </p>
                </section>

                <section aria-label="가린 정보" className="panel flex flex-[1_1_300px] flex-col gap-2.5 p-5">
                  <div className="flex items-baseline justify-between">
                    <h2 className="m-0 text-base font-bold">보내기 전에 가린 정보</h2>
                    <span className="num text-xs text-muted">{preview?.ok ? preview.maskedCount : 0}건</span>
                  </div>
                  {maskedSamples.length > 0 ? (
                    <ul className="m-0 list-none rounded-xl bg-surface-2 px-3 py-2.5 text-[13px] leading-[1.7]">
                      {maskedSamples.map((message) => (
                        <li key={`${message.sentAt}-${message.body}`}>{message.body}</li>
                      ))}
                    </ul>
                  ) : (
                    <p className="section-copy m-0">가릴 전화번호·계좌번호·이메일을 찾지 못했어요.</p>
                  )}
                </section>
              </div>

              {preview && !preview.ok ? (
                <p role="alert" className="m-0 rounded-xl bg-risk-soft px-4 py-3 text-sm font-semibold text-risk-ink">
                  {buildErrorMessage(preview.error)}
                </p>
              ) : null}

              <section aria-label="참여자" className="panel flex flex-col gap-3 p-5">
                <div className="flex flex-wrap items-baseline justify-between gap-1.5">
                  <h2 className="m-0 text-base font-bold">이 중에 누가 나예요?</h2>
                  <span className="text-xs text-muted">다른 사람은 참여할 때 직접 골라요 · AI에는 ‘참여자 A, B…’로 보내요</span>
                </div>
                <fieldset className="m-0 grid grid-cols-[repeat(auto-fill,minmax(170px,1fr))] gap-2 border-0 p-0">
                  <legend className="sr-only">대화 속 내 이름</legend>
                  {speakersInRange.map(([speaker, count]) => (
                    <SpeakerOption
                      key={speaker}
                      name={speaker}
                      meta={`${count}개`}
                      checked={selfSpeaker === speaker}
                      onChange={() => setSelfSpeaker(speaker)}
                    />
                  ))}
                  <SpeakerOption name="목록에 없음" meta="나중에 연결" checked={selfSpeaker === null} onChange={() => setSelfSpeaker(null)} />
                </fieldset>
              </section>

              <div className="flex flex-wrap items-center justify-between gap-3.5 py-1">
                <label className="flex flex-[1_1_380px] cursor-pointer items-start gap-2.5 text-sm leading-normal">
                  <input
                    type="checkbox"
                    checked={consented}
                    onChange={(event) => setConsented(event.target.checked)}
                    className="mt-0.5 h-5 w-5 flex-none accent-[rgb(var(--accent))]"
                  />
                  <span>
                    이 대화의 참여자이며, 대화 내용을 이 모임을 정리하는 데에만 사용하는 것에 동의합니다. 다른 참여자의
                    메시지가 포함되므로 언제든 가져온 대화를 삭제할 수 있습니다.
                  </span>
                </label>
                <button
                  type="button"
                  onClick={() => submitMutation.mutate()}
                  disabled={!consented || !preview?.ok || submitMutation.isPending}
                  className="btn-accent min-h-[52px] px-[26px] text-base"
                >
                  {submitMutation.isPending ? "보내는 중..." : "분석 시작"}
                </button>
              </div>
              {submitError ? (
                <p role="alert" className="m-0 text-sm text-danger">
                  {submitError}
                </p>
              ) : null}
            </section>
          ) : null}
        </div>

        <aside className="flex max-w-full flex-[1_1_300px] flex-col gap-4 lg:max-w-[380px]">
          <ExportGuide />
          <section aria-label="데이터 처리 방식" className="flex flex-col gap-3 rounded-[20px] bg-band px-6 py-[22px] text-white">
            <h2 className="m-0 text-base font-bold">이렇게 다뤄요</h2>
            {[
              "원본 파일은 보내지 않아요. 고른 기간만 보내요.",
              "AI에게는 이름 대신 가명으로 보내고, 학습에는 쓰지 않아요.",
              "모임으로 만들지 않으면 7일 뒤 자동으로 지워져요.",
            ].map((line) => (
              <div key={line} className="flex gap-2.5 text-sm leading-normal text-[#C9CCD3]">
                <span aria-hidden="true" className="text-[#9AA3FF]">✓</span>
                <span>{line}</span>
              </div>
            ))}
          </section>
        </aside>
      </div>
    </div>
  );
}

function SummaryStat({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex flex-col-reverse">
      <dt className="text-xs text-muted">{label}</dt>
      <dd className="num m-0 text-xl font-bold">{value}</dd>
    </div>
  );
}

function SpeakerOption({
  name,
  meta,
  checked,
  onChange,
}: {
  name: string;
  meta: string;
  checked: boolean;
  onChange: () => void;
}) {
  return (
    <label
      className={`flex cursor-pointer items-center gap-2.5 rounded-[14px] border px-3 py-2.5 transition ${
        checked ? "border-accent bg-accent-soft" : "border-line bg-surface hover:bg-surface-2"
      }`}
    >
      <span
        aria-hidden="true"
        className="flex h-[30px] w-[30px] flex-none items-center justify-center rounded-full text-xs font-bold text-[#15171C]"
        style={{ background: avatarColor(name) }}
      >
        {initialOf(name)}
      </span>
      <span className="min-w-0 flex-1">
        <b className="block truncate text-sm">{name}</b>
        <span className="num block text-[11px] text-muted">{meta}</span>
      </span>
      <input type="radio" name="self-speaker" checked={checked} onChange={onChange} className="h-[18px] w-[18px] accent-[rgb(var(--accent))]" />
    </label>
  );
}

function ExportGuide() {
  const [tab, setTab] = useState<(typeof EXPORT_GUIDES)[number]["id"]>(EXPORT_GUIDES[0].id);
  const guide = EXPORT_GUIDES.find((item) => item.id === tab) ?? EXPORT_GUIDES[0];
  return (
    <section aria-label="대화 내보내는 법" className="panel flex flex-col gap-3 p-5">
      <h2 className="m-0 text-base font-bold">카톡 대화 내보내는 법</h2>
      <div role="tablist" aria-label="기기" className="flex gap-0.5 rounded-[10px] bg-surface-2 p-[3px]">
        {EXPORT_GUIDES.map((item) => (
          <button
            key={item.id}
            type="button"
            role="tab"
            aria-selected={item.id === tab}
            onClick={() => setTab(item.id)}
            className={`min-h-[36px] flex-1 rounded-lg text-[13px] font-bold ${
              item.id === tab ? "bg-surface text-ink shadow-[0_1px_2px_rgb(0_0_0/0.08)]" : "text-muted"
            }`}
          >
            {item.label}
          </button>
        ))}
      </div>
      <ol role="tabpanel" className="m-0 flex flex-col gap-2 pl-5 text-sm leading-normal">
        {guide.steps.map((stepText) => (
          <li key={stepText}>{stepText}</li>
        ))}
      </ol>
    </section>
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

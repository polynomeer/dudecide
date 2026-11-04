"use client";

import Link from "next/link";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { FormErrorSummary } from "@/components/form-error-summary";
import { apiClient } from "@/lib/api/client";
import { getApiErrorMessage } from "@/lib/api/error-display";
import { BUDGET_OPTIONS, MEETING_TYPES } from "@/lib/display";

type FormValues = {
  title: string;
  meetingType: string;
  expectedHeadcount: number;
  originRegion: string;
  startDate: string;
  endDate: string;
  budgetId: string;
};

const MIN_HEADCOUNT = 2;
const MAX_HEADCOUNT = 30;

export function CreateMeetingForm() {
  const router = useRouter();
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const form = useForm<FormValues>({
    mode: "onBlur",
    defaultValues: {
      title: "",
      meetingType: "trip",
      expectedHeadcount: 4,
      originRegion: "",
      startDate: "",
      endDate: "",
      budgetId: "",
    },
  });
  const meetingType = form.watch("meetingType");
  const headcount = form.watch("expectedHeadcount");
  const budgetId = form.watch("budgetId");
  const startDate = form.watch("startDate");
  const placeholder = MEETING_TYPES.find((type) => type.value === meetingType)?.placeholder ?? "모임 이름";

  const mutation = useMutation({
    mutationFn: async (values: FormValues) => {
      const budget = BUDGET_OPTIONS.find((option) => option.id === values.budgetId);
      return apiClient.createMeeting({
        title: values.title,
        meetingType: values.meetingType,
        expectedHeadcount: values.expectedHeadcount,
        originRegion: values.originRegion || undefined,
        budget: budget ? { min: budget.min, max: budget.max } : undefined,
        startsAt: values.startDate ? new Date(`${values.startDate}T00:00:00`).toISOString() : undefined,
        endsAt: values.endDate || values.startDate
          ? new Date(`${values.endDate || values.startDate}T23:59:59`).toISOString()
          : undefined,
      });
    },
    onSuccess: (response) => {
      router.push(`/meetings/${response.id}`);
    },
    onError: (error: Error) => {
      setErrorMessage(getApiErrorMessage(error, { resourceName: "모임" }));
    },
  });

  const setHeadcount = (next: number) =>
    form.setValue("expectedHeadcount", Math.min(MAX_HEADCOUNT, Math.max(MIN_HEADCOUNT, next)));

  return (
    <form
      className="flex max-w-2xl flex-col gap-5"
      onSubmit={form.handleSubmit((values) => {
        setErrorMessage(null);
        mutation.mutate(values);
      })}
    >
      <FormErrorSummary
        errors={
          form.formState.submitCount > 0 && form.formState.errors.title
            ? [{ fieldId: "meeting-title", message: "모임 이름을 세 글자 이상 적어 주세요." }]
            : []
        }
      />
      <fieldset className="m-0 border-0 p-0">
        <legend className="mb-1.5 text-xs font-bold">모임 종류</legend>
        <div className="grid grid-cols-3 gap-1.5 sm:grid-cols-6">
          {MEETING_TYPES.map((type) => {
            const checked = meetingType === type.value;
            return (
              <label
                key={type.value}
                className={`flex min-h-[52px] cursor-pointer items-center justify-center rounded-xl border px-1.5 text-center text-[13px] font-bold transition ${
                  checked ? "border-accent bg-accent text-on-accent" : "border-line bg-surface text-ink hover:bg-surface-2"
                }`}
              >
                <input type="radio" value={type.value} {...form.register("meetingType")} className="sr-only" />
                {type.label}
              </label>
            );
          })}
        </div>
      </fieldset>

      <div>
        <label htmlFor="meeting-title" className="mb-1.5 block text-xs font-bold">
          모임 이름
        </label>
        <input
          {...form.register("title", { required: true, minLength: 3 })}
          id="meeting-title"
          className="field h-12 text-[15px]"
          placeholder={placeholder}
          aria-invalid={form.formState.errors.title ? "true" : "false"}
        />
        {form.formState.errors.title ? (
          <p className="mt-2 text-sm text-danger">모임 이름을 세 글자 이상 적어 주세요.</p>
        ) : null}
      </div>

      <div className="grid grid-cols-2 gap-2.5">
        <div>
          <span id="headcount-label" className="mb-1.5 block text-xs font-bold">
            예상 인원
          </span>
          <div role="group" aria-labelledby="headcount-label" className="flex h-12 items-center justify-between rounded-xl border border-line bg-surface">
            <button type="button" aria-label="한 명 줄이기" onClick={() => setHeadcount(headcount - 1)} className="h-12 w-11 text-xl font-bold">
              −
            </button>
            <span className="num text-base font-semibold" aria-live="polite">
              {headcount}명
            </span>
            <button type="button" aria-label="한 명 늘리기" onClick={() => setHeadcount(headcount + 1)} className="h-12 w-11 text-xl font-bold">
              +
            </button>
          </div>
        </div>
        <div>
          <label htmlFor="origin-region" className="mb-1.5 block text-xs font-bold">
            출발 지역
          </label>
          <input {...form.register("originRegion")} id="origin-region" className="field h-12 text-[15px]" placeholder="예: 김포, 서울역" />
        </div>
      </div>

      <div className="grid grid-cols-2 gap-2.5">
        <div>
          <label htmlFor="start-date" className="mb-1.5 block text-xs font-bold">
            시작일 (선택)
          </label>
          <input type="date" id="start-date" {...form.register("startDate")} className="field h-12 text-[15px]" />
        </div>
        <div>
          <label htmlFor="end-date" className="mb-1.5 block text-xs font-bold">
            끝나는 날 (선택)
          </label>
          <input
            type="date"
            id="end-date"
            min={startDate || undefined}
            {...form.register("endDate", {
              validate: (value, values) => !value || !values.startDate || value >= values.startDate,
            })}
            className="field h-12 text-[15px]"
            aria-invalid={form.formState.errors.endDate ? "true" : "false"}
          />
          {form.formState.errors.endDate ? (
            <p className="mt-2 text-sm text-danger">시작일보다 빠를 수 없어요.</p>
          ) : null}
        </div>
      </div>

      <fieldset className="m-0 border-0 p-0">
        <legend className="mb-1.5 text-xs font-bold">1인 예산</legend>
        <div className="flex flex-wrap gap-1.5">
          {[...BUDGET_OPTIONS, { id: "", label: "아직 몰라요" }].map((option) => {
            const checked = budgetId === option.id;
            return (
              <label
                key={option.id || "unknown"}
                className={`flex h-11 cursor-pointer items-center rounded-xl border px-3.5 text-[13px] font-bold transition ${
                  checked ? "border-ink bg-ink text-on-ink" : "border-line bg-surface text-ink hover:bg-surface-2"
                }`}
              >
                <input type="radio" value={option.id} {...form.register("budgetId")} className="sr-only" />
                {option.label}
              </label>
            );
          })}
        </div>
      </fieldset>

      {errorMessage ? <p className="m-0 text-sm text-danger">{errorMessage}</p> : null}
      <div className="flex flex-col gap-1 border-t border-line pt-4">
        <button type="submit" disabled={mutation.isPending} className="action-primary min-h-[52px] rounded-[14px] text-[15px]">
          {mutation.isPending ? "만드는 중…" : "모임 만들기"}
        </button>
        <Link href="/import" className="link-accent flex min-h-[44px] items-center justify-center text-[13px]">
          이미 단톡방에서 얘기 중이라면 → 대화로 시작하기
        </Link>
      </div>
    </form>
  );
}

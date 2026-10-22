"use client";

import Link from "next/link";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { FormErrorSummary } from "@/components/form-error-summary";
import { apiClient } from "@/lib/api/client";
import { MEETING_TYPES } from "@/lib/display";

type FormValues = {
  title: string;
  meetingType: string;
  expectedHeadcount: number;
  originRegion: string;
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
    },
  });
  const meetingType = form.watch("meetingType");
  const headcount = form.watch("expectedHeadcount");
  const placeholder = MEETING_TYPES.find((type) => type.value === meetingType)?.placeholder ?? "모임 이름";

  const mutation = useMutation({
    mutationFn: async (values: FormValues) =>
      apiClient.createMeeting({
        title: values.title,
        meetingType: values.meetingType,
        expectedHeadcount: values.expectedHeadcount,
        originRegion: values.originRegion || undefined,
      }),
    onSuccess: (response) => {
      router.push(`/meetings/${response.id}`);
    },
    onError: (error: Error) => {
      setErrorMessage(error.message);
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

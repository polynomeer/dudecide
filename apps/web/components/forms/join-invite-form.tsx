"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { FormErrorSummary } from "@/components/form-error-summary";
import { apiClient } from "@/lib/api/client";

type FormValues = {
  displayName: string;
};

export function JoinInviteForm({ token }: { token: string }) {
  const router = useRouter();
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const form = useForm<FormValues>({
    mode: "onBlur",
    defaultValues: {
      displayName: "",
    },
  });

  const mutation = useMutation({
    mutationFn: (values: FormValues) => apiClient.joinInvite(token, values),
    onSuccess: (response) => {
      router.push(`/meetings/${response.meetingId}`);
    },
    onError: (error: Error) => {
      setErrorMessage(error.message);
    },
  });

  return (
    <form
      aria-label="모임 참여"
      className="flex flex-col gap-4 rounded-[22px] bg-surface p-6"
      onSubmit={form.handleSubmit((values) => {
        setErrorMessage(null);
        mutation.mutate(values);
      })}
    >
      <div>
        <h2 className="m-0 text-[17px] font-bold">참여하고 투표하기</h2>
        <p className="m-0 mt-1 text-[13px] text-muted">가입 없이 이름만 정하면 돼요.</p>
      </div>
      <FormErrorSummary
        errors={
          form.formState.submitCount > 0 && form.formState.errors.displayName
            ? [{ fieldId: "invite-display-name", message: "이름을 두 글자 이상 적어 주세요." }]
            : []
        }
      />
      <div className="flex flex-wrap items-start gap-2">
        <div className="min-w-0 flex-[1_1_240px]">
          <label htmlFor="invite-display-name" className="mb-1.5 block text-xs font-bold">
            모임에서 보일 이름
          </label>
          <input
            {...form.register("displayName", { required: true, minLength: 2 })}
            id="invite-display-name"
            className="field h-12 text-[15px]"
            placeholder="예: 도윤"
            aria-invalid={form.formState.errors.displayName ? "true" : "false"}
          />
          {form.formState.errors.displayName ? (
            <p className="mt-2 text-sm text-danger">두 글자 이상 적어 주세요.</p>
          ) : null}
        </div>
        <button type="submit" disabled={mutation.isPending} className="action-primary mt-[22px] min-h-[48px] px-6">
          {mutation.isPending ? "참여하는 중…" : "참여하기"}
        </button>
      </div>
      {errorMessage ? <p className="m-0 text-sm text-danger">{errorMessage}</p> : null}
    </form>
  );
}

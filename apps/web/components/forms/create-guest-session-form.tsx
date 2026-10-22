"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { useRouter } from "next/navigation";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { FormErrorSummary } from "@/components/form-error-summary";
import { apiClient } from "@/lib/api/client";

type FormValues = {
  displayName: string;
};

export function CreateGuestSessionForm() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const form = useForm<FormValues>({
    mode: "onBlur",
    defaultValues: {
      displayName: "",
    },
  });

  const mutation = useMutation({
    mutationFn: (values: FormValues) => apiClient.createGuestSession(values),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["current-actor"] });
      router.push("/meetings/new");
    },
    onError: (error: Error) => {
      setErrorMessage(error.message);
    },
  });

  return (
    <form
      className="panel flex max-w-xl flex-col gap-5 p-7"
      onSubmit={form.handleSubmit((values) => {
        setErrorMessage(null);
        mutation.mutate(values);
      })}
    >
      <FormErrorSummary
        errors={
          form.formState.submitCount > 0 && form.formState.errors.displayName
            ? [{ fieldId: "display-name", message: "이름을 두 글자 이상 적어 주세요." }]
            : []
        }
      />
      <div>
        <label htmlFor="display-name" className="mb-1.5 block text-xs font-bold">
          모임에서 보일 이름
        </label>
        <input
          {...form.register("displayName", { required: true, minLength: 2 })}
          id="display-name"
          className="field h-12 text-[15px]"
          placeholder="예: 지수"
          aria-invalid={form.formState.errors.displayName ? "true" : "false"}
        />
        {form.formState.errors.displayName ? (
          <p className="mt-2 text-sm text-danger">두 글자 이상 적어 주세요.</p>
        ) : (
          <p className="mt-2 text-xs text-muted">가입 없이 이 브라우저에만 저장돼요. 나중에 바꿀 수 있어요.</p>
        )}
      </div>
      {errorMessage ? <p className="m-0 text-sm text-danger">{errorMessage}</p> : null}
      <button type="submit" disabled={mutation.isPending} className="action-primary min-h-[52px] rounded-[14px] text-[15px]">
        {mutation.isPending ? "시작하는 중…" : "시작하기"}
      </button>
    </form>
  );
}

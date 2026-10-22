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
      className="panel max-w-2xl space-y-5 px-6 py-6"
      onSubmit={form.handleSubmit((values) => {
        setErrorMessage(null);
        mutation.mutate(values);
      })}
    >
      <div className="info-chip normal-case tracking-[0.02em]">Next: land directly in the meeting room</div>
      <FormErrorSummary
        errors={
          form.formState.submitCount > 0 && form.formState.errors.displayName
            ? [{ fieldId: "invite-display-name", message: "Enter a display name with at least two characters." }]
            : []
        }
      />
      <div>
        <label className="mb-2 block text-sm font-semibold">Your display name</label>
        <input
          {...form.register("displayName", { required: true, minLength: 2 })}
          id="invite-display-name"
          className="field"
          placeholder="e.g. Cabin Driver"
          aria-invalid={form.formState.errors.displayName ? "true" : "false"}
        />
        {form.formState.errors.displayName ? (
          <p className="mt-2 text-sm text-ember">Enter at least two characters.</p>
        ) : null}
      </div>
      {errorMessage ? <p className="text-sm text-ember">{errorMessage}</p> : null}
      <button
        type="submit"
        disabled={mutation.isPending}
        className="action-primary disabled:cursor-not-allowed disabled:opacity-60"
      >
        {mutation.isPending ? "Joining..." : "Join meeting"}
      </button>
    </form>
  );
}

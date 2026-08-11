"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { apiClient } from "@/lib/api/client";

type FormValues = {
  displayName: string;
};

export function JoinInviteForm({ token }: { token: string }) {
  const router = useRouter();
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const form = useForm<FormValues>({
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
      <div>
        <label className="mb-2 block text-sm font-semibold">Your display name</label>
        <input
          {...form.register("displayName", { required: true, minLength: 2 })}
          className="w-full rounded-3xl border border-[color:var(--color-line)] bg-white/80 px-4 py-3 outline-none transition focus:border-ink"
          placeholder="e.g. Cabin Driver"
        />
      </div>
      {errorMessage ? <p className="text-sm text-ember">{errorMessage}</p> : null}
      <button
        type="submit"
        disabled={mutation.isPending}
        className="rounded-full bg-ink px-5 py-3 text-sm font-semibold text-mist transition hover:bg-pine disabled:cursor-not-allowed disabled:opacity-60"
      >
        {mutation.isPending ? "Joining..." : "Join meeting"}
      </button>
    </form>
  );
}

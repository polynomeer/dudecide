"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { useRouter } from "next/navigation";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api/client";

type FormValues = {
  displayName: string;
};

export function CreateGuestSessionForm() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const form = useForm<FormValues>({
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
      className="panel max-w-2xl space-y-5 px-6 py-6"
      onSubmit={form.handleSubmit((values) => {
        setErrorMessage(null);
        mutation.mutate(values);
      })}
    >
      <div>
        <label className="mb-2 block text-sm font-semibold">Display name</label>
        <input
          {...form.register("displayName", { required: true, minLength: 2 })}
          className="w-full rounded-3xl border border-[color:var(--color-line)] bg-white/80 px-4 py-3 outline-none transition focus:border-ink"
          placeholder="e.g. Summer Trip Host"
        />
        {form.formState.errors.displayName ? (
          <p className="mt-2 text-sm text-ember">Enter at least two characters.</p>
        ) : null}
      </div>
      {errorMessage ? <p className="text-sm text-ember">{errorMessage}</p> : null}
      <button
        type="submit"
        disabled={mutation.isPending}
        className="rounded-full bg-ink px-5 py-3 text-sm font-semibold text-mist transition hover:bg-pine disabled:cursor-not-allowed disabled:opacity-60"
      >
        {mutation.isPending ? "Creating session..." : "Create guest session"}
      </button>
    </form>
  );
}

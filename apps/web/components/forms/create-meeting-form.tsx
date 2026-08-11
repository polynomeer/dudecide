"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { apiClient } from "@/lib/api/client";

type FormValues = {
  title: string;
  meetingType: string;
  expectedHeadcount: number | "";
  originRegion: string;
};

export function CreateMeetingForm() {
  const router = useRouter();
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const form = useForm<FormValues>({
    defaultValues: {
      title: "",
      meetingType: "trip",
      expectedHeadcount: "",
      originRegion: "",
    },
  });

  const mutation = useMutation({
    mutationFn: async (values: FormValues) =>
      apiClient.createMeeting({
        title: values.title,
        meetingType: values.meetingType,
        expectedHeadcount:
          typeof values.expectedHeadcount === "number" ? values.expectedHeadcount : undefined,
        originRegion: values.originRegion || undefined,
      }),
    onSuccess: (response) => {
      router.push(`/meetings/${response.id}`);
    },
    onError: (error: Error) => {
      setErrorMessage(error.message);
    },
  });

  return (
    <form
      className="panel max-w-3xl space-y-6 px-6 py-6"
      onSubmit={form.handleSubmit((values) => {
        setErrorMessage(null);
        mutation.mutate(values);
      })}
    >
      <div className="grid gap-5 md:grid-cols-2">
        <div className="md:col-span-2">
          <label className="mb-2 block text-sm font-semibold">Meeting title</label>
          <input
            {...form.register("title", { required: true, minLength: 3 })}
            className="w-full rounded-3xl border border-[color:var(--color-line)] bg-white/80 px-4 py-3 outline-none transition focus:border-ink"
            placeholder="e.g. 2026 Autumn Cabin Trip"
          />
        </div>
        <div>
          <label className="mb-2 block text-sm font-semibold">Meeting type</label>
          <select
            {...form.register("meetingType", { required: true })}
            className="w-full rounded-3xl border border-[color:var(--color-line)] bg-white/80 px-4 py-3 outline-none transition focus:border-ink"
          >
            <option value="trip">Trip</option>
            <option value="outing">Outing</option>
            <option value="retreat">Retreat</option>
          </select>
        </div>
        <div>
          <label className="mb-2 block text-sm font-semibold">Expected headcount</label>
          <input
            type="number"
            {...form.register("expectedHeadcount", {
              valueAsNumber: true,
            })}
            className="w-full rounded-3xl border border-[color:var(--color-line)] bg-white/80 px-4 py-3 outline-none transition focus:border-ink"
            placeholder="8"
          />
        </div>
        <div className="md:col-span-2">
          <label className="mb-2 block text-sm font-semibold">Origin region</label>
          <input
            {...form.register("originRegion")}
            className="w-full rounded-3xl border border-[color:var(--color-line)] bg-white/80 px-4 py-3 outline-none transition focus:border-ink"
            placeholder="Seoul"
          />
        </div>
      </div>
      {errorMessage ? <p className="text-sm text-ember">{errorMessage}</p> : null}
      <button
        type="submit"
        disabled={mutation.isPending}
        className="rounded-full bg-ink px-5 py-3 text-sm font-semibold text-mist transition hover:bg-pine disabled:cursor-not-allowed disabled:opacity-60"
      >
        {mutation.isPending ? "Creating meeting..." : "Create meeting"}
      </button>
    </form>
  );
}

"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { FormErrorSummary } from "@/components/form-error-summary";
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
    mode: "onBlur",
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
      <div className="info-chip normal-case tracking-[0.02em]">Next: invite people and start threads</div>
      <FormErrorSummary
        errors={
          form.formState.submitCount > 0 && form.formState.errors.title
            ? [{ fieldId: "meeting-title", message: "Enter a meeting title with at least three characters." }]
            : []
        }
      />
      <div className="grid gap-5 md:grid-cols-2">
        <div className="md:col-span-2">
          <label className="mb-2 block text-sm font-semibold">Meeting title</label>
          <input
            {...form.register("title", { required: true, minLength: 3 })}
            id="meeting-title"
            className="field"
            placeholder="e.g. 2026 Autumn Cabin Trip"
            aria-invalid={form.formState.errors.title ? "true" : "false"}
          />
          {form.formState.errors.title ? (
            <p className="mt-2 text-sm text-ember">Enter a meeting title with at least three characters.</p>
          ) : null}
        </div>
        <div>
          <label className="mb-2 block text-sm font-semibold">Meeting type</label>
          <select
            {...form.register("meetingType", { required: true })}
            className="field"
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
            className="field"
            placeholder="8"
          />
        </div>
        <div className="md:col-span-2">
          <label className="mb-2 block text-sm font-semibold">Origin region</label>
          <input
            {...form.register("originRegion")}
            className="field"
            placeholder="Seoul"
          />
        </div>
      </div>
      {errorMessage ? <p className="text-sm text-ember">{errorMessage}</p> : null}
      <button
        type="submit"
        disabled={mutation.isPending}
        className="action-primary disabled:cursor-not-allowed disabled:opacity-60"
      >
        {mutation.isPending ? "Creating meeting..." : "Create meeting"}
      </button>
    </form>
  );
}

"use client";

import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { getApiErrorMessage } from "@/lib/api/error-display";
import { apiClient, type MeetingDetailResponse } from "@/lib/api/client";

function toDateInput(value?: string | null) {
  if (!value) {
    return "";
  }
  const date = new Date(value);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

/** Host-only inline editor for the event dates that drive the D-day countdown. */
export function EventDatesEditor({ meeting }: { meeting: MeetingDetailResponse }) {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [start, setStart] = useState(toDateInput(meeting.startsAt));
  const [end, setEnd] = useState(toDateInput(meeting.endsAt));
  const mutation = useMutation({
    mutationFn: () =>
      apiClient.updateMeeting(meeting.id, {
        startsAt: new Date(`${start}T00:00:00`).toISOString(),
        endsAt: new Date(`${end || start}T23:59:59`).toISOString(),
      }),
    onSuccess: async (updated) => {
      queryClient.setQueryData(["meeting", meeting.id], updated);
      setOpen(false);
    },
  });

  if (!open) {
    return (
      <button type="button" onClick={() => setOpen(true)} className="link-accent text-xs">
        {meeting.startsAt ? "날짜 바꾸기" : "+ 날짜 정하기"}
      </button>
    );
  }

  const invalid = !start || (end !== "" && end < start);

  return (
    <form
      aria-label="모임 날짜"
      className="mt-2 flex flex-wrap items-end gap-2"
      onSubmit={(event) => {
        event.preventDefault();
        if (!invalid) {
          mutation.mutate();
        }
      }}
    >
      <label className="flex flex-col gap-1 text-xs font-bold">
        시작일
        <input type="date" value={start} onChange={(event) => setStart(event.target.value)} className="field h-10 py-0" required />
      </label>
      <label className="flex flex-col gap-1 text-xs font-bold">
        끝나는 날
        <input
          type="date"
          value={end}
          min={start || undefined}
          onChange={(event) => setEnd(event.target.value)}
          className="field h-10 py-0"
        />
      </label>
      <button type="submit" disabled={invalid || mutation.isPending} className="action-primary min-h-[40px]">
        {mutation.isPending ? "저장 중…" : "저장"}
      </button>
      <button type="button" onClick={() => setOpen(false)} className="btn-ghost">
        취소
      </button>
      {mutation.isError ? (
        <p role="alert" className="m-0 w-full text-sm text-danger">
          {getApiErrorMessage(mutation.error, { resourceName: "모임 날짜" })}
        </p>
      ) : null}
    </form>
  );
}

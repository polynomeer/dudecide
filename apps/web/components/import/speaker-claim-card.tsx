"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { ApiError, apiClient } from "@/lib/api/client";

const DISMISS_KEY = (meetingId: string) => `dudecide:speaker-claim-dismissed:${meetingId}`;

/**
 * Asks a member which name they use in the imported chat. Members type the name instead of picking from a list,
 * so other speakers' exported names are never shown to them.
 */
export function SpeakerClaimCard({ meetingId }: { meetingId: string }) {
  const queryClient = useQueryClient();
  const [name, setName] = useState("");
  const [dismissed, setDismissed] = useState(true);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    setDismissed(readDismissed(meetingId));
  }, [meetingId]);

  const speakersQuery = useQuery({
    queryKey: ["import-speakers", meetingId],
    queryFn: () => apiClient.listImportSpeakers(meetingId),
    retry: false,
  });

  const claimMutation = useMutation({
    mutationFn: () => apiClient.claimImportSpeaker(meetingId, name.trim()),
    onSuccess: async () => {
      setMessage(null);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["import-speakers", meetingId] }),
        queryClient.invalidateQueries({ queryKey: ["thread-messages"] }),
        queryClient.invalidateQueries({ queryKey: ["chat-import-evidence"] }),
      ]);
    },
    onError: (error: unknown) => {
      const status = error instanceof ApiError ? error.status : null;
      setMessage(
        status === 404
          ? "그 이름의 참여자를 찾지 못했어요. 카카오톡에 보이는 이름 그대로 적어 주세요."
          : status === 409
            ? "이미 다른 이름으로 연결되어 있어요. 바꾸려면 호스트에게 요청해 주세요."
            : "연결하지 못했어요. 잠시 후 다시 시도해 주세요.",
      );
    },
  });

  const speakers = speakersQuery.data ?? [];
  const alreadyClaimed = speakers.some((speaker) => speaker.mine);
  const unclaimed = speakers.some((speaker) => !speaker.mapped);
  if (dismissed || speakers.length === 0 || alreadyClaimed || !unclaimed) {
    return null;
  }

  const dismiss = () => {
    writeDismissed(meetingId);
    setDismissed(true);
  };

  return (
    <aside
      aria-label="대화 속 내 이름 연결"
      className="fixed bottom-4 left-4 z-50 w-[min(22rem,calc(100vw-2rem))] rounded-[20px] bg-surface px-5 py-5 shadow-[0_16px_40px_rgb(0_0_0/0.16)] ring-2 ring-accent"
    >
      <h2 className="m-0 text-base font-bold">가져온 대화에 나도 있나요?</h2>
      <p className="m-0 mt-1 text-sm text-muted">카카오톡에서 쓰는 이름을 적으면, 대화 속 내 메시지에 내 이름이 표시됩니다.</p>
      <form
        className="mt-3 flex gap-2"
        onSubmit={(event) => {
          event.preventDefault();
          if (name.trim()) {
            claimMutation.mutate();
          }
        }}
      >
        <input
          aria-label="카카오톡 이름"
          value={name}
          onChange={(event) => setName(event.target.value)}
          className="field min-w-0 flex-1"
          placeholder="예: 준호"
        />
        <button type="submit" disabled={!name.trim() || claimMutation.isPending} className="action-primary">
          연결
        </button>
      </form>
      {message ? (
        <p role="alert" className="mt-2 text-sm text-danger">
          {message}
        </p>
      ) : null}
      <button type="button" onClick={dismiss} className="mt-3 text-xs text-muted underline">
        나중에 할게요
      </button>
    </aside>
  );
}

function readDismissed(meetingId: string): boolean {
  try {
    return window.localStorage.getItem(DISMISS_KEY(meetingId)) === "1";
  } catch {
    return false;
  }
}

function writeDismissed(meetingId: string) {
  try {
    window.localStorage.setItem(DISMISS_KEY(meetingId), "1");
  } catch {
    // Storage can be unavailable (private mode); dismissing then only lasts for this page.
  }
}

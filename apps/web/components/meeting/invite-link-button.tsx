"use client";

import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { apiClient } from "@/lib/api/client";

/** Creates an invite link on first click and copies it to the clipboard. */
export function InviteLinkButton({ meetingId, className = "action-secondary" }: { meetingId: string; className?: string }) {
  const [copied, setCopied] = useState(false);
  const [failedUrl, setFailedUrl] = useState<string | null>(null);
  const inviteMutation = useMutation({
    mutationFn: () => apiClient.createInviteLink(meetingId),
  });

  async function handleClick() {
    const invite = inviteMutation.data ?? (await inviteMutation.mutateAsync());
    const url = `${window.location.origin}/invite/${invite.token}`;
    try {
      await navigator.clipboard.writeText(url);
      setCopied(true);
      setFailedUrl(null);
    } catch {
      // Clipboard can be blocked; show the link so it can be copied by hand.
      setFailedUrl(url);
    }
  }

  return (
    <span className="inline-flex flex-col gap-1">
      <button
        type="button"
        onClick={() => void handleClick().catch(() => undefined)}
        disabled={inviteMutation.isPending}
        className={className}
      >
        {copied ? "복사됨 ✓" : inviteMutation.isError ? "다시 시도" : "초대 링크 복사"}
      </button>
      {failedUrl ? (
        <input readOnly value={failedUrl} aria-label="초대 링크" className="field num text-xs" />
      ) : null}
    </span>
  );
}

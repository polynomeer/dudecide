"use client";

import { useQuery } from "@tanstack/react-query";
import { apiClient, type MembershipResponse } from "@/lib/api/client";

export function useMeetingMembers(meetingId?: string) {
  return useQuery({
    queryKey: ["meeting-members", meetingId],
    queryFn: () => apiClient.listMembers(meetingId!),
    enabled: Boolean(meetingId),
  });
}

/** Display name for a user in this meeting, or null when they are not (or no longer) a member. */
export function memberName(members: MembershipResponse[] | undefined, userId?: string | null): string | null {
  if (!userId) {
    return null;
  }
  return members?.find((member) => member.userId === userId)?.displayName ?? null;
}

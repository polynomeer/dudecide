"use client";

import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api/client";

export function useCurrentActor() {
  return useQuery({
    queryKey: ["current-actor"],
    queryFn: () => apiClient.getCurrentActor(),
  });
}

export function useMeetingHostAccess(meetingId?: string) {
  const actorQuery = useCurrentActor();
  const meetingQuery = useQuery({
    queryKey: ["meeting", meetingId],
    queryFn: () => apiClient.getMeeting(meetingId!),
    enabled: Boolean(meetingId),
  });

  return {
    actorQuery,
    meetingQuery,
    isHost:
      Boolean(actorQuery.data?.userId) &&
      Boolean(meetingQuery.data?.ownerUserId) &&
      actorQuery.data?.userId === meetingQuery.data?.ownerUserId,
  };
}

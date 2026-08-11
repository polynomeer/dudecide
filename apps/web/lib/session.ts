"use client";

import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api/client";

export function useCurrentActor() {
  return useQuery({
    queryKey: ["current-actor"],
    queryFn: () => apiClient.getCurrentActor(),
  });
}

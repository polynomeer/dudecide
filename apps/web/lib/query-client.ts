import { QueryClient } from "@tanstack/react-query";
import { ApiError } from "@/lib/api/client";

const MAX_RETRIES = 3;

export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 10_000,
        refetchOnWindowFocus: false,
        // A 4xx answer will not change on retry; a missing session should redirect right away.
        retry: (failureCount, error) =>
          failureCount < MAX_RETRIES && !(error instanceof ApiError && error.status >= 400 && error.status < 500),
      },
    },
  });
}

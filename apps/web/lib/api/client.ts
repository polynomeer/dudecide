export type CurrentActorResponse = {
  userId: string;
  displayName: string;
  authSource: string;
  createdAt?: string | null;
};

export type CreateGuestSessionRequest = {
  displayName: string;
};

export type CreateMeetingRequest = {
  title: string;
  meetingType: string;
  expectedHeadcount?: number;
  originRegion?: string;
};

export type CreateMeetingResponse = {
  id: string;
  title: string;
  meetingType: string;
  status: string;
};

export type JoinInviteRequest = {
  displayName: string;
};

export type MembershipResponse = {
  id: string;
  meetingId: string;
  userId: string;
  displayName: string;
  role: string;
  attendanceStatus: string;
  joinedAt: string;
};

export type ThreadSummaryView = {
  id: string;
  category: string;
  title: string;
  status: string;
  sortOrder: number;
  messageCount: number;
  decisionCount: number;
  openVoteCount: number;
  lastActivityAt?: string | null;
};

export type MeetingDetailResponse = {
  id: string;
  title: string;
  meetingType: string;
  status: string;
  originRegion?: string | null;
  expectedHeadcount?: number | null;
  ownerUserId: string;
  memberCount: number;
  decisionCount: number;
  openVoteCount: number;
  threads: ThreadSummaryView[];
};

export type MessageView = {
  id: string;
  threadId: string;
  senderUserId: string;
  body: string;
  attachments: Array<Record<string, unknown>>;
  aiLabels: Record<string, unknown>;
  createdAt: string;
  editedAt?: string | null;
};

export type CreateMessageRequest = {
  body: string;
  attachments?: Array<Record<string, unknown>>;
};

export type DecisionResponse = {
  id: string;
  meetingId: string;
  threadId: string;
  decisionType: string;
  title: string;
  status: string;
  proposedResult: Record<string, unknown>;
  confirmedResult: Record<string, unknown>;
  decidedAt?: string | null;
};

export type DecisionEvidenceView = {
  id: string;
  messageId: string;
  threadId: string;
  evidenceType: string;
  quoteSnapshot: string;
  confidence?: number | null;
  createdAt?: string | null;
};

export type DecisionHistoryView = {
  id: string;
  actorUserId?: string | null;
  action: string;
  reason?: string | null;
  beforeState: Record<string, unknown>;
  afterState: Record<string, unknown>;
  createdAt?: string | null;
};

export type DecisionDetailResponse = DecisionResponse & {
  evidence: DecisionEvidenceView[];
  history: DecisionHistoryView[];
};

export type CreateDecisionRequest = {
  threadId: string;
  decisionType: string;
  title: string;
  proposedResult?: Record<string, unknown>;
};

export type ConfirmDecisionRequest = {
  confirmedResult: Record<string, unknown>;
  reason?: string;
};

export type ReconsiderDecisionRequest = {
  reason: string;
};

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL?.replace(/\/$/, "") ?? "http://localhost:8080";

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(init?.headers ?? {}),
    },
    credentials: "include",
    cache: "no-store",
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Request failed with ${response.status}`);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}

export const apiClient = {
  getCurrentActor: () => request<CurrentActorResponse>("/api/v1/auth/me"),
  createGuestSession: (body: CreateGuestSessionRequest) =>
    request<CurrentActorResponse>("/api/v1/auth/guest-sessions", {
      method: "POST",
      body: JSON.stringify(body),
    }),
  clearSession: () =>
    request<void>("/api/v1/auth/session", {
      method: "DELETE",
    }),
  createMeeting: (body: CreateMeetingRequest) =>
    request<CreateMeetingResponse>("/api/v1/meetings", {
      method: "POST",
      body: JSON.stringify(body),
    }),
  joinInvite: (token: string, body: JoinInviteRequest) =>
    request<MembershipResponse>(`/api/v1/invite-links/${token}/join`, {
      method: "POST",
      body: JSON.stringify(body),
    }),
  getMeeting: (meetingId: string) => request<MeetingDetailResponse>(`/api/v1/meetings/${meetingId}`),
  listMessages: (threadId: string, before?: string, limit = 50) => {
    const query = new URLSearchParams();
    if (before) {
      query.set("before", before);
    }
    query.set("limit", String(limit));
    const suffix = query.toString();
    return request<MessageView[]>(
      `/api/v1/threads/${threadId}/messages${suffix ? `?${suffix}` : ""}`,
    );
  },
  postMessage: (threadId: string, body: CreateMessageRequest) =>
    request<MessageView>(`/api/v1/threads/${threadId}/messages`, {
      method: "POST",
      body: JSON.stringify(body),
    }),
  listDecisions: (meetingId: string) =>
    request<DecisionResponse[]>(`/api/v1/meetings/${meetingId}/decisions`),
  getDecision: (decisionId: string) =>
    request<DecisionDetailResponse>(`/api/v1/decisions/${decisionId}`),
  createDecision: (meetingId: string, body: CreateDecisionRequest) =>
    request<DecisionResponse>(`/api/v1/meetings/${meetingId}/decisions`, {
      method: "POST",
      body: JSON.stringify(body),
    }),
  confirmDecision: (decisionId: string, body: ConfirmDecisionRequest) =>
    request<DecisionResponse>(`/api/v1/decisions/${decisionId}/confirm`, {
      method: "POST",
      body: JSON.stringify(body),
    }),
  reconsiderDecision: (decisionId: string, body: ReconsiderDecisionRequest) =>
    request<DecisionResponse>(`/api/v1/decisions/${decisionId}/reconsider`, {
      method: "POST",
      body: JSON.stringify(body),
    }),
};

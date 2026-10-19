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
  readOnly?: boolean;
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

export type MessageSpeakerView = {
  displayName: string;
  userId?: string | null;
  importSpeakerId?: string | null;
  pseudonymized: boolean;
};

export type MessageView = {
  id: string;
  threadId: string;
  senderUserId: string | null;
  origin: "native" | "imported";
  speaker: MessageSpeakerView;
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

export type AddDecisionEvidenceRequest = {
  messageId: string;
  evidenceType: string;
  quoteSnapshot?: string;
  confidence?: number;
};

export type CreateVoteOptionRequest = {
  optionType: string;
  label: string;
  payload?: Record<string, unknown>;
  sortOrder?: number;
};

export type CreateVoteRequest = {
  threadId: string;
  decisionId?: string;
  decisionType?: string;
  title: string;
  voteType: string;
  opensAt?: string;
  deadlineAt?: string;
  options: CreateVoteOptionRequest[];
};

export type VoteSubmissionItem = {
  optionId: string;
  responseValue: string;
};

export type VoteSubmissionRequest = {
  responses: VoteSubmissionItem[];
};

export type VoteOptionView = {
  id: string;
  optionType: string;
  label: string;
  payload: Record<string, unknown>;
};

export type VoteAggregateOptionView = {
  optionId: string;
  label: string;
  counts: Record<string, number>;
  rawScore?: number | null;
  rank?: number | null;
};

export type VoteAggregateView = {
  totalParticipants: number;
  respondedParticipants: number;
  unresolvedParticipants: number;
  canEarlyClose?: boolean | null;
  leadingOptionIds: string[];
  options: VoteAggregateOptionView[];
};

export type VoteResponseView = {
  voteId: string;
  meetingId: string;
  decisionId?: string | null;
  threadId: string;
  voteType: string;
  status: string;
  opensAt?: string | null;
  deadlineAt?: string | null;
  closedAt?: string | null;
  options: VoteOptionView[];
  responses: VoteSubmissionItem[];
  aggregates?: VoteAggregateView | null;
};

export type SearchMessageView = {
  id: string;
  threadId: string;
  body: string;
  createdAt: string;
};

export type SearchDecisionView = {
  id: string;
  threadId: string;
  title: string;
  status: string;
};

export type TaskView = {
  id: string;
  meetingId: string;
  ownerUserId?: string | null;
  sourceMessageId?: string | null;
  title: string;
  status: string;
  dueAt?: string | null;
  createdAt: string;
};

export type SearchResponse = {
  meetingId: string;
  query: string;
  messages: SearchMessageView[];
  decisions: SearchDecisionView[];
  tasks: TaskView[];
};

export type MeetingReportResponse = {
  id: string;
  meetingId: string;
  version: number;
  snapshot: Record<string, unknown>;
  generatedAt: string;
};

export type CreateTaskRequest = {
  title: string;
  ownerUserId?: string;
  sourceMessageId?: string;
  dueAt?: string;
};

export type UpdateTaskRequest = {
  title?: string;
  status?: string;
  ownerUserId?: string;
  dueAt?: string;
};

export type NotificationView = {
  id: string;
  meetingId: string;
  recipientUserId: string;
  type: string;
  payload: Record<string, unknown>;
  sentAt?: string | null;
  createdAt: string;
};

export type ChatImportSourceType = "kakao_export" | "paste";

export type ChatImportStatus = "submitted" | "analyzing" | "ready" | "applied" | "failed" | "deleted";

export type ChatImportSpeakerInput = {
  key: string;
  displayName: string;
  isSelf?: boolean | null;
  membershipId?: string | null;
};

export type ChatImportMessageInput = {
  speakerKey: string;
  sentAt: string;
  body: string;
};

export type CreateChatImportRequest = {
  source: {
    type: ChatImportSourceType;
    format: string;
    formatVersion?: string | null;
    roomName?: string | null;
  };
  range: { start: string; end: string };
  speakers: ChatImportSpeakerInput[];
  messages: ChatImportMessageInput[];
  consent: { isParticipant: boolean; version: string };
  mediaCounts?: Array<{ date: string; count: number }> | null;
  stats?: {
    parsedCount?: number;
    uploadedCount?: number;
    maskedCount?: number;
    unparsedLineCount?: number;
  } | null;
  meetingTitle?: string | null;
  meetingType?: string | null;
};

export type ChatImportCreatedResponse = {
  importId: string;
  meetingId: string;
  threadId: string;
  status: ChatImportStatus;
  messageCount: number;
  duplicateCount: number;
};

export type ChatImportView = {
  id: string;
  meetingId: string;
  threadId: string;
  uploadedByUserId: string;
  sourceType: string;
  sourceFormat: string;
  sourceRoomName?: string | null;
  rangeStart: string;
  rangeEnd: string;
  messageCount: number;
  duplicateCount: number;
  status: ChatImportStatus;
  progress: Record<string, unknown>;
  result?: Record<string, unknown> | null;
  failureReason?: string | null;
  purgeAt?: string | null;
  createdAt: string;
  completedAt?: string | null;
  deletionImpact?: ChatImportDeletionImpact | null;
};

export type ChatImportDeletionImpact = {
  removedMessages: number;
  removedEvidence: number;
  affectedConfirmedDecisions: number;
};

export type DeleteChatImportResponse = ChatImportDeletionImpact & {
  importId: string;
};

export type ApplyChatImportResponse = {
  importId: string;
  appliedItemIds: string[];
  skippedItemIds: string[];
  decisionIds: string[];
  taskIds: string[];
  meetingStatus: string;
};

export type ImportSpeakerView = {
  id: string;
  threadId: string;
  label: string;
  mapped: boolean;
  membershipId?: string | null;
  displayName?: string | null;
  pseudonym?: string | null;
  mine: boolean;
};

export type InvitePreviewResponse = {
  meetingTitle: string;
  meetingStatus: string;
  memberCount: number;
  decisions: Array<{ title: string; decisionType: string; status: string }>;
  pendingDecisionCount: number;
  confirmedDecisionCount: number;
  openTaskCount: number;
  openQuestionCount: number;
};

export type InviteLinkResponse = {
  id: string;
  meetingId: string;
  token: string;
  status: string;
  expiresAt?: string | null;
  createdAt: string;
};

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL?.replace(/\/$/, "") ?? "http://localhost:8080";

export class ApiError extends Error {
  status: number;
  body: string;

  constructor(status: number, body: string) {
    super(body || `Request failed with ${status}`);
    this.name = "ApiError";
    this.status = status;
    this.body = body;
  }
}

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
    throw new ApiError(response.status, text || `Request failed with ${response.status}`);
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
  previewInvite: (token: string) => request<InvitePreviewResponse>(`/api/v1/invite-links/${token}/preview`),
  createInviteLink: (meetingId: string, expiresAt?: string) =>
    request<InviteLinkResponse>(`/api/v1/meetings/${meetingId}/invite-links`, {
      method: "POST",
      body: JSON.stringify({ expiresAt: expiresAt ?? null }),
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
  createChatImport: (body: CreateChatImportRequest, idempotencyKey?: string) =>
    request<ChatImportCreatedResponse>("/api/v1/imports", {
      method: "POST",
      body: JSON.stringify(body),
      headers: idempotencyKey ? { "Idempotency-Key": idempotencyKey } : undefined,
    }),
  createMeetingChatImport: (meetingId: string, body: CreateChatImportRequest, idempotencyKey?: string) =>
    request<ChatImportCreatedResponse>(`/api/v1/meetings/${meetingId}/imports`, {
      method: "POST",
      body: JSON.stringify(body),
      headers: idempotencyKey ? { "Idempotency-Key": idempotencyKey } : undefined,
    }),
  listChatImports: (meetingId: string) => request<ChatImportView[]>(`/api/v1/meetings/${meetingId}/imports`),
  getChatImport: (importId: string, includeDeletionImpact = false) =>
    request<ChatImportView>(
      `/api/v1/imports/${importId}${includeDeletionImpact ? "?includeDeletionImpact=true" : ""}`,
    ),
  getChatImportEvidence: (importId: string) => request<MessageView[]>(`/api/v1/imports/${importId}/evidence`),
  applyChatImport: (importId: string, itemIds: string[], idempotencyKey?: string) =>
    request<ApplyChatImportResponse>(`/api/v1/imports/${importId}/apply`, {
      method: "POST",
      body: JSON.stringify({ itemIds }),
      headers: idempotencyKey ? { "Idempotency-Key": idempotencyKey } : undefined,
    }),
  deleteChatImport: (importId: string) =>
    request<DeleteChatImportResponse>(`/api/v1/imports/${importId}`, {
      method: "DELETE",
    }),
  listImportSpeakers: (meetingId: string) =>
    request<ImportSpeakerView[]>(`/api/v1/meetings/${meetingId}/import-speakers`),
  updateImportSpeaker: (speakerId: string, membershipId: string | null) =>
    request<ImportSpeakerView>(`/api/v1/import-speakers/${speakerId}`, {
      method: "PATCH",
      body: JSON.stringify({ membershipId }),
    }),
  claimImportSpeaker: (meetingId: string, displayName: string) =>
    request<{ claimedSpeakerIds: string[] }>(`/api/v1/meetings/${meetingId}/import-speakers/claim`, {
      method: "POST",
      body: JSON.stringify({ displayName }),
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
  addDecisionEvidence: (decisionId: string, body: AddDecisionEvidenceRequest) =>
    request<DecisionEvidenceView>(`/api/v1/decisions/${decisionId}/evidence`, {
      method: "POST",
      body: JSON.stringify(body),
    }),
  createVote: (meetingId: string, body: CreateVoteRequest) =>
    request<VoteResponseView>(`/api/v1/meetings/${meetingId}/votes`, {
      method: "POST",
      body: JSON.stringify(body),
    }),
  getVote: (voteId: string) =>
    request<VoteResponseView>(`/api/v1/votes/${voteId}`),
  submitVoteResponses: (voteId: string, body: VoteSubmissionRequest) =>
    request<VoteResponseView>(`/api/v1/votes/${voteId}/responses/me`, {
      method: "PUT",
      body: JSON.stringify(body),
    }),
  closeVote: (voteId: string) =>
    request<VoteResponseView>(`/api/v1/votes/${voteId}/close`, {
      method: "POST",
    }),
  searchMeeting: (meetingId: string, query: string) => {
    const params = new URLSearchParams({ q: query });
    return request<SearchResponse>(`/api/v1/meetings/${meetingId}/search?${params.toString()}`);
  },
  getLatestReport: (meetingId: string) =>
    request<MeetingReportResponse>(`/api/v1/meetings/${meetingId}/reports/latest`),
  refreshReport: (meetingId: string) =>
    request<MeetingReportResponse>(`/api/v1/meetings/${meetingId}/reports/refresh`, {
      method: "POST",
    }),
  listTasks: (meetingId: string) =>
    request<TaskView[]>(`/api/v1/meetings/${meetingId}/tasks`),
  createTask: (meetingId: string, body: CreateTaskRequest) =>
    request<TaskView>(`/api/v1/meetings/${meetingId}/tasks`, {
      method: "POST",
      body: JSON.stringify(body),
    }),
  updateTask: (taskId: string, body: UpdateTaskRequest) =>
    request<TaskView>(`/api/v1/tasks/${taskId}`, {
      method: "PATCH",
      body: JSON.stringify(body),
    }),
  listNotifications: (meetingId: string) =>
    request<NotificationView[]>(`/api/v1/meetings/${meetingId}/notifications`),
};

export interface Interaction {
  id: string;
  customerId: string;
  interactionType: string;
  summary: string;
  correlationId: string;
  createdAt: string;
  version: number;
}

export interface CreateInteractionRequest {
  customerId: string;
  interactionType: string;
  summary: string;
  correlationId?: string;
}

export interface UpdateInteractionRequest {
  version: number;
  interactionType?: string;
  summary?: string;
}

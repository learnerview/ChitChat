import { Client, type StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import type { RealtimeEvent } from '../api/types';

export type RealtimeEventHandler = (event: RealtimeEvent) => void;
export type ConnectionStatus = 'connected' | 'connecting' | 'offline';

const MAX_ATTEMPTS = 5;
const MAX_DELAY_MS = 15_000;

/**
 * STOMP-over-SockJS client with bounded exponential-backoff reconnect
 * (1s, 2s, 4s, 8s, 15s). After MAX_ATTEMPTS failures it goes `offline`
 * and stays there until `reconnect()` is called manually.
 *
 * Authentication uses a fresh single-use ticket per attempt, so the
 * long-lived JWT never appears in a URL. After a reconnect it re-subscribes
 * every watched conversation and re-issues the resume command.
 */
export class RealtimeClient {
  private client: Client | null = null;
  private currentUrl = '/ws';
  private readonly handlers = new Map<string, RealtimeEventHandler>();
  private readonly subscriptions = new Map<string, StompSubscription>();
  private readonly lastSequences = new Map<string, number>();
  private syncHandler: RealtimeEventHandler | null = null;
  private statusHandler: ((status: ConnectionStatus) => void) | null = null;
  private ticketProvider: (() => Promise<string>) | null = null;
  private attempts = 0;
  private stopped = false;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;

  connect(
    getTicket: () => Promise<string>,
    onSync: RealtimeEventHandler,
    onStatus: (status: ConnectionStatus) => void,
  ): void {
    this.disconnect();
    this.stopped = false;
    this.attempts = 0;
    this.ticketProvider = getTicket;
    this.syncHandler = onSync;
    this.statusHandler = onStatus;
    this.open();
  }

  /** Manual recovery after the client gave up (status `offline`). */
  reconnect(): void {
    if (this.stopped || !this.ticketProvider || !this.syncHandler || !this.statusHandler) return;
    this.attempts = 0;
    this.open();
  }

  private open(): void {
    this.statusHandler?.('connecting');
    const client = new Client({
      webSocketFactory: () => new SockJS(this.currentUrl),
      reconnectDelay: 0, // retries are managed here, not by stomp
      beforeConnect: async () => {
        try {
          const ticket = await this.ticketProvider!();
          this.currentUrl = `/ws?ticket=${encodeURIComponent(ticket)}`;
        } catch (err) {
          this.scheduleRetry();
          throw err;
        }
      },
      onConnect: () => {
        this.attempts = 0;
        this.statusHandler?.('connected');
        client.subscribe('/user/queue/sync', frame => {
          this.syncHandler?.(JSON.parse(frame.body) as RealtimeEvent);
        });
        for (const conversationId of this.handlers.keys()) {
          this.subscribeTopic(client, conversationId);
        }
      },
      onWebSocketClose: () => {
        if (!this.stopped) this.scheduleRetry();
      },
      onStompError: () => {
        if (!this.stopped) this.scheduleRetry();
      },
    });
    this.client = client;
    client.activate();
  }

  private scheduleRetry(): void {
    if (this.stopped || this.reconnectTimer !== null) return;
    this.attempts++;
    if (this.attempts > MAX_ATTEMPTS) {
      this.statusHandler?.('offline');
      return;
    }
    this.statusHandler?.('connecting');
    const delay = Math.min(1000 * 2 ** (this.attempts - 1), MAX_DELAY_MS);
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.open();
    }, delay);
  }

  /** Watch a conversation's topic. `lastSequence` is the resume position. */
  watchConversation(
    conversationId: string,
    lastSequence: number,
    handler: RealtimeEventHandler,
  ): void {
    this.handlers.set(conversationId, handler);
    this.noteSequence(conversationId, lastSequence);
    if (this.client?.connected) {
      this.subscribeTopic(this.client, conversationId);
    }
  }

  /** Advance the resume position as newer messages are observed. */
  noteSequence(conversationId: string, sequence: number): void {
    if (sequence > (this.lastSequences.get(conversationId) ?? 0)) {
      this.lastSequences.set(conversationId, sequence);
    }
  }

  private subscribeTopic(client: Client, conversationId: string): void {
    if (!client.connected) return;
    const handler = this.handlers.get(conversationId);
    if (!handler) return;

    this.subscriptions.get(conversationId)?.unsubscribe();
    this.subscriptions.set(
      conversationId,
      client.subscribe(`/topic/conversations/${conversationId}`, frame => {
        handler(JSON.parse(frame.body) as RealtimeEvent);
      }),
    );
    client.publish({
      destination: `/app/conversations/${conversationId}/resume`,
      body: JSON.stringify({ afterSequence: this.lastSequences.get(conversationId) ?? 0 }),
    });
  }

  disconnect(): void {
    this.stopped = true;
    if (this.reconnectTimer !== null) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    this.subscriptions.clear();
    void this.client?.deactivate();
    this.client = null;
  }
}

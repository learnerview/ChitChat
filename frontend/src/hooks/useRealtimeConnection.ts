import { useCallback, useEffect, useState } from 'react';
import { api } from '../api/client';
import type { AuthResponse, RealtimeEvent } from '../api/types';
import { RealtimeClient, type ConnectionStatus } from '../ws/realtime';

/**
 * Owns the realtime connection lifecycle: connect with fresh single-use
 * tickets, bounded backoff, manual reconnect after the client gives up.
 */
export function useRealtimeConnection(
  auth: AuthResponse | null,
  onSyncEvent: (event: RealtimeEvent) => void,
) {
  const [status, setStatus] = useState<ConnectionStatus>('offline');
  const [realtime, setRealtime] = useState<RealtimeClient | null>(null);

  useEffect(() => {
    if (!auth) return;
    const client = new RealtimeClient();
    setRealtime(client);
    client.connect(
      () => api.getWsTicket().then(response => response.ticket),
      onSyncEvent,
      setStatus,
    );
    return () => {
      client.disconnect();
      setRealtime(null);
      setStatus('offline');
    };
  }, [auth, onSyncEvent]);

  const reconnect = useCallback(() => {
    realtime?.reconnect();
  }, [realtime]);

  return { status, realtime, reconnect };
}

import { useCallback, useState } from 'react';

export type ToastKind = 'error' | 'success';

export interface Toast {
  id: number;
  message: string;
  kind: ToastKind;
}

let nextId = 1;

export function useToasts() {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const push = useCallback((message: string, kind: ToastKind = 'error') => {
    const id = nextId++;
    setToasts(prev => [...prev, { id, message, kind }]);
    setTimeout(() => {
      setToasts(prev => prev.filter(toast => toast.id !== id));
    }, 4500);
  }, []);

  return { toasts, push };
}

export function ToastStack({ toasts }: { toasts: Toast[] }) {
  if (toasts.length === 0) return null;
  return (
    <div className="toast-stack">
      {toasts.map(toast => (
        <div key={toast.id} className={`toast ${toast.kind}`}>
          <span className="toast-icon">{toast.kind === 'success' ? '✓' : '!'}</span>
          {toast.message}
        </div>
      ))}
    </div>
  );
}

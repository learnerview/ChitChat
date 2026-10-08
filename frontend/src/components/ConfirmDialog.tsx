import { useEffect, useRef } from 'react';
import { TrashIcon } from './icons';
import Dialog from './Dialog';

interface ConfirmDialogProps {
  title: string;
  message: string;
  confirmLabel?: string;
  onConfirm: () => void;
  onClose: () => void;
}

/** Animated confirmation for destructive actions; closes on Escape/backdrop. */
export default function ConfirmDialog({
  title,
  message,
  confirmLabel = 'Delete',
  onConfirm,
  onClose,
}: ConfirmDialogProps) {
  const confirmRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    confirmRef.current?.focus();
  }, []);

  return (
    <Dialog onClose={onClose} className="confirm-dialog" role="alertdialog">
      <div className="confirm-icon">
        <TrashIcon size={22} />
      </div>
      <h3>{title}</h3>
      <p>{message}</p>
      <div className="dialog-actions">
        <button onClick={onClose}>Cancel</button>
        <button ref={confirmRef} className="danger" onClick={onConfirm}>
          {confirmLabel}
        </button>
      </div>
    </Dialog>
  );
}

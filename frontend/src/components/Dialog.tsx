import { useEffect, type ReactNode } from 'react';

interface DialogProps {
  children: ReactNode;
  onClose: () => void;
  /** Extra class on the dialog card (e.g. `wide`, `profile-card`). */
  className?: string;
  /** ARIA role; when set, `aria-modal` is applied too. */
  role?: 'dialog' | 'alertdialog';
}

/** Backdrop + centered dialog card; closes on Escape and backdrop press. */
export default function Dialog({ children, onClose, className, role }: DialogProps) {
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  return (
    <div className="dialog-backdrop" onMouseDown={onClose}>
      <div
        className={className ? `dialog ${className}` : 'dialog'}
        role={role}
        aria-modal={role ? true : undefined}
        onMouseDown={event => event.stopPropagation()}
      >
        {children}
      </div>
    </div>
  );
}

import { AlertTriangle } from 'lucide-react';

export default function ConfirmDialog({
  open = true,
  title = 'Confirmer cette action ?',
  message,
  confirmLabel = 'Confirmer',
  cancelLabel = 'Annuler',
  danger = false,
  pending = false,
  onCancel,
  onConfirm
}) {
  if (!open) return null;

  return (
    <div
      className="m3-modal-overlay"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !pending) onCancel?.();
      }}
    >
      <section
        className="m3-modal-box"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="m3-confirm-title"
        aria-describedby={message ? 'm3-confirm-message' : undefined}
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="m3-modal-heading">
          <h2 id="m3-confirm-title">{title}</h2>
        </div>
        {message && (
          <div className="m3-dialog-message" id="m3-confirm-message">
            {danger && <AlertTriangle size={22} aria-hidden="true" />}
            <p>{message}</p>
          </div>
        )}
        <div className="m3-modal-actions">
          <button
            type="button"
            className="btn btn-outline"
            onClick={onCancel}
            disabled={pending}
          >
            {cancelLabel}
          </button>
          <button
            type="button"
            className={`btn ${danger ? 'm3-btn-danger' : 'btn-primary'}`}
            onClick={onConfirm}
            disabled={pending}
          >
            {pending ? 'En cours…' : confirmLabel}
          </button>
        </div>
      </section>
    </div>
  );
}

export default function Modal({ open = true, title, onClose, children }) {
  if (!open) return null;

  return (
    <div
      className="m3-modal-overlay"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose?.();
      }}
    >
      <section
        className="m3-modal-box"
        role="dialog"
        aria-modal="true"
        aria-label={title || 'Fenêtre de dialogue'}
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="m3-modal-heading">
          <h2>{title}</h2>
        </div>
        {children}
      </section>
    </div>
  );
}

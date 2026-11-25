export default function StatusBadge({ status = 'PENDING' }) {
  return (
    <span className={`status-pill ${String(status).toLowerCase()}`}>
      {status}
    </span>
  );
}

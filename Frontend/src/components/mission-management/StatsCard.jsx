export default function StatsCard({ label, value, detail }) {
  return (
    <div className="m3-stat-card">
      <div className="muted">{label}</div>
      <strong>{value}</strong>
      {detail && <small className="muted">{detail}</small>}
    </div>
  );
}

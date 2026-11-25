import { Search, Plus, Bot } from 'lucide-react';
import { useState } from 'react';
import { useAgentCatalog } from '../../context/AgentCatalogContext';
import NodePalette from './NodePalette';

export default function AgentSelector({ onAdd, onAddLogic }) {
  const { agents, loading, error, retry } = useAgentCatalog();
  const mockAgents = agents.filter(a => a.status === 'ACTIVE' && a.versions.some(v => v.active));
  const [query, setQuery] = useState('');
  const filtered = mockAgents.filter((a) =>
    `${a.name} ${a.role}`.toLowerCase().includes(query.toLowerCase())
  );
  return (
    <section className="agents-panel">
      <div className="panel-head">
        <div>
          <strong>Agents disponibles</strong>
          <small>Catalogue fourni par M3</small>
        </div>
        <span className="count">{mockAgents.length}</span>
      </div>
      <label className="search-box compact">
        <Search size={16} />
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Rechercher un agent..."
        />
      </label>
      <div className="agent-list">
        {loading && <p>Chargement des agents…</p>}
        {error && <div role="alert"><p>{error}</p><button onClick={retry}>Réessayer</button></div>}
        {filtered.map((agent) => (
          <button
            key={agent.id}
            className="agent-choice"
            onClick={() => onAdd(agent.id)}
            title={`Ajouter ${agent.name}`}
          >
            <span className="agent-icon" style={{ background: agent.color }}>
              <Bot size={18} />
            </span>
            <span className="agent-choice-text">
              <strong>{agent.name}</strong>
              <small>{agent.role}</small>
            </span>
            <Plus size={16} className="plus-icon" />
          </button>
        ))}
        {!loading && !error && !filtered.length && <p className="empty-mini">Aucun agent trouvé.</p>}
      </div>
      <div className="panel-note">
        Seuls les agents actifs avec une version active peuvent être ajoutés. La source JSON ou M1 est configurée dans le backend.
      </div>
      {onAddLogic && <NodePalette onAdd={onAddLogic} />}
    </section>
  );
}

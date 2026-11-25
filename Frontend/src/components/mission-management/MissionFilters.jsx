import { Search, LayoutGrid, List } from 'lucide-react';

export default function MissionFilters({
  search,
  setSearch,
  mode,
  setMode,
  sort,
  setSort,
  view,
  setView
}) {
  return (
    <div className="m3-filters">
      <label className="search-box">
        <Search size={17} />
        <input
          aria-label="Rechercher une mission"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Rechercher une mission..."
        />
      </label>
      <select
        className="select"
        value={mode}
        onChange={(e) => setMode(e.target.value)}
        aria-label="Mode"
      >
        <option value="all">Tous les modes</option>
        <option value="manual">Manuel</option>
        <option value="automatic">Automatique IA</option>
      </select>
      <select
        className="select"
        value={sort}
        onChange={(e) => setSort(e.target.value)}
        aria-label="Tri"
      >
        <option value="modified">Dernière modification</option>
        <option value="created">Création</option>
        <option value="name">Nom A-Z</option>
      </select>
      <div className="m3-view-switch">
        <button
          className={view === 'grid' ? 'chosen' : ''}
          title="Grille"
          onClick={() => setView('grid')}
        >
          <LayoutGrid size={17} />
        </button>
        <button
          className={view === 'list' ? 'chosen' : ''}
          title="Liste"
          onClick={() => setView('list')}
        >
          <List size={17} />
        </button>
      </div>
    </div>
  );
}

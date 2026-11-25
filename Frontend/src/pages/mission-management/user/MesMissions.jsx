import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Plus, Search } from 'lucide-react';
import { useMissions } from '../../../context/MissionContext';
import MissionCard from '../../../components/mission-management/MissionCard';
import MissionFilters from '../../../components/mission-management/MissionFilters';
import RenameMissionDialog from '../../../components/mission-management/RenameMissionDialog';
import ConfirmDialog from '../../../components/common/ConfirmDialog';

export default function MesMissions() {
  const nav = useNavigate();
  const {
    missions,
    loading,
    error,
    loadMissions,
    executions,
    deleteMission,
    duplicateMission,
    updateMission
  } = useMissions();
  const [search, setSearch] = useState('');
  const [mode, setMode] = useState('all');
  const [sort, setSort] = useState('modified');
  const [view, setView] = useState('grid');
  const [rename, setRename] = useState(null);
  const [deleting, setDeleting] = useState(null);
  const [toast, setToast] = useState('');
  const [deletingPending, setDeletingPending] = useState(false);
  const reportActionError = async (err, fallback) => {
    if (err.status === 404) {
      await loadMissions();
      setToast('Cette mission n’est plus disponible. La liste a été actualisée.');
    } else if (err.status === 401) {
      setToast('Votre session a expiré. Reconnectez-vous pour continuer.');
    } else if (err.status === 403) {
      setToast('Vous n’avez pas l’autorisation de modifier cette mission.');
    } else {
      setToast(fallback);
    }
  };
  const shown = useMemo(
    () =>
      missions
        .filter(
          (m) =>
            (mode === 'all' || m.mode === mode) &&
            `${m.name} ${m.description || ''}`
              .toLowerCase()
              .includes(search.toLowerCase())
        )
        .sort((a, b) =>
          sort === 'name'
            ? a.name.localeCompare(b.name)
            : new Date(
                b[sort === 'created' ? 'createdAt' : 'updatedAt'] || b.updatedAt
              ) -
              new Date(
                a[sort === 'created' ? 'createdAt' : 'updatedAt'] || a.updatedAt
              )
        ),
    [missions, mode, search, sort]
  );
  const action = async (name, m) => {
    if (name === 'open' || name === 'edit') nav(`/studio/${m.id}`);
    if (name === 'rename') setRename(m);
    if (name === 'duplicate') {
      try {
        await duplicateMission(m);
        setToast('Mission dupliquée.');
      } catch (err) {
        await reportActionError(err, 'La duplication n’a pas abouti. Réessayez dans un instant.');
      }
    }
    if (name === 'delete') setDeleting(m);
    if (name === 'executions') nav(`/executions?mission=${m.id}`);
  };
  return (
    <div className="page page-missions">
      <div className="page-heading">
        <div>
          <div className="breadcrumb">M3 / Bibliothèque</div>
          <h1>
            Mes missions
            <span className="m3-counter">{missions.length}</span>
          </h1>
          <p>Retrouvez, organisez et gérez vos workflows multi-agents.</p>
        </div>
        <button
          className="btn btn-primary"
          onClick={() => nav('/missions/new')}
        >
          <Plus size={16} />
          Créer une mission
        </button>
      </div>
      {toast && (
        <div role="status" className="m3-toast" onClick={() => setToast('')}>
          {toast}
          <small>×</small>
        </div>
      )}
      {loading && <p role="status">Chargement des missions…</p>}
      {error && (
        <div role="alert">
          <p>Impossible de charger vos missions pour le moment. Réessayez dans un instant.</p>
          <button className="btn btn-outline" onClick={loadMissions}>
            Réessayer
          </button>
        </div>
      )}
      <MissionFilters
        {...{ search, setSearch, mode, setMode, sort, setSort, view, setView }}
      />
      <div className={view === 'list' ? 'm3-library-list' : 'm3-library-grid'}>
        {shown.map((m) => (
          <MissionCard
            key={m.id}
            mission={m}
            latest={executions.find((e) => e.missionId === m.id)}
            onOpen={() => nav(`/studio/${m.id}`)}
            onAction={action}
          />
        ))}
      </div>
      {!loading && !error && !shown.length && (
        <div className="m3-empty-panel">
          <Search size={30} />
          <h3>Aucune mission trouvée</h3>
          <p>Créez une mission ou ajustez vos filtres.</p>
          <button
            className="btn btn-outline"
            onClick={() => {
              setSearch('');
              setMode('all');
            }}
          >
            Réinitialiser les filtres
          </button>
        </div>
      )}
      <RenameMissionDialog
        mission={rename}
        onClose={() => setRename(null)}
        onSave={async (name) => {
          await updateMission(rename.id, { name });
          setRename(null);
          setToast('Mission renommée.');
        }}
      />
      <ConfirmDialog
        open={!!deleting}
        message={`Voulez-vous supprimer « ${deleting?.name || ''} » ? Cette action est définitive.`}
        pending={deletingPending}
        onCancel={() => { if (!deletingPending) setDeleting(null); }}
        onConfirm={async () => {
          if (deletingPending) return;
          setDeletingPending(true);
          try {
            await deleteMission(deleting.id);
            setDeleting(null);
            setToast('Mission supprimée.');
          } catch (err) {
            setDeleting(null);
            await reportActionError(
              err,
              err.status === 400 || err.status === 409
                ? 'Cette mission possède un historique d’exécution et ne peut pas être supprimée.'
                : 'La suppression n’a pas abouti. Réessayez dans un instant.'
            );
          } finally {
            setDeletingPending(false);
          }
        }}
      />
    </div>
  );
}

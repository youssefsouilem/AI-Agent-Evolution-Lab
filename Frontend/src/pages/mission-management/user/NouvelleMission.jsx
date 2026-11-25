import { useAgentCatalog } from '../../../context/AgentCatalogContext';
import {
  missionTemplates,
  createTemplateWorkflow
} from '../../../data/missionTemplates';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowRight, Bot, Sparkles, Workflow, X } from 'lucide-react';
import { useMissions } from '../../../context/MissionContext';
import previewManual from '../../../assets/preview-manual.svg';
import previewAutomatic from '../../../assets/preview-automatic.svg';

export default function NouvelleMission() {
  const {
    agents,
    loading: agentsLoading,
    error: agentsError
  } = useAgentCatalog();
  const navigate = useNavigate();
  const { createMission } = useMissions();

  const [choice, setChoice] = useState('manual');
  const [templateId, setTemplateId] = useState(missionTemplates[0].id);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  async function submit(event) {
    event.preventDefault();

    if (!name.trim() || loading) {
      return;
    }

    setLoading(true);
    setError('');

    try {
      if (choice === 'template' && (agentsLoading || agentsError))
        throw new Error(agentsError || 'Chargement du catalogue…');
      const mission = await createMission({
        name: name.trim(),
        description: description.trim(),
        mode: choice === 'template' ? 'manual' : choice,
        initialWorkflow:
          choice === 'template'
            ? createTemplateWorkflow(templateId, agents)
            : undefined
      });

      navigate(`/studio/${mission.id}`);
    } catch (err) {
      console.error('Erreur lors de la création :', err);

      setError(err.message || 'Impossible de créer la mission.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div
      className="mission-create-backdrop"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget && !loading) {
          navigate('/missions');
        }
      }}
    >
      <section
        className="mission-create-dialog"
        role="dialog"
        aria-modal="true"
        aria-label="Créer une mission"
      >
        <button
          className="mission-create-close"
          type="button"
          onClick={() => navigate('/missions')}
          aria-label="Fermer"
          disabled={loading}
        >
          <X size={19} />
        </button>

        <div className="mission-create-left">
          <div className="mission-create-heading">
            <span className="mission-create-eyebrow">
              <Sparkles size={15} />
              M3 · MISSION STUDIO
            </span>

            <h1>Créer à partir de zéro</h1>

            <p>
              Choisissez comment construire votre mission avec les agents
              disponibles dans M1.
            </p>
          </div>

          <form onSubmit={submit} className="mission-create-form">
            <div className="mission-create-label">
              Choisissez un type de création
            </div>

            <div className="mission-create-options">
              <button
                type="button"
                className={`mission-create-option ${
                  choice === 'manual' ? 'selected' : ''
                }`}
                onClick={() => setChoice('manual')}
                aria-pressed={choice === 'manual'}
                disabled={loading}
              >
                <span className="mission-option-icon">
                  <Workflow size={22} />
                </span>

                <strong>Flux de travail</strong>

                <small>Construire manuellement avec des agents M1</small>
              </button>

              <button
                type="button"
                className={`mission-create-option ${
                  choice === 'automatic' ? 'selected' : ''
                }`}
                onClick={() => setChoice('automatic')}
                aria-pressed={choice === 'automatic'}
                disabled={loading}
              >
                <span className="mission-option-icon automatic">
                  <Bot size={22} />
                </span>

                <strong>Automatique IA</strong>

                <small>Décrire un objectif pour l’AI Router</small>
              </button>
            </div>

            <button
              type="button"
              className={`btn ${choice === 'template' ? 'btn-primary' : 'btn-outline'}`}
              onClick={() => setChoice('template')}
              disabled={loading}
            >
              Utiliser un modèle
            </button>
            {choice === 'template' && (
              <label className="mission-create-label">
                Modèle de démonstration
                <select
                  className="field"
                  value={templateId}
                  onChange={(event) => setTemplateId(event.target.value)}
                  disabled={loading}
                >
                  {missionTemplates.map((template) => (
                    <option key={template.id} value={template.id}>
                      {template.name}
                    </option>
                  ))}
                </select>
                <small>
                  {
                    missionTemplates.find(
                      (template) => template.id === templateId
                    ).description
                  }{' '}
                  Agents issus du catalogue configuré dans M3.
                </small>
              </label>
            )}
            <div className="mission-create-separator" />

            <label htmlFor="new-mission-name" className="mission-create-label">
              Nom de la mission
              <span className="required">*</span>
            </label>

            <input
              id="new-mission-name"
              className="mission-create-input"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Donnez un nom à votre mission"
              maxLength={80}
              required
              autoFocus
              disabled={loading}
            />

            <label
              htmlFor="new-mission-description"
              className="mission-create-label"
            >
              Description
              <span className="optional">(facultatif)</span>
            </label>

            <textarea
              id="new-mission-description"
              className="mission-create-textarea"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Décrivez votre mission..."
              rows={3}
              disabled={loading}
            />

            <p className="mission-create-hint">
              {choice === 'template'
                ? 'Le modèle s’ouvrira dans le canvas pour être personnalisé.'
                : choice === 'manual'
                  ? 'Après la création, vous ouvrirez un canvas vide pour ajouter et connecter des agents.'
                  : 'Après la création, le chat pourra demander une proposition au service AI Router configuré.'}
            </p>

            {error && (
              <p
                role="alert"
                style={{
                  color: '#dc2626',
                  fontSize: '13px',
                  marginTop: '10px'
                }}
              >
                {error}
              </p>
            )}

            <div className="mission-create-footer">
              <button
                type="button"
                className="btn btn-outline"
                onClick={() => navigate('/missions')}
                disabled={loading}
              >
                Annuler
              </button>

              <button
                type="submit"
                className="btn btn-primary"
                disabled={!name.trim() || loading}
              >
                {loading ? 'Création en cours...' : 'Créer et continuer'}

                {!loading && <ArrowRight size={16} />}
              </button>
            </div>
          </form>
        </div>

        <aside className="mission-create-preview">
          <div className="mission-preview-label">
            {choice === 'manual' ? 'FLUX DE TRAVAIL' : 'AUTOMATIQUE IA'}
          </div>

          <h2>
            {choice === 'manual'
              ? 'Construisez visuellement votre workflow'
              : 'Décrivez votre objectif, puis ajustez le workflow'}
          </h2>

          <p>
            {choice === 'manual'
              ? 'Ajoutez les agents existants, définissez leurs tâches et connectez les étapes dans un éditeur visuel.'
              : 'Un grand chat s’ouvre, puis Studio Chat et le canvas apparaissent côte à côte après votre message.'}
          </p>

          <div className="mission-preview-image-wrap">
            <img
              src={choice === 'manual' ? previewManual : previewAutomatic}
              alt={
                choice === 'manual'
                  ? 'Aperçu original du workflow manuel'
                  : 'Aperçu original du Studio Chat et du canvas'
              }
            />
          </div>

          <span className="mission-preview-caption">
            Aperçu illustratif de l’interface
          </span>
        </aside>
      </section>
    </div>
  );
}

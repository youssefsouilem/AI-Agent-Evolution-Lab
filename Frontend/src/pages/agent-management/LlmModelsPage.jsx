import { useCallback, useEffect, useState } from 'react'
import { Check, Copy, Cpu, Info, RefreshCw, Sparkles } from 'lucide-react'
import { listLlmModels } from '../../services/agentApi'
import { ErrorBanner, Loading, EmptyState } from '../../components/agent-management/ui'
import '../../components/agent-management/agent-management.css'

export default function LlmModelsPage() {
  const [providers, setProviders] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [copiedId, setCopiedId] = useState(null)

  const loadModels = useCallback(async () => {
    setLoading(true)
    try {
      const data = await listLlmModels()
      setProviders(Array.isArray(data) ? data : [])
      setError(null)
    } catch (err) {
      setError(err)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadModels()
  }, [loadModels])

  const copyToClipboard = (id) => {
    navigator.clipboard.writeText(id)
    setCopiedId(id)
    setTimeout(() => setCopiedId(null), 2000)
  }

  return (
    <div>
      <div className="m1-breadcrumb">
        Agent Studio <span>/</span> Modèles LLM
      </div>

      <div className="m1-page-head">
        <div>
          <h1>Modèles LLM</h1>
          <p>Catalogue des modèles de langage configurés et utilisables par vos agents.</p>
        </div>
        <button type="button" className="m1-ghost" onClick={loadModels} disabled={loading}>
          <RefreshCw size={14} className={loading ? 'animate-spin' : ''} />
          Actualiser
        </button>
      </div>

      {error && <ErrorBanner error={error} retry={loadModels} />}

      {loading ? (
        <Loading label="Chargement des modèles..." />
      ) : providers.length === 0 ? (
        <EmptyState
          text="Aucun fournisseur de modèle configuré."
          subtext="Vérifiez les variables d'environnement des clés API côté backend."
        />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
          {providers.map((provider) => (
            <div
              key={provider.provider}
              style={{
                background: 'white',
                border: '1px solid var(--border)',
                borderRadius: 14,
                padding: '24px',
                boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
              }}
            >
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  marginBottom: 16,
                  paddingBottom: 16,
                  borderBottom: '1px solid var(--border)',
                  flexWrap: 'wrap',
                  gap: 12,
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  <div
                    style={{
                      width: 40,
                      height: 40,
                      borderRadius: 10,
                      background: 'var(--lavender)',
                      color: 'var(--purple)',
                      display: 'grid',
                      placeItems: 'center',
                    }}
                  >
                    <Cpu size={20} />
                  </div>
                  <div>
                    <h2 style={{ margin: 0, fontSize: 18, color: '#1b1b35', fontWeight: 700 }}>
                      {provider.label || provider.provider}
                    </h2>
                    <small style={{ color: 'var(--muted)', fontSize: 12 }}>
                      Fournisseur : {provider.provider}
                    </small>
                  </div>
                </div>

                <div>
                  {provider.available ? (
                    <span
                      style={{
                        background: '#ecfdf5',
                        color: '#059669',
                        border: '1px solid #a7f3d0',
                        padding: '4px 10px',
                        borderRadius: 6,
                        fontSize: 12,
                        fontWeight: 600,
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: 6,
                      }}
                    >
                      <Check size={13} /> Disponible
                    </span>
                  ) : (
                    <span
                      style={{
                        background: '#f3f4f6',
                        color: '#6b7280',
                        border: '1px solid #e5e7eb',
                        padding: '4px 10px',
                        borderRadius: 6,
                        fontSize: 12,
                        fontWeight: 600,
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: 6,
                      }}
                      title={provider.reason || 'Clé API non configurée'}
                    >
                      <Info size={13} /> Non configuré
                    </span>
                  )}
                </div>
              </div>

              {!provider.available && provider.reason && (
                <div
                  style={{
                    background: '#fffbeb',
                    border: '1px solid #fef3c7',
                    borderRadius: 8,
                    padding: '10px 14px',
                    fontSize: 12,
                    color: '#b45309',
                    marginBottom: 16,
                  }}
                >
                  {provider.reason}
                </div>
              )}

              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
                  gap: 14,
                }}
              >
                {(provider.models || []).map((model) => (
                  <div
                    key={model.id}
                    style={{
                      border: '1px solid var(--border)',
                      borderRadius: 10,
                      padding: '14px 16px',
                      background: '#fcfcff',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: 8,
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <strong style={{ fontSize: 14, color: '#1b1b35' }}>{model.label}</strong>
                      {model.recommended && (
                        <span
                          style={{
                            background: '#ede8ff',
                            color: 'var(--purple)',
                            border: '1px solid #d5cef7',
                            padding: '2px 8px',
                            borderRadius: 6,
                            fontSize: 11,
                            fontWeight: 700,
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: 4,
                          }}
                        >
                          <Sparkles size={11} /> Recommandé
                        </span>
                      )}
                    </div>

                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                      <code
                        style={{
                          fontSize: 12,
                          background: 'white',
                          border: '1px solid #e5e7eb',
                          padding: '3px 8px',
                          borderRadius: 6,
                          color: '#4b5563',
                        }}
                      >
                        {model.id}
                      </code>

                      <button
                        type="button"
                        className="m1-ghost-sm"
                        onClick={() => copyToClipboard(model.id)}
                        title="Copier l'identifiant"
                      >
                        {copiedId === model.id ? (
                          <>
                            <Check size={12} style={{ color: '#059669' }} /> Copié !
                          </>
                        ) : (
                          <>
                            <Copy size={12} /> Copier
                          </>
                        )}
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

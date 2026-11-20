import { useCallback, useEffect, useState } from 'react'
import { CheckCircle2, RefreshCw, XCircle } from 'lucide-react'
import { getM1Health } from '../../services/agentApi'
import '../../components/agent-management/agent-management.css'

const MICROSERVICES = [
  {
    id: 'M1',
    name: 'Agent Management (M1)',
    description: 'Gestion des agents IA, versionnage immutable, exécution et persistance des configurations.',
    port: 8081,
    isM1: true,
  },
  {
    id: 'M2',
    name: 'MCP & Sécurité (M2)',
    description: 'Catalogue des outils externes, passerelles MCP et contrôles de sécurité.',
    port: 8082,
  },
  {
    id: 'M3',
    name: 'Mission Management (M3)',
    description: 'Orchestration des missions et des workflows multi-agents.',
    port: 8083,
  },
  {
    id: 'M4',
    name: 'Benchmark & Safety (M4)',
    description: 'Évaluation des modèles, tests de sécurité et benchmarks standardisés.',
    port: 8084,
  },
  {
    id: 'M5',
    name: 'Optimization · Optuna (M5)',
    description: 'Optimisation automatique des hyperparamètres et des prompts systèmes.',
    port: 8085,
  },
  {
    id: 'M6',
    name: 'Anomaly Monitoring (M6)',
    description: 'Surveillance en temps réel et détection proactive des anomalies de comportement.',
    port: 8086,
  },
  {
    id: 'M7',
    name: 'Event Logging & Analytics (M7)',
    description: 'Journalisation durable des événements, métriques d exécution et audit.',
    port: 8087,
  },
]

export default function AdminServicesPage() {
  const [m1Health, setM1Health] = useState({ status: 'CHECKING' })
  const [loading, setLoading] = useState(true)

  const checkHealth = useCallback(async () => {
    setLoading(true)
    try {
      const res = await getM1Health()
      setM1Health(res)
    } catch {
      setM1Health({ status: 'UNREACHABLE' })
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    checkHealth()
  }, [checkHealth])

  const isMock = import.meta.env.VITE_USE_MOCK === 'true'

  const renderBadge = (service) => {
    if (service.isM1) {
      if (loading) {
        return (
          <span className="m1-badge" style={{ background: '#f3f4f6', color: '#6b7280' }}>
            Vérification...
          </span>
        )
      }
      if (m1Health.status === 'UP') {
        return (
          <span
            className="m1-badge"
            style={{ background: '#ecfdf5', color: '#059669', border: '1px solid #a7f3d0' }}
          >
            <CheckCircle2 size={12} style={{ marginRight: 4 }} />
            UP {isMock ? '(démo)' : ''}
          </span>
        )
      }
      if (m1Health.status === 'DOWN') {
        return (
          <span
            className="m1-badge"
            style={{ background: '#fef2f2', color: '#dc2626', border: '1px solid #fecaca' }}
          >
            <XCircle size={12} style={{ marginRight: 4 }} />
            DOWN
          </span>
        )
      }
      return (
        <span
          className="m1-badge"
          style={{ background: '#f3f4f6', color: '#6b7280', border: '1px solid #e5e7eb' }}
        >
          Injoignable
        </span>
      )
    }

    return (
      <span
        className="m1-badge"
        style={{ background: '#f8fafc', color: '#94a3b8', border: '1px solid #e2e8f0' }}
      >
        Non configuré
      </span>
    )
  }

  return (
    <div>
      <div className="m1-breadcrumb">
        Administration <span>/</span> Supervision
      </div>

      <div className="m1-page-head">
        <div>
          <h1>Supervision des services</h1>
          <p>État et disponibilité des microservices de la plateforme AI Agent Evolution Lab.</p>
        </div>
        <button
          type="button"
          className="m1-primary"
          onClick={checkHealth}
          disabled={loading}
        >
          <RefreshCw size={14} className={loading ? 'animate-spin' : ''} />
          {loading ? 'Vérification...' : 'Actualiser'}
        </button>
      </div>

      <div className="m1-table-wrapper" style={{ marginTop: 24 }}>
        <table className="m1-table">
          <thead>
            <tr>
              <th style={{ width: '80px' }}>Service</th>
              <th>Nom & Rôle</th>
              <th>Port cible</th>
              <th>Statut santé</th>
              <th style={{ textAlign: 'right' }}>Détails</th>
            </tr>
          </thead>
          <tbody>
            {MICROSERVICES.map((srv) => (
              <tr key={srv.id}>
                <td>
                  <span className="m1-v-tag">{srv.id}</span>
                </td>
                <td>
                  <strong>{srv.name}</strong>
                  <p style={{ margin: '4px 0 0', color: 'var(--muted)', fontSize: 12 }}>
                    {srv.description}
                  </p>
                </td>
                <td>
                  <code style={{ fontSize: 12, background: '#f1f0fb', padding: '2px 6px', borderRadius: 4 }}>
                    :{srv.port}
                  </code>
                </td>
                <td>{renderBadge(srv)}</td>
                <td style={{ textAlign: 'right', color: 'var(--muted)', fontSize: 12 }}>
                  {srv.isM1 ? (
                    loading ? (
                      '...'
                    ) : m1Health.status === 'UP' ? (
                      <span style={{ color: '#059669' }}>Opérationnel</span>
                    ) : m1Health.status === 'DOWN' ? (
                      <span style={{ color: '#dc2626' }}>Actuator DOWN</span>
                    ) : (
                      'Port injoignable'
                    )
                  ) : (
                    'En attente du proxy'
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

import { useAuth } from '../components/common/AuthContext'
import { Link } from 'react-router-dom'
import {
  Activity,
  ArrowRight,
  Bot,
  Compass,
  LineChart,
  Lock,
  Sparkles,
} from 'lucide-react'
import '../components/agent-management/agent-management.css'

const MODULES = [
  {
    id: 'agents',
    title: 'Agents',
    code: 'M1',
    description: 'Créez, configurez et versionnez vos agents IA. Exécution directe et gestion des hyperparamètres.',
    icon: Bot,
    to: '/agents',
    status: 'available',
    statusLabel: 'Disponible',
  },
  {
    id: 'mcp',
    title: 'Sécurité MCP',
    code: 'M2',
    description: 'Protocoles de sécurité et intégration des outils tiers via le Model Context Protocol.',
    icon: Lock,
    status: 'coming_soon',
    statusLabel: 'Bientôt',
  },
  {
    id: 'missions',
    title: 'Missions',
    code: 'M3',
    description: 'Orchestrez des workflows multi-agents complexes pour résoudre des tâches collaboratives.',
    icon: Compass,
    to: '/missions/overview',
    status: 'available',
    statusLabel: 'Disponible',
  },
  {
    id: 'benchmark',
    title: 'Benchmark & Safety',
    code: 'M4',
    description: 'Évaluez les performances, la conformité et la sécurité de vos agents IA.',
    icon: LineChart,
    status: 'coming_soon',
    statusLabel: 'Bientôt',
  },
  {
    id: 'optimization',
    title: 'Optimisation',
    code: 'M5',
    description: 'Optimisation bayésienne des prompts et hyperparamètres via Optuna.',
    icon: Sparkles,
    status: 'coming_soon',
    statusLabel: 'Bientôt',
  },
  {
    id: 'monitoring',
    title: 'Monitoring',
    code: 'M6',
    description: 'Surveillance continue des exécutions, détection d anomalies et alertes.',
    icon: Activity,
    to: '/monitoring',
    status: 'available',
    statusLabel: 'Disponible',
  },
]

export default function HomePage() {
  const { isAdmin } = useAuth()
  return (
    <div>
      <div className="m1-breadcrumb">Plateforme <span>/</span> Accueil</div>

      <div className="m1-page-head">
        <div>
          <h1>Bienvenue dans Evolution Lab</h1>
          <p>La plateforme collaborative pour concevoir, évaluer et optimiser vos agents IA.</p>
        </div>
      </div>

      <div className="m1-card-grid" style={{ marginTop: 24 }}>
        {MODULES.map((mod) => {
          const Icon = mod.icon
          const isAvailable = mod.status === 'available'

          const CardContent = (
            <div
              className="m1-agent-card"
              style={{
                opacity: isAvailable ? 1 : 0.75,
                borderColor: isAvailable ? '#d5cef7' : 'var(--border)',
              }}
            >
              <div className="m1-card-top">
                <div
                  className="m1-card-icon"
                  style={{
                    background: isAvailable ? 'var(--lavender)' : '#f3f4f6',
                    color: isAvailable ? 'var(--purple)' : '#9ca3af',
                  }}
                >
                  <Icon size={20} />
                </div>
                <span
                  className="m1-badge"
                  style={{
                    background: isAvailable ? '#ecfdf5' : '#f3f4f6',
                    color: isAvailable ? '#059669' : '#6b7280',
                    border: isAvailable ? '1px solid #a7f3d0' : '1px solid #e5e7eb',
                  }}
                >
                  {mod.statusLabel}
                </span>
              </div>

              <div className="m1-card-title">
                <strong>{mod.title}</strong>
                <span className="m1-v-tag">{mod.code}</span>
              </div>

              <p>{mod.description}</p>

              <div className="m1-card-footer">
                {isAvailable ? (
                  <span className="m1-link-button">
                    Accéder au module <ArrowRight size={14} />
                  </span>
                ) : (
                  <span style={{ fontSize: 12, color: 'var(--muted)' }}>Module en cours de développement</span>
                )}
              </div>
            </div>
          )

          return isAvailable ? (
            <Link key={mod.id} to={mod.id === 'missions' && isAdmin ? '/admin' : mod.to} style={{ textDecoration: 'none', color: 'inherit' }}>
              {CardContent}
            </Link>
          ) : (
            <div key={mod.id}>{CardContent}</div>
          )
        })}
      </div>
    </div>
  )
}

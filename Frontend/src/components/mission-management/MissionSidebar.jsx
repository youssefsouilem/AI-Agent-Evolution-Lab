import { useEffect, useRef, useState } from 'react';
import { NavLink, useLocation, useNavigate } from 'react-router-dom';
import {
  Blocks,
  Clock3,
  Home,
  LayoutDashboard,
  PlayCircle,
  Workflow,
  Settings2,
  ShieldCheck,
  Activity,
  ChevronDown,
  Check,
  UserRound
} from 'lucide-react';
import { platformModules } from '../../data/platformModules';

const userNav = [
  ['/missions/overview', 'Accueil', Home],
  ['/missions', 'Mes missions', LayoutDashboard],
  ['/studio', 'Mission Studio', Workflow],
  ['/executions', 'Exécutions', PlayCircle],
  ['/history', 'Historique', Clock3]
];
const adminNav = [
  ['/admin', 'Vue d’ensemble', LayoutDashboard],
  ['/admin/missions', 'Toutes les missions', Workflow],
  ['/admin/executions', 'Supervision des exécutions', Activity],
  ['/admin/settings', 'Paramètres d’orchestration', Settings2],
  ['/admin/access', 'Autorisations', ShieldCheck]
];
const isMissionPath = (path) =>
  /^\/(missions|studio|executions|history|admin)(\/|$)/.test(path);

export default function MissionSidebar({ role, setRole }) {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const [expanded, setExpanded] = useState(() => isMissionPath(pathname));
  const account = useRef(null);
  useEffect(() => {
    if (isMissionPath(pathname)) setExpanded(true);
    if (account.current) account.current.open = false;
  }, [pathname]);
  const chooseRole = (value) => {
    setRole(value);
    if (account.current) account.current.open = false;
    setExpanded(true);
    navigate(value === 'admin' ? '/admin' : '/missions/overview');
  };
  const childLink = ([path, title, Icon]) => (
    <NavLink
      key={path}
      to={path}
      end={['/missions', '/missions/overview', '/admin'].includes(path)}
      className={({ isActive }) =>
        `platform-sub-link ${isActive ? 'active' : ''}`
      }
    >
      <Icon size={16} />
      <span>{title}</span>
    </NavLink>
  );
  return (
    <aside className="sidebar platform-sidebar">
      <div className="brand">
        <span className="brand-mark">
          <Blocks size={23} />
        </span>
        <div>
          <strong>Evolution Lab</strong>
          <small>AI AGENT EVOLUTION LAB</small>
        </div>
      </div>
      <nav className="platform-nav" aria-label="Navigation principale">
        <NavLink
          to="/"
          end
          className={({ isActive }) =>
            `platform-module-row ${isActive ? 'active' : ''}`
          }
        >
          <Home size={20} />
          <span>Accueil</span>
        </NavLink>
        {platformModules.map((module) =>
          module.key === 'missions' ? (
            <div className="platform-mission-group" key={module.key}>
              <button
                type="button"
                className={`platform-module-row ${isMissionPath(pathname) ? 'active' : ''}`}
                aria-expanded={expanded}
                aria-controls="missions-navigation"
                onClick={() => setExpanded((value) => !value)}
              >
                <Workflow size={20} />
                <span>Missions</span>
                <ChevronDown
                  size={17}
                  className={expanded ? 'chevron expanded' : 'chevron'}
                />
              </button>
              {expanded && (
                <div id="missions-navigation" className="platform-sub-nav">
                  {(role === 'admin' ? adminNav : userNav).map(childLink)}
                </div>
              )}
            </div>
          ) : (
            <button
              key={module.key}
              type="button"
              className="platform-module-row platform-module-unavailable"
              disabled
              title="Disponible après intégration"
            >
              <module.Icon size={20} />
              <span>{module.label}</span>
            </button>
          )
        )}
      </nav>
      <div className="sidebar-footer platform-account-footer">
        <details
          ref={account}
          className="platform-account"
          onKeyDown={(event) => {
            if (event.key === 'Escape') account.current.open = false;
          }}
        >
          <summary aria-label="Compte Souleima">
            <span className="avatar">S</span>
            <span className="platform-account-name">
              <strong>Souleima</strong>
              <small>
                {role === 'admin' ? 'Administrateur' : 'Utilisateur'}
              </small>
            </span>
            <ChevronDown size={17} />
          </summary>
          <div
            className="platform-account-options"
            role="group"
            aria-label="Choisir un rôle de démonstration"
          >
            <p>Espace de démonstration</p>
            <button type="button" onClick={() => chooseRole('user')}>
              <UserRound size={17} /> Utilisateur{' '}
              {role === 'user' && <Check size={16} />}
            </button>
            <button type="button" onClick={() => chooseRole('admin')}>
              <ShieldCheck size={17} /> Administrateur{' '}
              {role === 'admin' && <Check size={16} />}
            </button>
          </div>
        </details>
      </div>
    </aside>
  );
}

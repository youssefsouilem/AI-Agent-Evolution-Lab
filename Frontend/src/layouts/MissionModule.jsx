import { Outlet } from 'react-router-dom';
import { AgentCatalogProvider } from '../context/AgentCatalogContext';
import { MissionProvider } from '../context/MissionContext';
import '../styles/mission-management.css';

export default function MissionModule() {
  return (
    <MissionProvider>
      <AgentCatalogProvider>
        <div className="m3-module">
          <Outlet />
        </div>
      </AgentCatalogProvider>
    </MissionProvider>
  );
}

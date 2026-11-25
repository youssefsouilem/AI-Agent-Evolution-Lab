import { useCallback } from 'react';
import {
  ReactFlow,
  ReactFlowProvider,
  Background,
  MiniMap,
  Handle,
  Position,
  addEdge,
  applyNodeChanges,
  applyEdgeChanges,
  useReactFlow
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { Bot, RotateCcw, Scan, ZoomIn, ZoomOut } from 'lucide-react';
import StepInspector from './StepInspector';
import ConditionNode from './ConditionNode';
import { Play, Flag } from 'lucide-react';

function AgentNode({ data, selected }) {
  return (
    <div className={`flow-agent ${selected ? 'selected' : ''}`}>
      <Handle type="target" position={Position.Left} />
      <div className="flow-agent-top">
        <span className="flow-agent-icon">
          <Bot size={19} />
        </span>
        <div>
          <span className="flow-agent-title">{data.label}</span>
          <span className="flow-agent-role">{data.role}</span>
        </div>
        <span className="step-number">
          {String(data.order || 1).padStart(2, '0')}
        </span>
      </div>
      <div className="flow-agent-instruction">
        {data.instruction || 'Configurer les instructions de cette étape'}
      </div>
      <div className="flow-agent-bottom">
        <span className="flow-dot" />
        AGENT M1 · DÉMO
      </div>
      <Handle type="source" position={Position.Right} />
    </div>
  );
}
function StartNode() {
  return (
    <div className="m3-control-node">
      <Play size={15} />
      Début
      <Handle type="source" position={Position.Right} />
    </div>
  );
}
function EndNode() {
  return (
    <div className="m3-control-node end">
      <Handle type="target" position={Position.Left} />
      <Flag size={15} />
      Fin
    </div>
  );
}
const nodeTypes = {
  agentNode: AgentNode,
  conditionNode: ConditionNode,
  startNode: StartNode,
  endNode: EndNode
};
function Inner({
  nodes = [],
  setNodes = () => {},
  edges = [],
  setEdges = () => {},
  onSelectNode,
  readOnly = false,
  onReset,
  activeNodeId
}) {
  const flow = useReactFlow();
  const onNodesChange = useCallback(
    (changes) => {
      if (!readOnly) setNodes((old) => applyNodeChanges(changes, old));
    },
    [readOnly, setNodes]
  );
  const onEdgesChange = useCallback(
    (changes) => {
      if (!readOnly) setEdges((old) => applyEdgeChanges(changes, old));
    },
    [readOnly, setEdges]
  );
  const onConnect = useCallback(
    (e) => {
      if (!readOnly && e.source !== e.target)
        setEdges((old) =>
          addEdge(
            (() => {
              const source = nodes.find((node) => node.id === e.source);
              const target = nodes.find((node) => node.id === e.target);
              const visited = new Set();
              const reaches = (id) => {
                if (id === e.source) return true;
                if (visited.has(id)) return false;
                visited.add(id);
                return old
                  .filter(
                    (edge) => !edge.data?.correction && edge.source === id
                  )
                  .some((edge) => reaches(edge.target));
              };
              const correction =
                e.sourceHandle === 'no' &&
                source?.type === 'conditionNode' &&
                target?.type === 'agentNode' &&
                reaches(e.target);
              return {
                ...e,
                animated: true,
                label: correction ? 'Non · correction limitée' : undefined,
                data: { correction },
                style: {
                  stroke: correction ? '#b7791f' : '#9284e6',
                  strokeWidth: 2
                }
              };
            })(),
            old
          )
        );
    },
    [readOnly, setEdges, nodes]
  );
  const displayedNodes = nodes.map((n) => ({
    ...n,
    className: activeNodeId === n.id ? 'm3-active-flow-node' : ''
  }));
  return (
    <div className="canvas-wrap">
      <div className="canvas-label">
        <span className="live-dot" />
        VISUAL WORKFLOW
        <span className="demo-tag">SIMULATION</span>
      </div>
      <ReactFlow
        nodes={displayedNodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onConnect={onConnect}
        onNodeClick={(_, node) => onSelectNode?.(node.id)}
        onPaneClick={() => onSelectNode?.(null)}
        nodeTypes={nodeTypes}
        fitView
        fitViewOptions={{ padding: 0.35 }}
        defaultEdgeOptions={{ style: { stroke: '#9284e6', strokeWidth: 2 } }}
        nodesConnectable={!readOnly}
        nodesDraggable={!readOnly}
        elementsSelectable={!readOnly}
        deleteKeyCode={readOnly ? null : ['Backspace', 'Delete']}
        proOptions={{ hideAttribution: true }}
      >
        <Background color="#e9e9f1" gap={23} size={1.3} />
        <MiniMap
          zoomable
          pannable
          style={{
            border: '1px solid #eeeef4',
            borderRadius: 10,
            width: 128,
            height: 80
          }}
          nodeColor="#b6aef5"
        />
      </ReactFlow>
      <div className="canvas-tools">
        <button
          title="Zoom arrière"
          onClick={() => flow.zoomOut({ duration: 180 })}
        >
          <ZoomOut size={17} />
        </button>
        <button
          title="Centrer"
          onClick={() => flow.fitView({ duration: 230, padding: 0.3 })}
        >
          <Scan size={17} />
        </button>
        <button
          title="Zoom avant"
          onClick={() => flow.zoomIn({ duration: 180 })}
        >
          <ZoomIn size={17} />
        </button>
        {!readOnly && (
          <button title="Réinitialiser" onClick={onReset}>
            <RotateCcw size={16} />
          </button>
        )}
      </div>
      {!nodes.length && (
        <div className="canvas-empty">
          <div className="canvas-empty-icon">
            <Bot size={26} />
          </div>
          <strong>Votre workflow commence ici</strong>
          <p>
            Ajoutez des agents depuis le catalogue, puis reliez leurs blocs.
          </p>
        </div>
      )}
    </div>
  );
}
export default function WorkflowCanvas(props) {
  return (
    <ReactFlowProvider>
      <Inner {...props} />
    </ReactFlowProvider>
  );
}
export { StepInspector as StepSettings };

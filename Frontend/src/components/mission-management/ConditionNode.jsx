import { Handle, Position } from '@xyflow/react';
import { GitBranch } from 'lucide-react';

export default function ConditionNode({ data, selected }) {
  return (
    <div className={`m3-condition-node ${selected ? 'selected' : ''}`}>
      <Handle type="target" position={Position.Left} />
      <div className="m3-condition-inner">
        <GitBranch size={20} />
        <strong>{data.label || 'Condition If / Else'}</strong>
        <small>
          {data.variable || 'score'}
          {data.operator || '>='}
          {data.expected ?? 0.8}
        </small>
        <small>Données fictives :{data.sampleValue ?? 0.85}</small>
      </div>
      <div className="m3-condition-output yes">
        Oui
        <Handle id="yes" type="source" position={Position.Right} />
      </div>
      <div className="m3-condition-output no">
        Non
        <Handle id="no" type="source" position={Position.Bottom} />
      </div>
    </div>
  );
}

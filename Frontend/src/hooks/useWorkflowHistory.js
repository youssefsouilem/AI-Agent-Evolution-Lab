import { useCallback, useReducer } from 'react';

export function workflowHistoryReducer(state, action) {
  if (action.type === 'replace')
    return { graph: action.value, past: [], future: [] };
  if (action.type === 'commit') {
    const graph =
      typeof action.value === 'function'
        ? action.value(state.graph)
        : action.value;
    if (JSON.stringify(graph) === JSON.stringify(state.graph)) return state;
    return { graph, past: [...state.past.slice(-29), state.graph], future: [] };
  }
  if (action.type === 'undo' && state.past.length) {
    return {
      graph: state.past.at(-1),
      past: state.past.slice(0, -1),
      future: [state.graph, ...state.future]
    };
  }
  if (action.type === 'redo' && state.future.length) {
    return {
      graph: state.future[0],
      past: [...state.past, state.graph],
      future: state.future.slice(1)
    };
  }
  return state;
}

export function useWorkflowHistory(initialNodes = [], initialEdges = []) {
  const [state, dispatch] = useReducer(workflowHistoryReducer, {
    graph: { nodes: initialNodes, edges: initialEdges },
    past: [],
    future: []
  });
  const commit = useCallback(
    (value) => dispatch({ type: 'commit', value }),
    []
  );
  const undo = useCallback(() => dispatch({ type: 'undo' }), []);
  const redo = useCallback(() => dispatch({ type: 'redo' }), []);
  const replace = useCallback(
    (value) => dispatch({ type: 'replace', value }),
    []
  );
  return {
    graph: state.graph,
    commit,
    undo,
    redo,
    replace,
    canUndo: state.past.length > 0,
    canRedo: state.future.length > 0
  };
}

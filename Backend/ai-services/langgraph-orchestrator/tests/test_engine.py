import os
import json
import tempfile
from uuid import uuid4
from concurrent.futures import ThreadPoolExecutor

temporary = tempfile.TemporaryDirectory()
os.environ['CHECKPOINT_DB'] = temporary.name + '/checkpoints.sqlite'
os.environ['CALLS_DB'] = temporary.name + '/calls.sqlite'
os.environ['M1_MODE'] = 'mock'

from fastapi.testclient import TestClient
import httpx
import pytest
import workflow as w
from main import app

client = TestClient(app)

@pytest.fixture(scope='session', autouse=True)
def close_test_database():
    yield
    w.connection.close()
    temporary.cleanup()

def agent(id='a', **extra):
    return dict(id=id, agentId='2', agentVersionId='21', instruction='Analyser', **extra)

def run(stages, execution=None):
    return client.post('/api/orchestrator/execute', json=dict(
        mission_id=1, execution_id=execution or str(uuid4()), instruction='Ventes', stages=stages))

def test_sequence_transmits_outputs_and_rejects_duplicate():
    execution = str(uuid4())
    response = run([agent(), agent('b')], execution)
    assert response.status_code == 200
    data = response.json()
    assert data['status'] == 'COMPLETED'
    assert 'a' in data['state']['outputs']['b']['output']
    assert run([agent()], execution).status_code == 409

def test_pause_resume_and_refusal():
    execution = str(uuid4())
    assert run([dict(kind='approval'), agent()], execution).json()['status'] == 'PAUSED'
    response = client.post(f'/api/orchestrator/executions/{execution}/resume', json={'approved': True})
    assert response.json()['status'] == 'COMPLETED'
    assert client.post(f'/api/orchestrator/executions/{execution}/resume', json={'approved': True}).status_code == 409
    other = str(uuid4())
    run([dict(kind='approval')], other)
    assert client.post(f'/api/orchestrator/executions/{other}/resume', json={'approved': False}).json()['status'] == 'FAILED'

def test_parallel_and_join():
    response = run([dict(kind='parallel', agents=[agent('x'), agent('y')]), agent('synthesis')])
    data = response.json()
    assert data['status'] == 'COMPLETED'
    assert set(data['state']['outputs']) == {'x', 'y', 'synthesis'}

def test_failure_is_not_a_pause(monkeypatch):
    monkeypatch.setattr(w, 'execute_agent', lambda **kwargs: {'status': 'FAILED', 'error': 'M1 indisponible'})
    data = run([agent(), agent('not-run')]).json()
    assert data['status'] == 'FAILED'
    assert 'not-run' not in data['state']['outputs']

def test_review_loop_is_bounded(monkeypatch):
    monkeypatch.setattr(w, 'execute_agent', lambda **kwargs: {'status': 'SUCCESS', 'valid': False, 'output': 'À corriger'})
    data = run([agent(retry_to=0, max_attempts=3)]).json()
    assert data['status'] == 'FAILED'
    assert data['state']['attempts']['a'] == 3

def test_missing_reviewer_contract_fails(monkeypatch):
    monkeypatch.setattr(w, 'execute_agent', lambda **kwargs: {'status': 'SUCCESS', 'output': 'texte non structuré'})
    data = run([agent(retry_to=0)]).json()
    assert data['status'] == 'FAILED'
    assert 'Reviewer' in data['state']['error']

def test_real_contract_and_replay_cache(monkeypatch):
    monkeypatch.setattr(w, 'M1_MODE', 'real')
    monkeypatch.setattr(w, 'M1_EXECUTE_URL', '')
    calls=[]
    def post(url, **kwargs):
        calls.append(kwargs)
        assert url.endswith('/api/agents/2/execute')
        assert kwargs['json']['versionId'] == 21
        assert isinstance(kwargs['json']['input'], str)
        assert isinstance(kwargs['json']['context'], dict)
        return httpx.Response(200, json=dict(status='SUCCESS', output='{"valid":true}', versionId=21), request=httpx.Request('POST', url))
    monkeypatch.setattr(w.httpx, 'post', post)
    context={'executionId':str(uuid4()), 'callId':'a:1', 'outputs':{}}
    first=w.execute_agent('2', 'Analyser', context, '21')
    second=w.execute_agent('2', 'Analyser', context, '21')
    assert first == second
    assert first['valid'] is True
    assert len(calls) == 1

def test_versions_and_duplicate_workers_are_validated():
    invalid=agent(); invalid.pop('agentVersionId')
    assert run([invalid]).status_code == 422
    assert run([dict(kind='parallel', agents=[agent('x'), agent('x')])]).status_code == 422


def conditional_stages(sample=80):
    return [
        agent('a', parents=[]),
        dict(id='check', kind='condition', parents=[dict(id='a')],
             condition=dict(variable='score', operator='>=', expected=70, sample=sample)),
        agent('yes-agent', parents=[dict(id='check', branch='yes')]),
        agent('no-agent', parents=[dict(id='check', branch='no')]),
        agent('merge', parents=[dict(id='yes-agent'), dict(id='no-agent')])
    ]


@pytest.mark.parametrize('sample,chosen,skipped', [(80, 'yes-agent', 'no-agent'), (20, 'no-agent', 'yes-agent')])
def test_condition_selects_only_one_branch_and_merges(sample, chosen, skipped):
    response = run(conditional_stages(sample))
    assert response.status_code == 200, response.text
    state = response.json()['state']
    assert state['status'] == 'COMPLETED'
    assert chosen in state['outputs'] and skipped not in state['outputs']
    assert state['skipped_steps'] == [skipped]
    assert 'merge' in state['outputs']
    assert state['control_results']['check']['actual'] == sample


def test_real_condition_reads_json_and_never_uses_sample():
    state = dict(simulated=False, outputs={'42': {'output': '{"score": 85}'}}, executed_steps=['42'])
    assert w.condition_value(dict(variable='score', sample=0), state) == 85
    assert w.condition_value(dict(variable='step-42.score', sample=0), state) == 85
    with pytest.raises(ValueError):
        w.condition_value(dict(variable='missing', sample=100), state)
    state['outputs']['42']['output'] = '{"score": true}'
    with pytest.raises(ValueError):
        w.condition_value(dict(variable='score'), state)


def test_conditions_reject_missing_value_cycles_and_missing_branches():
    stages = conditional_stages(None)
    result = run(stages)
    assert result.json()['status'] == 'FAILED'
    assert 'numérique' in result.json()['state']['error']
    stages = conditional_stages()
    stages[0]['parents'] = [dict(id='merge')]
    assert run(stages).status_code == 422
    assert run(conditional_stages()[:-2]).status_code == 422


@pytest.mark.parametrize('score,branch', [(85, 'yes'), (10, 'no')])
def test_real_mode_routes_from_agent_json_ignoring_opposite_sample(monkeypatch, score, branch):
    monkeypatch.setattr(w, 'M1_MODE', 'real')
    monkeypatch.setattr(w, 'execute_agent', lambda **kwargs: dict(status='SUCCESS', output=json.dumps({'score': score})))
    stages = conditional_stages(0 if score >= 70 else 100)
    data = run(stages).json()
    assert data['status'] == 'COMPLETED'
    assert data['state']['decisions']['check'] == branch
    assert data['state']['control_results']['check']['simulated'] is False
    assert 'merge' in data['state']['outputs']


@pytest.mark.parametrize('output', ['{}', '{"score": null}', '{"score": "85"}', 'pas du JSON', '{"score": true}'])
def test_real_mode_invalid_json_fails_instead_of_using_sample(monkeypatch, output):
    monkeypatch.setattr(w, 'M1_MODE', 'real')
    monkeypatch.setattr(w, 'execute_agent', lambda **kwargs: dict(status='SUCCESS', output=output))
    data = run(conditional_stages(100)).json()
    assert data['status'] == 'FAILED'
    assert 'Condition check' in data['state']['error']
    assert 'merge' not in data['state']['outputs']



def correction_stages(sample=0):
    return [
        agent('fix', parents=[], max_attempts=2),
        agent('review', parents=[dict(id='fix')], max_attempts=2),
        dict(id='check', kind='condition', parents=[dict(id='review')],
             condition=dict(variable='valid', operator='is_true', expected=1, sample=sample,
                            correction=dict(target='fix', maxAttempts=2, steps=['fix', 'review', 'check']))),
        agent('final', parents=[dict(id='check', branch='yes')])
    ]


def test_correction_mock_stops_at_two_total_attempts_and_keeps_history():
    response = run(correction_stages())
    assert response.status_code == 200, response.text
    state = response.json()['state']
    assert state['status'] == 'FAILED'
    assert state['attempts'] == {'fix': 2, 'review': 2}
    assert len(state['results']) == 4
    assert len(state['condition_history']) == 2
    assert '2/2' in state['error']
    assert 'final' not in state['outputs']


def test_correction_real_uses_reviewer_json_then_succeeds_and_passes_feedback(monkeypatch):
    monkeypatch.setattr(w, 'M1_MODE', 'real')
    calls = []
    def execute(**kwargs):
        calls.append(kwargs)
        reviewing = kwargs['agent_id'] == '3'
        review_count = sum(call['agent_id'] == '3' for call in calls)
        output = json.dumps({'valid': review_count >= 2, 'feedback': 'corriger la syntaxe'}) if reviewing else 'print(42)'
        return dict(status='SUCCESS', output=output, tokensUsed=2)
    monkeypatch.setattr(w, 'execute_agent', execute)
    stages = correction_stages(0)
    stages[1]['agentId'] = '3'
    response = run(stages)
    assert response.status_code == 200, response.text
    state = response.json()['state']
    assert state['status'] == 'COMPLETED'
    assert state['attempts']['fix'] == state['attempts']['review'] == 2
    assert state['attempts']['final'] == 1
    assert [row['branch'] for row in state['condition_history']] == ['no', 'yes']
    assert calls[2]['context']['outputs']['review']['output'].find('corriger la syntaxe') >= 0
    assert len({call['context']['callId'] for call in calls}) == 5


def test_correction_first_success_has_no_extra_call_and_unsafe_loops_are_rejected():
    state = run(correction_stages(1)).json()['state']
    assert state['status'] == 'COMPLETED'
    assert state['attempts'] == {'fix': 1, 'review': 1, 'final': 1}
    stages = correction_stages()
    stages[2]['condition']['correction']['maxAttempts'] = 3
    assert run(stages).status_code == 422
    stages = correction_stages()
    stages[2]['condition']['correction']['target'] = 'final'
    assert run(stages).status_code == 422



def generation_request():
    return dict(mission_id=1, instruction='Planifier un voyage', router_agent_id='1', router_version_id='11',
                catalogue=[dict(id='2', name='Planificateur', versions=['21'])], max_steps=3)


def test_generation_requires_real_router_and_accepts_only_catalogue_agents(monkeypatch):
    request = generation_request()
    assert client.post('/api/orchestrator/generate', json=request).status_code == 503
    monkeypatch.setattr(w, 'M1_MODE', 'real')
    generated = dict(agents=[dict(name='Planifier', agentId='2', agentVersionId='21', instruction='Proposer les étapes du voyage')])
    monkeypatch.setattr(w, 'execute_agent', lambda *args, **kwargs: dict(status='SUCCESS', output=json.dumps(generated)))
    response = client.post('/api/orchestrator/generate', json=request)
    assert response.status_code == 200, response.text
    assert response.json()['steps'][0]['agentVersionId'] == '21'
    generated['agents'][0]['agentVersionId'] = '999'
    assert client.post('/api/orchestrator/generate', json=request).status_code == 502


def test_generation_rejects_malformed_json_or_too_many_steps(monkeypatch):
    monkeypatch.setattr(w, 'M1_MODE', 'real')
    monkeypatch.setattr(w, 'execute_agent', lambda *args, **kwargs: dict(status='SUCCESS', output='pas du JSON'))
    assert client.post('/api/orchestrator/generate', json=generation_request()).status_code == 502
    generated = dict(agents=[dict(name='Planifier', agentId='2', agentVersionId='21', instruction='Voyage')] * 4)
    monkeypatch.setattr(w, 'execute_agent', lambda *args, **kwargs: dict(status='SUCCESS', output=json.dumps(generated)))
    assert client.post('/api/orchestrator/generate', json=generation_request()).status_code == 502


def test_internal_authentication_protects_api_but_not_health(monkeypatch):
    monkeypatch.setenv('ORCHESTRATOR_TOKEN', 'unit-test-token')
    assert client.get('/health').status_code == 200
    assert client.get('/api/orchestrator/executions/missing').status_code == 401
    assert client.get('/api/orchestrator/executions/missing', headers={'Authorization': 'Bearer wrong'}).status_code == 401
    assert client.get('/api/orchestrator/executions/missing', headers={'Authorization': 'Bearer unit-test-token'}).status_code == 404


def test_mock_long_sequence_does_not_duplicate_all_previous_output_text():
    stages = [agent(str(index)) for index in range(12)]
    data = run(stages).json()
    assert data['status'] == 'COMPLETED'
    assert len(data['state']['results']) == 12
    assert all(len(result['output']['output']) < 1000 for result in data['state']['results'])

"""Explicit opt-in waiting-control protocol. V1 activity and completion remain unchanged.

The backend checks the original actual-start V2 memo before opening a session.
The SDK's 'completed' is never promoted to success, and no waiting receipt is an artifact.
"""
from __future__ import annotations
import asyncio
import hashlib
import json
from datetime import timedelta
from temporalio import activity
from temporalio.exceptions import ApplicationError
from temporalio.worker import Worker
from .host import ProfessionalHost, RuntimeBlocked, uuid, sha
from .control import HttpControl

class HttpControlV2(HttpControl):
    async def open(self, binding, reference, dispatch):
        return await self._request('open-v2', {'binding': binding.wire(), 'reference': reference, 'dispatch': dispatch})
    async def finish(self, binding, session, observation, files):
        return await self._request('finish-v2', {'binding': binding.wire(), 'session_id': session, 'observation': observation, 'files': files})

class ProfessionalHostV2(ProfessionalHost):
    def __init__(self, deployment, control: HttpControlV2, supervisor=None):
        if not isinstance(control, HttpControlV2):
            raise RuntimeBlocked('EXPLICIT_WAITING_V2_CONTROL_REQUIRED')
        super().__init__(deployment, control, supervisor)

    @staticmethod
    def _completion(binding, value):
        if not isinstance(value, dict) or set(value) != {'schemaVersion','kind','completion','waiting'} or value['schemaVersion'] != '2.0.0':
            raise RuntimeBlocked('REGISTERED_OUTCOME_V2_REQUIRED')
        if value['kind'] == 'RESULT':
            if value['waiting'] is not None: raise RuntimeBlocked('REGISTERED_OUTCOME_V2_MISMATCH')
            ProfessionalHost._completion(binding, value['completion'])
        elif value['kind'] == 'WAITING_CONTROL':
            waiting = value['waiting']
            fields = {'schemaVersion','outcome','runId','stepId','inputSha256','leaseGeneration','waitingReceiptId','waitingReceiptSha256'}
            if value['completion'] is not None or not isinstance(waiting, dict) or set(waiting) != fields:
                raise RuntimeBlocked('REGISTERED_WAITING_V2_REQUIRED')
            if (waiting['schemaVersion'] != '2.0.0' or waiting['outcome'] != 'WAITING_CONTROL'
                or waiting['runId'] != binding.run or waiting['stepId'] != binding.step
                or waiting['inputSha256'] != binding.input_sha256 or type(waiting['leaseGeneration']) is not int
                or waiting['leaseGeneration'] != binding.generation):
                raise RuntimeBlocked('REGISTERED_WAITING_V2_MISMATCH')
            uuid(waiting['waitingReceiptId']); sha(waiting['waitingReceiptSha256'])
        else:
            raise RuntimeBlocked('REGISTERED_OUTCOME_V2_MISMATCH')
        return value

class ProfessionalActivitiesV2:
    def __init__(self, host: ProfessionalHostV2):
        if not isinstance(host, ProfessionalHostV2): raise RuntimeBlocked('EXPLICIT_WAITING_V2_HOST_REQUIRED')
        self.host = host

    @activity.defn(name='medical.professional.step.v2')
    async def execute(self, reference: dict, dispatch: dict) -> dict:
        if activity.info().workflow_id != reference.get('tenantId','') + '/' + reference.get('runId',''):
            raise ApplicationError('WORKFLOW_OWNER_MISMATCH', type='WORKFLOW_OWNER_MISMATCH', non_retryable=True)
        try:
            return await self.host.execute(reference, dispatch, activity.heartbeat)
        except RuntimeBlocked as error:
            raise ApplicationError(str(error), type=str(error), non_retryable=True) from None
        except asyncio.CancelledError:
            raise
        except Exception:
            raise ApplicationError('PROFESSIONAL_RUNTIME_UNAVAILABLE', type='PROFESSIONAL_RUNTIME_UNAVAILABLE', non_retryable=True) from None

def require_deployment(protocol: dict, expected_sha256: str, namespace: str, queue: str):
    fields = {'schema_version','revision','namespace','control_task_queue','professional_task_queue','workflow_type','professional_activity','waiting_activity','version_marker','load_activity','recovery_activity','recovery_poll_millis','undispatched_failure_activity','execute_stage_activity','commit_stage_activity','recover_stage_activity'}
    if not isinstance(protocol, dict) or set(protocol) != fields or any(not isinstance(protocol[k],str) for k in fields - {'recovery_poll_millis'}) or type(protocol.get('recovery_poll_millis')) is not int:
        raise RuntimeBlocked('EXPLICIT_WAITING_V2_DEPLOYMENT_REQUIRED')
    uuid(protocol['revision']); sha(expected_sha256)
    canonical = json.dumps(protocol, sort_keys=True, separators=(',',':'), ensure_ascii=False).encode('utf-8')
    if (hashlib.sha256(canonical).hexdigest() != expected_sha256 or protocol['schema_version'] != '2.0.0'
        or protocol['namespace'] != namespace or protocol['professional_task_queue'] != queue
        or protocol['control_task_queue'] == queue or protocol['workflow_type'] != 'medical.task.run'
        or protocol['professional_activity'] != 'medical.professional.step.v2'
        or protocol['waiting_activity'] != 'medical.run.commit-waiting.v2'
        or protocol['version_marker'] != 'task-professional-outcome-v2'
        or protocol['load_activity'] != 'medical.run.load.v2'
        or protocol['recovery_activity'] != 'medical.run.recover-step.v2'
        or protocol['undispatched_failure_activity'] != 'medical.run.fail-undispatched.v2'
        or protocol['execute_stage_activity'] != 'medical.run.execute-stage.v2'
        or protocol['commit_stage_activity'] != 'medical.run.commit-stage.v2'
        or protocol['recover_stage_activity'] != 'medical.run.recover-stage.v2'
        or not 100 <= protocol['recovery_poll_millis'] <= 60000):
        raise RuntimeBlocked('EXPLICIT_WAITING_V2_DEPLOYMENT_REQUIRED')
    return protocol

async def serve_v2(client, queue: str, host: ProfessionalHostV2, stop: asyncio.Event, concurrency: int,
                   *, enabled: bool, protocol: dict, protocol_sha256: str):
    if enabled is not True or not queue or type(concurrency) is not int or concurrency < 1:
        raise RuntimeBlocked('EXPLICIT_WAITING_V2_DEPLOYMENT_REQUIRED')
    require_deployment(protocol, protocol_sha256, client.namespace, queue)
    adapter = ProfessionalActivitiesV2(host)
    async with Worker(client, task_queue=queue, activities=[adapter.execute], max_concurrent_activities=concurrency,
                      graceful_shutdown_timeout=timedelta(seconds=45)):
        await stop.wait()

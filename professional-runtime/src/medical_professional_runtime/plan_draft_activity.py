"""Separate pre-plan Temporal activity, sharing only physical host supervision."""
import asyncio
import time
from datetime import timedelta
from temporalio import activity
from temporalio.exceptions import ApplicationError
from temporalio.worker import Worker
from .host import ProfessionalHost, RuntimeBlocked, uuid, sha
from .supervisor_plan import binding_from_wire
from .plan_draft_control import PlanDraftHttpControl

class PlanDraftHost(ProfessionalHost):
    async def execute_plan(self,reference,heartbeat):
        if type(self.control) is not PlanDraftHttpControl:raise RuntimeBlocked('N01_CONTROL_REQUIRED')
        if not isinstance(reference,dict) or reference.get('schemaVersion')!='1.0.0' or reference.get('executionKind')!='SYSTEM_JOB' or reference.get('jobType')!='PLAN_DRAFT' or reference.get('nodeKind')!='N01':raise RuntimeBlocked('N01_WORKFLOW_REFERENCE_INVALID')
        launch=await self.control.open_reference(reference)
        binding=binding_from_wire(launch['binding'])
        if binding.tenant!=reference['tenantId'] or binding.run!=reference['runId'] or binding.step!=reference['stepId'] or binding.input_sha256!=reference['inputSha256']:raise RuntimeBlocked('N01_WORKFLOW_BINDING_MISMATCH')
        timeout=reference['activityTimeoutMillis']
        if type(timeout) is not int or not 0<timeout<=86400000:raise RuntimeBlocked('N01_DEADLINE_INVALID')
        return await self._execute_launch(binding,launch,time.time()+timeout/1000,heartbeat)
    @staticmethod
    def _completion(binding,result):
        fields={'schemaVersion','runId','stepId','inputSha256','receiptId','receiptSha256','state','candidateArtifactId','waitingItemId'}
        if not isinstance(result,dict) or set(result)!=fields or result['schemaVersion']!='1.0.0' or result['runId']!=binding.run or result['stepId']!=binding.step or result['inputSha256']!=binding.input_sha256 or result['state']!='WAITING_INPUT':raise RuntimeBlocked('N01_ORIGINAL_COMPLETION_REQUIRED')
        for key in ('receiptId','candidateArtifactId','waitingItemId'):uuid(result[key])
        sha(result['receiptSha256']);return result

class PlanDraftActivities:
    def __init__(self,host):
        if type(host) is not PlanDraftHost:raise RuntimeBlocked('N01_HOST_REQUIRED')
        self.host=host
    @activity.defn(name='medical.system.plan-draft.execute.v1')
    async def execute(self,reference:dict)->dict:
        if activity.info().workflow_id!=reference.get('tenantId','')+'/plan-draft/'+reference.get('runId',''):
            raise ApplicationError('N01_WORKFLOW_OWNER_MISMATCH',type='N01_WORKFLOW_OWNER_MISMATCH',non_retryable=True)
        try:return await self.host.execute_plan(reference,activity.heartbeat)
        except RuntimeBlocked as error:raise ApplicationError(str(error),type=str(error),non_retryable=True) from None
        except asyncio.CancelledError:raise
        except Exception:raise ApplicationError('N01_RUNTIME_ORIGINAL_RECOVERY_REQUIRED',type='N01_RUNTIME_ORIGINAL_RECOVERY_REQUIRED',non_retryable=True) from None

async def serve(client,queue,host,stop,concurrency):
    if not queue or type(concurrency) is not int or concurrency<1:raise ValueError('WORKER_CAPACITY_REQUIRED')
    adapter=PlanDraftActivities(host)
    async with Worker(client,task_queue=queue,activities=[adapter.execute],max_concurrent_activities=concurrency,graceful_shutdown_timeout=timedelta(seconds=45)):
        await stop.wait()

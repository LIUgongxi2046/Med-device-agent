"""TASK/N05 Temporal activity. Returns only an original independently registered AT07 review receipt.

Submitting findings never means adoption or professional approval.
The existing isolated process loop is shared, not the N02 business schema.
"""
import asyncio
import time
from datetime import timedelta
from temporalio import activity
from temporalio.exceptions import ApplicationError
from temporalio.worker import Worker
from .host import ProfessionalHost, RuntimeBlocked, uuid, sha
from .supervisor_plan import binding_from_wire
from .source_verifier_control import SourceVerifierHttpControl


class SourceVerifierHost(ProfessionalHost):
    def __init__(self, deployment, control, supervisor, relay):
        if type(control) is not SourceVerifierHttpControl:
            raise RuntimeBlocked('N05_CONTROL_REQUIRED')
        from .source_verifier_deployment import SourceVerifierRelayService
        if type(relay) is not SourceVerifierRelayService or relay.control is not control:
            raise RuntimeBlocked('N05_RELAY_REQUIRED')
        self.relay = relay
        super().__init__(deployment, control, supervisor)

    async def execute(self, reference, dispatch, heartbeat=lambda _: None):
        if not isinstance(reference, dict) or reference.get('schemaVersion') != '1.0.0' or reference.get('executionKind') != 'TASK' or not isinstance(dispatch, dict) or reference.get('runId') != dispatch.get('runId'):
            raise RuntimeBlocked('N05_WORKFLOW_REFERENCE_INVALID')
        deadline = reference.get('deadlineEpochMillis')
        if type(deadline) is not int or deadline <= 0:
            raise RuntimeBlocked('N05_DEADLINE_EXCEEDED')
        # This endpoint verifies actual workflow start + exact original dispatch.
        # Actor and prepared envelope are server-owned, never caller substitutions.
        launch = await self.control.open_workflow(reference, dispatch)
        binding = binding_from_wire(launch['binding'])
        if binding.tenant != reference.get('tenantId') or binding.run != reference['runId'] or binding.step != dispatch.get('stepId') or binding.input_sha256 != dispatch.get('inputSha256') or binding.generation != dispatch.get('leaseGeneration'):
            raise RuntimeBlocked('N05_WORKFLOW_BINDING_MISMATCH')
        self._validate_launch(binding, launch)
        if launch['state'] != 'COMPLETED':
            if deadline / 1000 <= time.time():
                raise RuntimeBlocked('N05_DEADLINE_EXCEEDED')
            self.relay.register(binding, launch['session_id'])
        return await self._execute_launch(binding, launch, deadline / 1000, heartbeat)

    @staticmethod
    def _validate_launch(binding, launch):
        if not isinstance(launch, dict) or launch.get('purpose') != 'TASK' or launch.get('node_kind') != 'N05' or launch.get('role') != 'SourceVerifier':
            raise RuntimeBlocked('N05_LAUNCH_IDENTITY_MISMATCH')
        ProfessionalHost._validate_launch(binding, launch)

    @staticmethod
    def _completion(binding, result):
        fields = {'schema_version','node_kind','tenant_id','run_id','step_id','action_id','input_sha256','check_id','target_kind','candidate_id','target_revision','target_sha256','findings_artifact_id','findings_receipt_sha256','coverage_artifact_id','coverage_receipt_sha256','coverage_complete','status','professional_approval','audit_id','event_id','issue_proposal_ids'}
        if not isinstance(result, dict) or set(result) != fields or result['schema_version'] != '1.0.0' or result['node_kind'] != 'N05' or result['target_kind'] != 'CANDIDATE' or result['status'] not in ('SUCCEEDED','WAITING_INPUT') or result['tenant_id'] != binding.tenant or result['run_id'] != binding.run or result['step_id'] != binding.step or result['input_sha256'] != binding.input_sha256 or result['professional_approval'] is not False or type(result['coverage_complete']) is not bool:
            raise RuntimeBlocked('N05_ORIGINAL_AT07_RECEIPT_REQUIRED')
        if result['status'] == 'SUCCEEDED' and (result['coverage_complete'] is not True or result['issue_proposal_ids'] != []):
            raise RuntimeBlocked('N05_ORIGINAL_AT07_RECEIPT_REQUIRED')
        for key in ('check_id','candidate_id','action_id','target_revision','findings_artifact_id','coverage_artifact_id','audit_id','event_id'):
            uuid(result[key])
        for key in ('target_sha256','findings_receipt_sha256','coverage_receipt_sha256'):
            sha(result[key])
        proposals = result['issue_proposal_ids']
        if not isinstance(proposals, list) or len(proposals) > 512 or len(set(proposals)) != len(proposals):
            raise RuntimeBlocked('N05_ORIGINAL_AT07_RECEIPT_REQUIRED')
        for proposal in proposals:
            uuid(proposal)
        # AT07 submission is not adoption, approval, or permission to synthesize PASS.
        return result


class SourceVerifierActivities:
    def __init__(self, host):
        if type(host) is not SourceVerifierHost:
            raise RuntimeBlocked('N05_HOST_REQUIRED')
        self.host = host

    @activity.defn(name='medical.task.source-verifier.execute.v1')
    async def execute(self, reference: dict, dispatch: dict) -> dict:
        if activity.info().workflow_id != reference.get('tenantId', '') + '/' + reference.get('runId', ''):
            raise ApplicationError('N05_WORKFLOW_OWNER_MISMATCH', type='N05_WORKFLOW_OWNER_MISMATCH', non_retryable=True)
        try:
            return await self.host.execute(reference, dispatch, activity.heartbeat)
        except RuntimeBlocked as error:
            raise ApplicationError(str(error), type=str(error), non_retryable=True) from None
        except asyncio.CancelledError:
            raise
        except Exception:
            raise ApplicationError('N05_ORIGINAL_RECOVERY_REQUIRED', type='N05_ORIGINAL_RECOVERY_REQUIRED', non_retryable=True) from None


async def serve(client, queue, host, stop, concurrency, *, enabled, protocol, protocol_sha256):
    from .source_verifier_deployment import require_deployment
    if enabled is not True or not queue or type(concurrency) is not int or not 1 <= concurrency <= 256:
        raise RuntimeBlocked('N05_EXPLICIT_DEPLOYMENT_REQUIRED')
    require_deployment(protocol, protocol_sha256, client.namespace, queue)
    adapter = SourceVerifierActivities(host)
    async with Worker(client, task_queue=queue, activities=[adapter.execute], max_concurrent_activities=concurrency, graceful_shutdown_timeout=timedelta(seconds=45)):
        await stop.wait()

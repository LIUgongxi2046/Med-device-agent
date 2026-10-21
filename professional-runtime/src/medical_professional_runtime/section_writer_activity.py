"""TASK/N04 Temporal activity. Returns only an original AT06 saved-candidate receipt.

Saving a candidate never means adoption, professional completion, or run success.
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
from .section_writer_control import SectionWriterHttpControl


class SectionWriterHost(ProfessionalHost):
    def __init__(self, deployment, control, supervisor, relay):
        if type(control) is not SectionWriterHttpControl:
            raise RuntimeBlocked('N04_CONTROL_REQUIRED')
        from .section_writer_deployment import SectionWriterRelayService
        if type(relay) is not SectionWriterRelayService or relay.control is not control:
            raise RuntimeBlocked('N04_RELAY_REQUIRED')
        self.relay = relay
        super().__init__(deployment, control, supervisor)

    async def execute(self, reference, dispatch, heartbeat=lambda _: None):
        if not isinstance(reference, dict) or reference.get('schemaVersion') != '1.0.0' or reference.get('executionKind') != 'TASK' or not isinstance(dispatch, dict) or reference.get('runId') != dispatch.get('runId'):
            raise RuntimeBlocked('N04_WORKFLOW_REFERENCE_INVALID')
        deadline = reference.get('deadlineEpochMillis')
        if type(deadline) is not int or deadline <= 0:
            raise RuntimeBlocked('N04_DEADLINE_EXCEEDED')
        # This endpoint verifies actual workflow start + exact original dispatch.
        # Actor and prepared envelope are server-owned, never caller substitutions.
        launch = await self.control.open_workflow(reference, dispatch)
        binding = binding_from_wire(launch['binding'])
        if binding.tenant != reference.get('tenantId') or binding.run != reference['runId'] or binding.step != dispatch.get('stepId') or binding.input_sha256 != dispatch.get('inputSha256') or binding.generation != dispatch.get('leaseGeneration'):
            raise RuntimeBlocked('N04_WORKFLOW_BINDING_MISMATCH')
        self._validate_launch(binding, launch)
        if launch['state'] != 'COMPLETED':
            if deadline / 1000 <= time.time():
                raise RuntimeBlocked('N04_DEADLINE_EXCEEDED')
            self.relay.register(binding, launch['session_id'])
        return await self._execute_launch(binding, launch, deadline / 1000, heartbeat)

    @staticmethod
    def _validate_launch(binding, launch):
        if not isinstance(launch, dict) or launch.get('purpose') != 'TASK' or launch.get('node_kind') != 'N04' or launch.get('role') != 'SectionWriter':
            raise RuntimeBlocked('N04_LAUNCH_IDENTITY_MISMATCH')
        ProfessionalHost._validate_launch(binding, launch)

    @staticmethod
    def _completion(binding, result):
        fields = {'schema_version','candidate_id','status','revision','tenant_id','run_id','step_id','action_id','input_sha256','candidate_artifact_id','candidate_receipt_sha256','body_artifact_id','body_sha256','base_document_revision','audit_id','event_id'}
        if not isinstance(result, dict) or set(result) != fields or result['schema_version'] != '1.0.0' or result['status'] != 'READY' or result['revision'] != '1' or result['tenant_id'] != binding.tenant or result['run_id'] != binding.run or result['step_id'] != binding.step or result['input_sha256'] != binding.input_sha256:
            raise RuntimeBlocked('N04_ORIGINAL_AT06_RECEIPT_REQUIRED')
        for key in ('candidate_id','action_id','candidate_artifact_id','body_artifact_id','base_document_revision','audit_id','event_id'):
            uuid(result[key])
        sha(result['candidate_receipt_sha256']); sha(result['body_sha256'])
        # action_id identifies AT06, NOT the host-control action in binding.
        return result


class SectionWriterActivities:
    def __init__(self, host):
        if type(host) is not SectionWriterHost:
            raise RuntimeBlocked('N04_HOST_REQUIRED')
        self.host = host

    @activity.defn(name='medical.task.section-writer.execute.v1')
    async def execute(self, reference: dict, dispatch: dict) -> dict:
        if activity.info().workflow_id != reference.get('tenantId', '') + '/' + reference.get('runId', ''):
            raise ApplicationError('N04_WORKFLOW_OWNER_MISMATCH', type='N04_WORKFLOW_OWNER_MISMATCH', non_retryable=True)
        try:
            return await self.host.execute(reference, dispatch, activity.heartbeat)
        except RuntimeBlocked as error:
            raise ApplicationError(str(error), type=str(error), non_retryable=True) from None
        except asyncio.CancelledError:
            raise
        except Exception:
            raise ApplicationError('N04_ORIGINAL_RECOVERY_REQUIRED', type='N04_ORIGINAL_RECOVERY_REQUIRED', non_retryable=True) from None


async def serve(client, queue, host, stop, concurrency, *, enabled, protocol, protocol_sha256):
    from .section_writer_deployment import require_deployment
    if enabled is not True or not queue or type(concurrency) is not int or not 1 <= concurrency <= 256:
        raise RuntimeBlocked('N04_EXPLICIT_DEPLOYMENT_REQUIRED')
    require_deployment(protocol, protocol_sha256, client.namespace, queue)
    adapter = SectionWriterActivities(host)
    async with Worker(client, task_queue=queue, activities=[adapter.execute], max_concurrent_activities=concurrency, graceful_shutdown_timeout=timedelta(seconds=45)):
        await stop.wait()

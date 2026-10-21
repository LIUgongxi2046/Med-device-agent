"""Independent SYSTEM_JOB/CHECK_DOCUMENT N07 activity. Never resumes the candidate TASK."""
import asyncio
import time
from datetime import timedelta
from temporalio import activity
from temporalio.exceptions import ApplicationError
from temporalio.worker import Worker
from .host import ProfessionalHost, RuntimeBlocked, uuid, sha
from .supervisor_plan import binding_from_wire
from .document_review_control import DocumentReviewHttpControl


class DocumentReviewHost(ProfessionalHost):
    def __init__(self, deployment, control, supervisor, relay):
        from .document_review_deployment import DocumentReviewRelayService
        if type(control) is not DocumentReviewHttpControl or type(relay) is not DocumentReviewRelayService or relay.control is not control:
            raise RuntimeBlocked('N07_CONTROL_AND_RELAY_REQUIRED')
        self.relay = relay
        super().__init__(deployment, control, supervisor)

    async def execute(self, reference, heartbeat=lambda _: None):
        fields = {'schemaVersion','executionKind','jobType','nodeKind','tenantId','projectId','runId','stepId','intentId','documentId','documentRevision','bodySha256','baselineSha256','inputSha256','admissionSha256','policyRevision','controlTaskQueue','professionalTaskQueue','activityTimeoutMillis','heartbeatTimeoutMillis'}
        if not isinstance(reference, dict) or set(reference) != fields or reference['schemaVersion'] != '1.0.0' or reference['executionKind'] != 'SYSTEM_JOB' or reference['jobType'] != 'CHECK_DOCUMENT' or reference['nodeKind'] != 'N07':
            raise RuntimeBlocked('N07_WORKFLOW_REFERENCE_INVALID')
        for key in ('tenantId','projectId','runId','stepId','intentId','documentId','documentRevision','policyRevision'):
            uuid(reference[key])
        for key in ('bodySha256','baselineSha256','inputSha256','admissionSha256'):
            sha(reference[key])
        timeout = reference['activityTimeoutMillis']
        if type(timeout) is not int or not 0 < timeout <= 86400000:
            raise RuntimeBlocked('N07_DEADLINE_INVALID')
        completed = await self.control.completed_original(reference)
        if completed is not None:
            result = self._receipt(completed, reference['runId'], reference['stepId'], reference['inputSha256'])
            for field in ('documentId','documentRevision','bodySha256','baselineSha256'):
                if result[field] != reference[field]:
                    raise RuntimeBlocked('N07_ORIGINAL_DOCUMENT_RESULT_REQUIRED')
            return result
        launch = await self.control.open_workflow(reference)
        binding = binding_from_wire(launch['binding'])
        if binding.tenant != reference['tenantId'] or binding.run != reference['runId'] or binding.step != reference['stepId'] or binding.input_sha256 != reference['inputSha256']:
            raise RuntimeBlocked('N07_WORKFLOW_BINDING_MISMATCH')
        self._validate_launch(binding, launch)
        if launch['state'] != 'COMPLETED':
            self.relay.register(binding, launch['session_id'])
        result = await self._execute_launch(binding, launch, time.time() + timeout / 1000, heartbeat)
        for field in ('documentId','documentRevision','bodySha256','baselineSha256'):
            if result[field] != reference[field]:
                raise RuntimeBlocked('N07_ORIGINAL_DOCUMENT_RESULT_REQUIRED')
        return result

    @staticmethod
    def _validate_launch(binding, launch):
        if not isinstance(launch, dict) or launch.get('purpose') != 'SYSTEM_JOB' or launch.get('node_kind') != 'N07' or launch.get('role') != 'SourceVerifier':
            raise RuntimeBlocked('N07_LAUNCH_IDENTITY_MISMATCH')
        ProfessionalHost._validate_launch(binding, launch)

    @staticmethod
    def _completion(binding, result):
        return DocumentReviewHost._receipt(result, binding.run, binding.step, binding.input_sha256)

    @staticmethod
    def _receipt(result, run, step, input_sha256):
        fields = {'schemaVersion','runId','stepId','inputSha256','receiptId','receiptSha256','state','documentId','documentRevision','bodySha256','baselineSha256','checkIds','professionalApproval'}
        if not isinstance(result, dict) or set(result) != fields or result['schemaVersion'] != '1.0.0' or result['runId'] != run or result['stepId'] != step or result['inputSha256'] != input_sha256 or result['state'] not in ('SUCCEEDED','WAITING_INPUT','FAILED','CANCELLED') or result['professionalApproval'] is not False:
            raise RuntimeBlocked('N07_ORIGINAL_DOCUMENT_RESULT_REQUIRED')
        for key in ('receiptId','documentId','documentRevision'):
            uuid(result[key])
        for key in ('receiptSha256','bodySha256','baselineSha256'):
            sha(result[key])
        checks = result['checkIds']
        if not isinstance(checks, list) or len(checks) > 512 or len(set(checks)) != len(checks) or result['state'] == 'SUCCEEDED' and not checks:
            raise RuntimeBlocked('N07_ORIGINAL_DOCUMENT_RESULT_REQUIRED')
        for check in checks:
            uuid(check)
        return result


class DocumentReviewActivities:
    def __init__(self, host):
        if type(host) is not DocumentReviewHost:
            raise RuntimeBlocked('N07_HOST_REQUIRED')
        self.host = host

    @activity.defn(name='medical.system.check-document.execute.v1')
    async def execute(self, reference: dict) -> dict:
        if activity.info().workflow_id != reference.get('tenantId', '') + '/check-document/' + reference.get('runId', ''):
            raise ApplicationError('N07_WORKFLOW_OWNER_MISMATCH', type='N07_WORKFLOW_OWNER_MISMATCH', non_retryable=True)
        try:
            return await self.host.execute(reference, activity.heartbeat)
        except RuntimeBlocked as error:
            raise ApplicationError(str(error), type=str(error), non_retryable=True) from None
        except asyncio.CancelledError:
            raise
        except Exception:
            raise ApplicationError('N07_ORIGINAL_RECOVERY_REQUIRED', type='N07_ORIGINAL_RECOVERY_REQUIRED', non_retryable=True) from None


async def serve(client, queue, host, stop, concurrency, *, enabled, protocol, protocol_sha256):
    from .document_review_deployment import require_deployment
    if enabled is not True or not queue or type(concurrency) is not int or not 1 <= concurrency <= 256:
        raise RuntimeBlocked('N07_EXPLICIT_DEPLOYMENT_REQUIRED')
    require_deployment(protocol, protocol_sha256, client.namespace, queue)
    adapter = DocumentReviewActivities(host)
    async with Worker(client, task_queue=queue, activities=[adapter.execute], max_concurrent_activities=concurrency, graceful_shutdown_timeout=timedelta(seconds=45)):
        await stop.wait()

"""Actual Temporal Python activity registered under the existing Java wire name."""
import asyncio
from datetime import timedelta
from temporalio import activity
from temporalio.exceptions import ApplicationError
from temporalio.worker import Worker
from .host import ProfessionalHost, RuntimeBlocked

class ProfessionalActivities:
    def __init__(self, host: ProfessionalHost): self.host = host

    @activity.defn(name='medical.professional.step')
    async def execute(self, reference: dict, dispatch: dict) -> dict:
        expected = reference.get('tenantId', '') + '/' + reference.get('runId', '')
        if activity.info().workflow_id != expected:
            raise ApplicationError('WORKFLOW_OWNER_MISMATCH', type='WORKFLOW_OWNER_MISMATCH', non_retryable=True)
        try:
            return await self.host.execute(reference, dispatch, activity.heartbeat)
        except RuntimeBlocked as error:
            raise ApplicationError(str(error), type=str(error), non_retryable=True) from None
        except asyncio.CancelledError:
            raise
        except Exception:
            raise ApplicationError('PROFESSIONAL_RUNTIME_UNAVAILABLE', type='PROFESSIONAL_RUNTIME_UNAVAILABLE', non_retryable=True) from None
        # Cancellation propagates after host admission closure/process cleanup.

async def serve(client, queue: str, host: ProfessionalHost, stop: asyncio.Event, concurrency: int):
    if not queue or concurrency < 1: raise ValueError('WORKER_CAPACITY_REQUIRED')
    adapter = ProfessionalActivities(host)
    async with Worker(client, task_queue=queue, activities=[adapter.execute], max_concurrent_activities=concurrency,
                      graceful_shutdown_timeout=timedelta(seconds=45)):
        await stop.wait()

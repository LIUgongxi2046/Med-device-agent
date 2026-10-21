"""Bounded original outbox consumer. A timeout never creates a replacement command.
Requires separately deployed, pinned mTLS control; no credentials are provisioned here.
"""
import asyncio
from .host import RuntimeBlocked, uuid
from .plan_draft_control import PlanDraftHttpControl

async def consume_once(control,tenant):
    if type(control) is not PlanDraftHttpControl:raise RuntimeBlocked('N01_CONTROL_REQUIRED')
    uuid(tenant)
    jobs=await control._request('queued',{'tenant_id':tenant})
    if not isinstance(jobs,list) or len(jobs)>100:raise RuntimeBlocked('N01_QUEUE_BOUND')
    seen=set();accepted=0
    for job in jobs:
        if not isinstance(job,dict) or set(job)!={'tenant_id','run_id','original_operation_id'} or job['tenant_id']!=tenant:raise RuntimeBlocked('N01_ORIGINAL_COMMAND_INVALID')
        uuid(job['run_id']);uuid(job['original_operation_id'])
        if job['run_id'] in seen:raise RuntimeBlocked('N01_QUEUE_DUPLICATE')
        seen.add(job['run_id'])
        # Server first checks the durable attempt. If present this call is query-only
        # recovery even when Temporal reports NOT_FOUND, never a second start.
        await control._request('launch',job)
        accepted+=1
    return accepted

async def serve(control,tenant,stop,poll_seconds):
    if type(poll_seconds) not in (int,float) or not 1<=poll_seconds<=300:raise ValueError('N01_POLL_BOUND')
    while not stop.is_set():
        await consume_once(control,tenant)
        try:await asyncio.wait_for(stop.wait(),poll_seconds)
        except asyncio.TimeoutError:pass

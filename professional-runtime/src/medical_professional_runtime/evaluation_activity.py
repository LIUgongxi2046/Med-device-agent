"""Dedicated Temporal EVAL activities. Execute is once-only; recovery cannot launch."""
import asyncio
import time
from pathlib import Path
from datetime import timedelta
from temporalio import activity
from temporalio.exceptions import ApplicationError
from temporalio.worker import Worker
from .host import RuntimeBlocked, ProfessionalHost, prepare_job, file_bytes, write_profile, profile_relative, write_inventory, strict_json, sha, uuid
from .supervisor import canonical
from .supervisor_plan import binding_from_wire
from .evaluation_validation import validate_launch
from .evaluation_control import EvaluationControl, snake, camel


def route(reference, step=None):
    ref = snake(reference)
    if not isinstance(ref,dict) or ref.get('schema_version') != '1.0.0' or ref.get('execution_kind') != 'SYSTEM_JOB' or ref.get('job_type') != 'EVALUATION_VERIFICATION': raise RuntimeBlocked('EVALUATION_REFERENCE_REQUIRED')
    uuid(ref['tenant_id']); uuid(ref['run_id']); sha(ref['admission_sha256'])
    value = {'schema_version':'1.0.0','reference':ref}
    if step is not None:
        selected = snake(step)
        if selected not in ref['steps'] or set(selected) != {'step_id','node_kind','node_sha256'}: raise RuntimeBlocked('EVALUATION_STEP_MISMATCH')
        uuid(selected['step_id']); sha(selected['node_sha256']); value['step'] = selected
    return value


def node_receipt(value, reference, step):
    value = snake(value); ref = snake(reference); selected = snake(step)
    fields = {'schema_version','run_id','step_id','node_sha256','state','receipt_id','receipt_sha256'}
    if not isinstance(value,dict) or set(value) != fields or value['schema_version'] != '1.0.0' or value['run_id'] != ref['run_id'] or value['step_id'] != selected['step_id'] or value['node_sha256'] != selected['node_sha256'] or value['state'] not in ('SUCCEEDED','UNKNOWN','WAITING_INPUT','FAILED','CANCELLED'): raise RuntimeBlocked('EVALUATION_ORIGINAL_NODE_RECEIPT_REQUIRED')
    uuid(value['receipt_id']); sha(value['receipt_sha256']); return camel(value)


def cell_receipt(value, reference):
    value = snake(value); ref = snake(reference)
    if not isinstance(value,dict) or set(value) != {'schema_version','run_id','admission_sha256','state','receipt_id','receipt_sha256'} or value['schema_version'] != '1.0.0' or value['run_id'] != ref['run_id'] or value['admission_sha256'] != ref['admission_sha256'] or value['state'] not in ('SUCCEEDED','UNKNOWN','WAITING_INPUT','FAILED','CANCELLED'): raise RuntimeBlocked('EVALUATION_ORIGINAL_CELL_RECEIPT_REQUIRED')
    uuid(value['receipt_id']); sha(value['receipt_sha256']); return camel(value)


class EvaluationHost:
    def __init__(self, deployments, control, supervisor, relay):
        if type(control) is not EvaluationControl or not deployments: raise RuntimeBlocked('EVALUATION_DEPLOYMENT_REQUIRED')
        for deployment in deployments.values(): deployment.validate()
        self.deployments = dict(deployments); self.control = control; self.supervisor = supervisor; self.relay = relay

    async def execute(self, reference, step, heartbeat=lambda _:None):
        request = route(reference,step); ref=request['reference']; selected=request['step']
        timeout=ref['activity_timeout_millis']
        if type(timeout) is not int or not 0<timeout<=86400000: raise RuntimeBlocked('EVALUATION_DEADLINE_INVALID')
        deadline=time.monotonic()+timeout/1000
        # Only this backend-issued private command permits launch. A lost open
        # response exits to Temporal recovery; it is NEVER attempted a second time.
        launch=await self.control.request('open',request)
        if isinstance(launch,dict) and 'receipt_id' in launch:
            return node_receipt(launch,reference,step)
        deployment=self.deployments.get(selected['node_kind'])
        if deployment is None: raise RuntimeBlocked('EVALUATION_NODE_DEPLOYMENT_MISSING')
        binding=binding_from_wire(launch['binding']); validate_launch(binding,launch)
        if binding.tenant!=ref['tenant_id'] or binding.run!=ref['run_id'] or binding.step!=selected['step_id'] or launch['node_kind']!=selected['node_kind']: raise RuntimeBlocked('EVALUATION_LAUNCH_OWNER_MISMATCH')
        session=launch['session_id']; self.control.register(binding,session,reference,step); self.relay.register(binding,session)
        job=deployment.jobs/binding.tenant/binding.run/binding.step/str(binding.generation)
        process=None; bridge=None
        try:
            if launch['job_directory']!=str(job): raise RuntimeBlocked('EVALUATION_JOB_MISMATCH')
            prepare_job(deployment.jobs,(binding.tenant,binding.run,binding.step,str(binding.generation)))
            bridge=await self.supervisor.open_bridge(binding,session,job)
            for item in launch['profile_files']:
                profile_relative(item['relative'])
                value=file_bytes(Path(item['source']),deployment.maximum_observation_bytes,item['sha256'])
                for marker,replacement in {b'@@MEDICAL_SOCKET_PATH@@':str(bridge.socket).encode(),b'@@MEDICAL_TOKEN_PATH@@':str(bridge.token_path).encode(),b'@@MEDICAL_INVENTORY_PATH@@':str(bridge.inventory_path).encode()}.items(): value=value.replace(marker,replacement)
                write_profile(job/'home',item['relative'],value)
            spec={**launch['sdk'],'session_id':session,'home':str(job/'home'),'workspace':str(job/'workspace'),'binding':binding.wire(),'prompt':launch['prompt'],'prompt_sha256':launch['prompt_sha256'],'prompt_provenance':launch['prompt_provenance']}
            payload=canonical(spec)
            if len(payload)>deployment.maximum_observation_bytes: raise RuntimeBlocked('EVALUATION_INPUT_LIMIT')
            environment={'HOME':str(job/'home'),'TMPDIR':str(job/'tmp'),'PATH':str(deployment.python.parent)+':/usr/bin:/bin','LANG':'C.UTF-8','LC_ALL':'C.UTF-8'}
            process=await self.supervisor.prepare(binding,session,job,payload,environment)
            admitted=await self.control.request('admit',self.control.route(binding,session))
            # Backend returns its original START, not a worker-manufactured receipt.
            if admitted.get('state')!='START' or admitted.get('binding')!=binding.wire() or admitted.get('session_id')!=session: raise RuntimeBlocked('EVALUATION_ADMISSION_MISMATCH')
            restored={key:val for key,val in admitted.items() if key not in ('isolation_receipt_id','supervision_sha256')}; restored['state']='PREPARE'
            if restored!=launch: raise RuntimeBlocked('EVALUATION_START_CHANGED')
            uuid(admitted['isolation_receipt_id']); sha(admitted['supervision_sha256'])
            inventory=file_bytes(bridge.inventory_path,deployment.maximum_observation_bytes)
            ProfessionalHost._require_inventory(launch,strict_json(inventory)); write_inventory(job,inventory)
            await self.supervisor.release(process,admitted)
            waiting=asyncio.create_task(self.supervisor.wait(process))
            try:
                while not waiting.done():
                    left=deadline-time.monotonic()
                    if left<=0: raise RuntimeBlocked('EVALUATION_DEADLINE_EXCEEDED')
                    await asyncio.wait({waiting},timeout=min(deployment.heartbeat_seconds,ref['heartbeat_timeout_millis']/3000,left))
                    if not waiting.done():
                        await self.control.heartbeat(binding,session); heartbeat({'session_id':session,'step_id':binding.step})
                observation=strict_json(await waiting)
            finally:
                if not waiting.done(): waiting.cancel(); await asyncio.gather(waiting,return_exceptions=True)
            if observation.get('session_id')!=session or observation.get('binding')!=binding.wire(): raise RuntimeBlocked('EVALUATION_CLOSED_OBSERVATION_MISMATCH')
            await bridge.close()
            # No output/status/exit booleans leave the host. Backend independently
            # fetches CLOSED, the session export and all registered model/tool outputs.
            return node_receipt(await self.control.request('finish',self.control.route(binding,session)),reference,step)
        except BaseException:
            try:
                closed=await self.control.request('close-admission',self.control.route(binding,session))
                if closed!={'session_id':session,'admission':'CLOSED'}: raise RuntimeBlocked('EVALUATION_CLOSURE_UNCONFIRMED')
            finally:
                try:
                    if bridge is not None: await bridge.close()
                finally:
                    if process is not None: await self.supervisor.abort(process)
                    else: await self.supervisor.abort_session(binding,session)
            raise


class EvaluationActivities:
    def __init__(self,control,host=None): self.control=control; self.host=host; self.cancellations={}
    def owner(self,reference):
        ref=route(reference)['reference']
        if activity.info().workflow_id!=ref['tenant_id']+'/'+ref['run_id']: raise ApplicationError('EVALUATION_WORKFLOW_OWNER_MISMATCH',non_retryable=True)
    async def _while_heartbeating(self,reference,operation):
        # Temporal liveness only. This does not renew a backend lease or attest
        # provider/process progress while private backend I/O is pending.
        interval=snake(reference)['heartbeat_timeout_millis']/3000
        if not 0<interval<=28800: raise RuntimeBlocked('EVALUATION_HEARTBEAT_LIMIT')
        async def pulse():
            while True:
                activity.heartbeat({'phase':'AWAITING_ORIGINAL_BACKEND_OR_SUPERVISOR'})
                await asyncio.sleep(min(interval,10))
        task=asyncio.create_task(pulse())
        try: return await operation
        finally:
            task.cancel();await asyncio.gather(task,return_exceptions=True)
    async def _request(self,reference,operation,body):
        return await self._while_heartbeating(reference,self.control.request(operation,body))
    @activity.defn(name='medical.evaluation.await-launch.v1')
    async def await_launch(self,reference:dict)->None:
        self.owner(reference)
        reply=await self._request(reference,'await-launch',route(reference))
        if reply!={'state':'ACCEPTED'}: raise RuntimeBlocked('EVALUATION_ORIGINAL_ACCEPTANCE_REQUIRED')
    @activity.defn(name='medical.evaluation.execute-node.v1')
    async def execute(self,reference:dict,step:dict)->dict:
        self.owner(reference)
        if self.host is None: raise ApplicationError('EVALUATION_PROFESSIONAL_HOST_REQUIRED',non_retryable=True)
        try: return await self._while_heartbeating(reference,self.host.execute(reference,step,activity.heartbeat))
        except asyncio.CancelledError: raise
        except Exception: raise ApplicationError('EVALUATION_ORIGINAL_RECOVERY_REQUIRED',type='EVALUATION_ORIGINAL_RECOVERY_REQUIRED',non_retryable=True) from None
    @activity.defn(name='medical.evaluation.recover-node.v1')
    async def recover(self,reference:dict,step:dict)->dict:
        self.owner(reference)
        return node_receipt(await self._request(reference,'recover-node',route(reference,step)),reference,step)
    @activity.defn(name='medical.evaluation.complete-cell.v1')
    async def complete(self,reference:dict,receipts:list[dict])->dict:
        self.owner(reference)
        return cell_receipt(await self._request(reference,'complete-cell',route(reference)|{'receipts':snake(receipts)}),reference)
    @activity.defn(name='medical.evaluation.cancel-cell.v1')
    async def cancel(self,reference:dict)->dict:
        self.owner(reference)
        plan=await self._request(reference,'cancel-plan',route(reference))
        ref=snake(reference)
        if not isinstance(plan,dict) or set(plan)!={'schema_version','receipt_id','run_id','admission_sha256','state','original_sessions','expected_nodes'} or plan['schema_version']!='1.0.0' or plan['run_id']!=ref['run_id'] or plan['admission_sha256']!=ref['admission_sha256'] or plan['expected_nodes']!=ref['steps'] or plan.get('state')!='CANCEL_REQUESTED' or not isinstance(plan.get('original_sessions'),list) or len(plan['original_sessions'])>10: raise RuntimeBlocked('EVALUATION_ORIGINAL_CANCEL_PLAN_REQUIRED')
        uuid(plan['receipt_id']); seen=set()
        for original in plan['original_sessions']:
            if not isinstance(original,dict) or set(original)!={'session_id','binding','node_kind'}: raise RuntimeBlocked('EVALUATION_CANCEL_SESSION_INVALID')
            session=uuid(original['session_id']); binding=binding_from_wire(original['binding'])
            if session in seen or binding.tenant!=ref['tenant_id'] or binding.run!=ref['run_id'] or not any(step['step_id']==binding.step and step['node_kind']==original['node_kind'] for step in ref['steps']): raise RuntimeBlocked('EVALUATION_CANCEL_OWNER_MISMATCH')
            seen.add(session)
            if self.host is None: raise RuntimeBlocked('EVALUATION_CANCEL_SUPERVISOR_REQUIRED')
            previous=self.cancellations.get(session)
            if previous is None:
                previous=(binding,asyncio.create_task(self.host.supervisor.abort_session(binding,session)))
                self.cancellations[session]=previous
            elif previous[0]!=binding: raise RuntimeBlocked('EVALUATION_CANCEL_OWNER_CHANGED')
            try: await self._while_heartbeating(reference,asyncio.shield(previous[1]))
            except asyncio.CancelledError: raise
            except Exception:
                # An uncertain abort consumes its attempt. The backend may return
                # UNKNOWN; never retry a kill or manufacture a terminal receipt.
                pass
        return cell_receipt(await self._request(reference,'cancel-cell',route(reference)),reference)


async def serve(client,protocol,control,host,stop,concurrency):
    # Queue separation is enforced by deployment wiring; control has no execute registration.
    adapter=EvaluationActivities(control,host)
    async with Worker(client,task_queue=protocol['control_task_queue'],activities=[adapter.await_launch,adapter.recover,adapter.complete,adapter.cancel],max_concurrent_activities=concurrency,graceful_shutdown_timeout=timedelta(seconds=45)), Worker(client,task_queue=protocol['professional_task_queue'],activities=[adapter.execute],max_concurrent_activities=concurrency,graceful_shutdown_timeout=timedelta(seconds=45)):
        await stop.wait()

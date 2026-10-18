import asyncio,hashlib,json,os,unittest,uuid
from pathlib import Path
import httpx2,rfc8785
import medical_model_gateway
if os.environ.get('MEDICAL_CAPTURE_STAGE'):
    medical_model_gateway.__path__.insert(0,str(Path(os.environ['MEDICAL_CAPTURE_STAGE'])/'medical_model_gateway'))
from medical_model_gateway.raw_response import CapturePolicy,ProviderResponseCapture,CapturingTransport,CaptureError

BODY={'model':'deepseek-chat','messages':[{'role':'user','content':'测试输入'}],'stream':True,'reasoning_effort':'none'}
ENDPOINT=ProviderResponseCapture.ENDPOINT
class Chunks(httpx2.AsyncByteStream):
    def __init__(self,chunks,delay=0,fail=False):self.chunks=chunks;self.delay=delay;self.fail=fail;self.closed=False
    async def __aiter__(self):
        for value in self.chunks:
            if self.delay:await asyncio.sleep(self.delay)
            yield value
        if self.fail:raise httpx2.ReadError('private upstream text must not be reported as receipt')
    async def aclose(self):self.closed=True

def capture(body=BODY,size=10000,seconds=2):
    return ProviderResponseCapture(CapturePolicy(True,100000,size,seconds),str(uuid.uuid4()),str(uuid.uuid4()),str(uuid.uuid4()),hashlib.sha256(rfc8785.dumps(body)).hexdigest())

def request(body=BODY):return httpx2.Request('POST',ENDPOINT,content=json.dumps(body,ensure_ascii=False).encode(),headers={'authorization':'Bearer never-store-this','cookie':'never-store-cookie'})

class CaptureTests(unittest.IsolatedAsyncioTestCase):
    async def test_exact_bytes_sha_and_wire_request_hash_have_no_headers(self):
        c=capture();s=Chunks([b'{ "original": ',b'"payload" }']);r=request();response=httpx2.Response(200,headers={'x-request-id':'actual-request-id','content-type':'application/json'},stream=s)
        transport=CapturingTransport(httpx2.MockTransport(lambda _:response),c)
        found=await transport.handle_async_request(r);self.assertEqual(await found.aread(),b'{ "original": "payload" }')
        actual=c.result();self.assertTrue(actual.capture_complete);self.assertEqual(actual.sha256,hashlib.sha256(actual.body).hexdigest());self.assertEqual(actual.request_body_sha256,hashlib.sha256(r.content).hexdigest());self.assertNotEqual(actual.request_body_sha256,actual.canonical_request_body_sha256)
        text=json.dumps(actual.descriptor());self.assertNotIn('never-store',text);self.assertNotIn('authorization',text);self.assertEqual(actual.provider_request_id,'actual-request-id');self.assertNotIn('payload',repr(actual));self.assertTrue(s.closed)
    async def test_unapproved_semantics_or_duplicate_keys_never_call_upstream(self):
        calls=[];c=capture();t=CapturingTransport(httpx2.MockTransport(lambda r:calls.append(r)),c)
        for raw in [b'{"model":"x"}',b'{"model":"x","model":"y"}',b'{"a":NaN}']:
            with self.assertRaises(CaptureError):await t.handle_async_request(httpx2.Request('POST',ENDPOINT,content=raw))
        self.assertEqual(calls,[]);self.assertEqual(c.result().transport_terminal,'NOT_STARTED')
    async def test_wrong_endpoint_and_reasoning_modes_are_not_dispatched(self):
        calls=[]
        for body,url in [(BODY,ENDPOINT+'?secret=x'),({**BODY,'reasoning_effort':'high'},ENDPOINT)]:
            c=capture(body);t=CapturingTransport(httpx2.MockTransport(lambda r:calls.append(r)),c)
            with self.assertRaises(CaptureError):await t.handle_async_request(httpx2.Request('POST',url,json=body))
        self.assertEqual(calls,[])
    async def test_same_attempt_is_one_use_even_after_failure(self):
        count=0
        async def fail(_):
            nonlocal count;count+=1;raise httpx2.ConnectError('unknown')
        c=capture();t=CapturingTransport(httpx2.MockTransport(fail),c)
        with self.assertRaises(httpx2.ConnectError):await t.handle_async_request(request())
        with self.assertRaisesRegex(CaptureError,'ALREADY_USED'):await t.handle_async_request(request())
        self.assertEqual(count,1);self.assertFalse(c.result().capture_complete);self.assertEqual(c.result().transport_terminal,'TRANSPORT_ERROR')
    async def test_byte_overrun_keeps_only_bounded_prefix_and_never_complete(self):
        c=capture(size=3);s=Chunks([b'ab',b'cdef']);t=CapturingTransport(httpx2.MockTransport(lambda _:httpx2.Response(200,stream=s)),c)
        r=await t.handle_async_request(request())
        with self.assertRaisesRegex(CaptureError,'BYTE_LIMIT'):await r.aread()
        v=c.result();self.assertEqual(v.body,b'');self.assertEqual(v.observed_bytes,6);self.assertEqual(v.transport_terminal,'BYTE_LIMIT');self.assertFalse(v.capture_complete);self.assertTrue(s.closed)
    async def test_time_overrun_is_incomplete_and_closes(self):
        c=capture(seconds=.01);s=Chunks([b'a'],delay=.05);t=CapturingTransport(httpx2.MockTransport(lambda _:httpx2.Response(200,stream=s)),c)
        r=await t.handle_async_request(request())
        with self.assertRaisesRegex(CaptureError,'TIME_LIMIT'):await r.aread()
        self.assertEqual(c.result().transport_terminal,'TIME_LIMIT');self.assertTrue(s.closed)
    async def test_cancelled_read_does_not_drain_more_response(self):
        c=capture();s=Chunks([b'a'],delay=1);t=CapturingTransport(httpx2.MockTransport(lambda _:httpx2.Response(200,stream=s)),c);r=await t.handle_async_request(request());task=asyncio.create_task(r.aread());await asyncio.sleep(.01);task.cancel()
        with self.assertRaises(asyncio.CancelledError):await task
        self.assertEqual(c.result().transport_terminal,'CANCELLED');self.assertFalse(c.result().capture_complete);self.assertTrue(s.closed)
    async def test_early_close_needs_explicit_validated_terminal_before_drain(self):
        for drain in (False,True):
            c=capture();s=Chunks([b'{\"first\":',b'\"tail\"}']);t=CapturingTransport(httpx2.MockTransport(lambda _:httpx2.Response(200,headers={'content-type':'application/json'},stream=s)),c);r=await t.handle_async_request(request());it=r.aiter_raw();self.assertEqual(await anext(it),b'{\"first\":')
            if drain:c.allow_terminal_drain('stop','response-id','deepseek-chat')
            await it.aclose();await r.aclose();v=c.result();self.assertEqual(v.capture_complete,drain);self.assertEqual(v.body,b'{\"first\":\"tail\"}' if drain else b'')
    async def test_no_silent_decompression_or_consumed_response(self):
        for response in [httpx2.Response(200,headers={'content-encoding':'gzip'},stream=Chunks([b'gzip'])),httpx2.Response(200,content=b'already-buffered')]:
            c=capture();t=CapturingTransport(httpx2.MockTransport(lambda _:response),c)
            with self.assertRaises(CaptureError):await t.handle_async_request(request())
            self.assertFalse(c.result().capture_complete)
    async def test_duplicate_request_id_not_invented_and_http_error_not_model_success(self):
        c=capture();t=CapturingTransport(httpx2.MockTransport(lambda _:httpx2.Response(429,headers=[('x-request-id','one'),('x-request-id','two'),('content-type','application/json')],stream=Chunks([b'{\"error\":\"limited\"}']))),c);r=await t.handle_async_request(request());await r.aread();v=c.result();self.assertTrue(v.capture_complete);self.assertIsNone(v.provider_request_id);self.assertIsNone(v.provider_finish_reason);self.assertEqual(v.http_status,429)
    async def test_observing_not_started_seals_capture_against_later_send(self):
        c=capture();self.assertEqual(c.result().transport_terminal,'NOT_STARTED');t=CapturingTransport(httpx2.MockTransport(lambda _:None),c)
        with self.assertRaisesRegex(CaptureError,'ALREADY_USED'):await t.handle_async_request(request())
    async def test_forbidden_reasoning_cannot_produce_original_body(self):
        c=capture();t=CapturingTransport(httpx2.MockTransport(lambda _:httpx2.Response(200,stream=Chunks([b'private reason']))),c);r=await t.handle_async_request(request());await anext(r.aiter_raw())
        with self.assertRaisesRegex(CaptureError,'NOT_RETAINED'):c.reject_reasoning_content()
        await r.aclose();v=c.result();self.assertEqual(v.body,b'');self.assertFalse(v.capture_complete);self.assertEqual(v.transport_terminal,'FORBIDDEN_PROVIDER_CONTENT')
    async def test_disabled_or_missing_caps_reject_before_capture(self):
        for p in [CapturePolicy(),CapturePolicy(True),CapturePolicy(True,100,0,1),CapturePolicy(True,True,100,1),CapturePolicy(True,100,100,float('inf'))]:
            with self.assertRaises(CaptureError):ProviderResponseCapture(p,str(uuid.uuid4()),str(uuid.uuid4()),str(uuid.uuid4()),'a'*64)


class SdkCaptureTests(unittest.IsolatedAsyncioTestCase):
    async def run_sdk(self, *, reasoning=False, broken=False):
        from openai import AsyncOpenAI
        from medical_model_gateway.provider import DeepSeekProvider, ProviderCompletion
        from medical_model_gateway.protocol import ProviderSettings, ExactInput
        from medical_contracts.model_stream import RequestParameters
        settings=ProviderSettings(model_id='deepseek-chat',model_version='deepseek-chat',max_output_tokens=32,deadline_seconds=2,temperature_enabled=False,reasoning_efforts=['none'],stop_limit=0)
        params=RequestParameters(max_output_tokens=16,temperature=None,reasoning_effort='none',stop=[],purpose='TASK')
        exact=ExactInput(messages=[{'role':'user','content':'测试输入'}],tools=[])
        approved=settings.request_options(params,exact);c=capture(approved)
        chunk=lambda delta,finish=None:{'id':'actual-response-id','object':'chat.completion.chunk','created':1,'model':'deepseek-chat','choices':[{'index':0,'delta':delta,'finish_reason':finish}]}
        values=[chunk({'role':'assistant','content':'result',**({'reasoning_content':'must-not-retain'} if reasoning else {})}),chunk({},'stop'),{'id':'actual-response-id','object':'chat.completion.chunk','created':1,'model':'deepseek-chat','choices':[],'usage':{'prompt_tokens':3,'completion_tokens':2,'total_tokens':5,'prompt_cache_hit_tokens':0,'prompt_cache_miss_tokens':3}}]
        wire=b''.join(b'data: '+json.dumps(v).encode()+b'\r\n\r\n' for v in values)+b'data: [DONE]\r\n\r\n'
        # The last whitespace block arrives after SDK [DONE]; capturing must drain actual EOF.
        stream=Chunks([wire[i:i+17] for i in range(0,len(wire),17)]+[b'\r\n'],fail=broken)
        calls=[]
        def handle(req):
            calls.append(req);return httpx2.Response(200,headers={'content-type':'text/event-stream','x-request-id':'actual-http-request'},stream=stream)
        client=AsyncOpenAI(api_key='isolated-sdk-test-placeholder',base_url='https://api.deepseek.com',max_retries=0,http_client=httpx2.AsyncClient(transport=CapturingTransport(httpx2.MockTransport(handle),c),trust_env=False,follow_redirects=False))
        provider=DeepSeekProvider(Path('/unused-no-secret-access'));provider.client=lambda capture=None:client
        found=[];failure=None
        try:
            async for event in provider.stream(settings,params,exact,c.receipt_id,capture=c):found.append(event)
        except Exception as e:failure=e
        self.assertEqual(len(calls),1);self.assertTrue(stream.closed);self.last_approved=approved;self.last_request_bytes=calls[0].content
        return c,found,failure,wire+b'\r\n',ProviderCompletion
    async def test_real_sdk_terminal_usage_done_and_exact_entity_eof(self):
        c,found,failure,wire,Completion=await self.run_sdk();self.assertIsNone(failure)
        result=found[-1];self.assertIsInstance(result,Completion);self.assertEqual(result.usage.total_tokens,5)
        self.assertEqual(result.raw_response.body,wire);self.assertTrue(result.raw_response.response_eof_observed)
        self.assertEqual(result.raw_response.provider_request_id,'actual-http-request');self.assertEqual(result.raw_response.provider_response_id,'actual-response-id')
        from jsonschema import Draft202012Validator
        schema=json.loads((Path(__import__('medical_model_gateway.raw_response',fromlist=['__file__']).__file__).parent/'provider-response-capture.schema.json').read_text())
        artifact=result.raw_response.artifact(100000);Draft202012Validator(schema).validate(json.loads(artifact))
        self.assertEqual(json.loads(artifact)['metadata']['sha256'],hashlib.sha256(wire).hexdigest())
    async def test_real_sdk_reasoning_is_discarded_and_never_completed(self):
        c,found,failure,wire,Completion=await self.run_sdk(reasoning=True);self.assertIsNotNone(failure)
        self.assertFalse(any(isinstance(v,Completion) for v in found));self.assertEqual(c.result().body,b'');self.assertFalse(c.result().capture_complete)
    async def test_real_sdk_eof_transport_failure_cannot_be_successful_terminal(self):
        c,found,failure,wire,Completion=await self.run_sdk(broken=True);self.assertIsNotNone(failure)
        self.assertFalse(any(isinstance(v,Completion) for v in found));self.assertFalse(c.result().capture_complete);self.assertEqual(c.result().body,b'')
    async def test_simulation_completion_subclass_still_imports(self):
        from medical_model_gateway.simulation import SimulationCompletion
        self.assertIn('case_id',SimulationCompletion.__dataclass_fields__)

class CaptureContentContractTests(unittest.IsolatedAsyncioTestCase):
    async def test_java_and_python_capture_schema_bytes_are_identical(self):
        root=Path(__file__).resolve().parents[1]
        java=(root/'business/medical-persistence/src/main/resources/provider-response-capture.schema.json').read_bytes()
        python=(Path(__import__('medical_model_gateway.raw_response',fromlist=['__file__']).__file__).parent/'provider-response-capture.schema.json').read_bytes()
        self.assertEqual(java,python);self.assertEqual(hashlib.sha256(java).hexdigest(),'99e4a445fac2018857fd989ba2e0d5df1605d31fce28add1865c1498990c935d')
    async def inspect(self,body,media='text/event-stream'):
        c=capture();t=CapturingTransport(httpx2.MockTransport(lambda _:httpx2.Response(200,headers={'content-type':media},stream=Chunks([body]))),c)
        r=await t.handle_async_request(request());await r.aread();return c.result()
    async def test_sse_hidden_reasoning_duplicate_keys_and_unknown_fields_never_persist(self):
        for body in [b'data: {"reasoning_content":"private"}\n\n',b'data: {"a":1,"a":2}\n\n',b': private heartbeat\n\ndata: {}\n\n',b'event: secret\ndata: {}\n\n',b'data: {"a":NaN}\n\n']:
            v=await self.inspect(body);self.assertFalse(v.capture_complete);self.assertEqual(v.body,b'');self.assertEqual(v.observed_bytes,len(body))
    async def test_multiline_json_data_crlf_preserves_exact_bytes(self):
        body=b'data: {"message":\r\ndata: "ok"}\r\n\r\ndata: [DONE]\r\n\r\n';v=await self.inspect(body)
        self.assertTrue(v.capture_complete);self.assertEqual(v.body,body);self.assertIsNone(v.provider_finish_reason)
    async def test_http_json_reasoning_nested_is_not_retained(self):
        v=await self.inspect(b'{"choices":[{"delta":{"reasoning_content":"private"}}]}','application/json')
        self.assertEqual(v.body,b'');self.assertEqual(v.content_policy_status,'FORBIDDEN_INTERNAL_REASONING')
    async def test_done_only_not_a_verified_provider_document(self):
        v=await self.inspect(b'data: [DONE]\n\n');self.assertFalse(v.capture_complete);self.assertEqual(v.body,b'')
    async def test_artifact_cap_never_truncates_or_changes_hash(self):
        v=await self.inspect(b'{"text":"original"}','application/json')
        for cap in [0,True,10,64*1024*1024+1]:
            with self.assertRaises(CaptureError):v.artifact(cap)
        import base64
        encoded=v.artifact(10000);doc=json.loads(encoded)
        self.assertEqual(base64.b64decode(doc['body_base64'],validate=True),v.body)
        self.assertEqual(doc['metadata']['sha256'],hashlib.sha256(v.body).hexdigest())
    async def test_schema_closed_and_complete_claim_requires_eof_identity(self):
        from jsonschema import Draft202012Validator
        schema=json.loads((Path(__import__('medical_model_gateway.raw_response',fromlist=['__file__']).__file__).parent/'provider-response-capture.schema.json').read_text());validator=Draft202012Validator(schema)
        v=await self.inspect(b'{}','application/json');doc=json.loads(v.artifact(10000));validator.validate(doc)
        for change in [{'unexpected':'x'},{'response_eof_observed':False},{'canonical_request_body_sha256':None},{'request_id_source':'HTTP_X_REQUEST_ID'}]:
            altered=json.loads(json.dumps(doc));altered['metadata'].update(change);self.assertTrue(list(validator.iter_errors(altered)))

if __name__=='__main__':unittest.main()

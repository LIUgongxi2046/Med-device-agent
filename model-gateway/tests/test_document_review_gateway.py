"""Isolated adapter doubles only. No provider, credentials, network or runtime."""
import unittest
from unittest.mock import AsyncMock
from medical_model_gateway.formal_invocation_adapter import InvocationProfile, _parse
from medical_model_gateway.document_review_gateway import DocumentReviewGatewayAdapter
from medical_model_gateway.task_gateway import TaskGatewayAdapter
import rfc8785

ID = '10000000-0000-4000-8000-000000000001'
ATTEMPT = '10000000-0000-4000-8000-000000000002'


class DocumentReviewGatewayTest(unittest.IsolatedAsyncioTestCase):
    def test_dedicated_routes_preserve_task_purpose(self):
        for operation in ('begin-send','receipts','reconciliations'):
            self.assertEqual(InvocationProfile.DOCUMENT_REVIEW_N07_V1.path(ID, operation), '/internal/v1/document-review-invocations/'+ID+'/'+operation)
        self.assertEqual(InvocationProfile.DOCUMENT_REVIEW_N07_V1.value[1], 'TASK')
        with self.assertRaises(ValueError): InvocationProfile.DOCUMENT_REVIEW_N07_V1.path(ID, '../../send')

    def test_n01_n02_cannot_be_relabelled(self):
        for profile in (InvocationProfile.TASK_V1, InvocationProfile.PLAN_DRAFT_V1):
            adapter = type('FixtureAdapter', (), {'_profile':profile})()
            with self.assertRaises(ValueError): DocumentReviewGatewayAdapter(adapter, ID, lambda: None)
        adapter = type('FixtureAdapter', (), {'_profile':InvocationProfile.DOCUMENT_REVIEW_N07_V1})()
        with self.assertRaises(ValueError): TaskGatewayAdapter(adapter, ID, lambda: None)

    async def test_recovery_records_held_original_without_dispatch(self):
        calls = []
        body = rfc8785.dumps({'tenant_id':ID,'fixture':'held original'})
        class Receipts:
            def read(self, invocation, attempt):
                calls.append((invocation, attempt)); return body
        adapter = type('FixtureAdapter', (), {'_profile':InvocationProfile.DOCUMENT_REVIEW_N07_V1,'_receipts':Receipts(),'dispatch':AsyncMock(),'record':AsyncMock()})()
        gateway = DocumentReviewGatewayAdapter(adapter, ID, lambda: None)
        result = await gateway.execute(rfc8785.dumps({'schema_version':'1.0.0','mode':'RECOVER','invocation_id':ID,'dispatch_attempt_id':ATTEMPT}))
        self.assertEqual(calls, [(ID, ATTEMPT)])
        adapter.dispatch.assert_not_awaited(); adapter.record.assert_awaited_once()
        self.assertEqual(_parse(result)['dispatch_attempt_id'], ATTEMPT)
        self.assertTrue(_parse(result)['recorded'])

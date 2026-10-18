"""No credentials or provider I/O: actual resolver with an explicitly mocked secure-read boundary."""
import importlib.util
import pathlib
import sys
import unittest
from unittest.mock import patch, Mock
import medical_model_gateway.approved_credentials as module

class ApprovedCredentialResolverTests(unittest.TestCase):
    def binding(self, **changes):
        values = dict(tenant_id='tenant-a', reference_id='reference-a', revision='r1',
            provider='DEEPSEEK', endpoint='https://api.deepseek.com', path=pathlib.Path('/deployment-only/not-read'))
        values.update(changes)
        return module.ApprovedCredentialBinding(**values)

    def test_wrong_tenant_revision_provider_endpoint_never_reads_secret(self):
        resolver = module.ApprovedCredentialResolver((self.binding(),), lambda _: True)
        with patch.object(module, 'read_key') as read:
            for values in [('tenant-b','reference-a','r1','DEEPSEEK','https://api.deepseek.com'),
                ('tenant-a','reference-a','r2','DEEPSEEK','https://api.deepseek.com'),
                ('tenant-a','reference-a','r1','OTHER','https://api.deepseek.com'),
                ('tenant-a','reference-a','r1','DEEPSEEK','https://unapproved.invalid')]:
                with self.assertRaises(PermissionError): resolver.require(*values)
            read.assert_not_called()

    def test_revocation_before_and_during_read_rejects(self):
        for checks in ([False], [True,False]):
            checks = iter(checks)
            resolver = module.ApprovedCredentialResolver((self.binding(),), lambda _: next(checks))
            with patch.object(module, 'read_key', return_value=object()):
                with self.assertRaises(PermissionError):
                    resolver.require('tenant-a','reference-a','r1','DEEPSEEK','https://api.deepseek.com')

    def test_exact_current_binding_reads_existing_secure_boundary_once(self):
        expected = object()
        resolver = module.ApprovedCredentialResolver((self.binding(),), lambda _: True)
        with patch.object(module, 'read_key', return_value=expected) as read:
            actual = resolver.require('tenant-a','reference-a','r1','DEEPSEEK','https://api.deepseek.com')
            self.assertIs(expected,actual)
            read.assert_called_once_with(pathlib.Path('/deployment-only/not-read'))

    def test_deployment_binding_rejects_ambiguity_relative_path_or_endpoint(self):
        for bindings in [(self.binding(),self.binding()),(self.binding(path=pathlib.Path('relative')),),
            (self.binding(endpoint='https://unapproved.invalid'),),(self.binding(provider='OTHER'),)]:
            with self.assertRaises(ValueError): module.ApprovedCredentialResolver(bindings,lambda _:True)
        self.assertNotIn('/deployment-only',repr(self.binding()))

    def test_provider_is_real_transport_and_construction_does_not_read_secret(self):
        from medical_model_gateway.provider import DeepSeekProvider
        resolver = module.ApprovedCredentialResolver((self.binding(),), lambda _:True)
        with patch.object(module,'read_key') as read:
            provider=resolver.provider('tenant-a','reference-a','r1','DEEPSEEK','https://api.deepseek.com')
            self.assertIsInstance(provider,DeepSeekProvider)
            read.assert_not_called()

    def test_missing_durable_guard_fails_before_secret_resolution(self):
        resolver = module.ApprovedCredentialResolver((self.binding(),), lambda _:True)
        provider = resolver.provider('tenant-a','reference-a','r1','DEEPSEEK','https://api.deepseek.com')
        capture = Mock()
        capture.require_durable_guard.side_effect = PermissionError('guard missing')
        with patch.object(module,'read_key') as read:
            with self.assertRaises(PermissionError): provider.client(capture)
            read.assert_not_called()

    def test_guard_precedes_secret_and_capture_transport_is_not_dropped(self):
        resolver = module.ApprovedCredentialResolver((self.binding(),), lambda _:True)
        provider = resolver.provider('tenant-a','reference-a','r1','DEEPSEEK','https://api.deepseek.com')
        order = []
        capture = Mock()
        capture.require_durable_guard.side_effect = lambda: order.append('guard')
        key = Mock()
        key.get_secret_value.return_value = 'isolated-test-value'
        def read(_):
            order.append('read')
            return key
        with patch.object(module,'read_key',side_effect=read), patch('medical_model_gateway.raw_response.CapturingTransport') as capturing, patch('httpx2.AsyncHTTPTransport') as raw, patch('httpx2.AsyncClient') as http, patch('openai.AsyncOpenAI') as sdk:
            provider.client(capture)
            self.assertEqual(['guard','read'],order)
            capturing.assert_called_once_with(raw.return_value,capture)
            raw.assert_called_once_with(trust_env=False,retries=0)
            self.assertIs(http.call_args.kwargs['transport'],capturing.return_value)
            self.assertFalse(http.call_args.kwargs['trust_env'])
            self.assertFalse(http.call_args.kwargs['follow_redirects'])
            self.assertEqual(0,sdk.call_args.kwargs['max_retries'])

if __name__=='__main__': unittest.main()

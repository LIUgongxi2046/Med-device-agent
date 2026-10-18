import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import uuid

from jsonschema import Draft202012Validator
from pydantic import ValidationError
import rfc8785

from medical_contracts.catalogue import SchemaCatalogue
from medical_contracts.domain import JSON_INTEGER_MAX
from medical_contracts.model_stream import ModelStreamBindings, RequestParameters, Usage
from medical_model_gateway.protocol import ExactInput, ProviderSettings
from medical_model_gateway.simulation import SimulationCase, content_hash
from medical_model_gateway.simulation_execution import SimulationExecutor, SimulationExecutionFinished, SimulationFileStore, SimulationIdentity
from medical_model_gateway.simulation_control import (
    SimulationBinding, SimulationControlError, SimulationQuota, SimulationSnapshot,
    SimulationUnitCaps, begin_send, control_schema, current_counter, mark_unknown,
    measure_input, measure_result, measurement_schema, observe_in_flight, record_result,
    request_cancel, reserve, snapshot_sha256,
)


# This test file is installed under tests/, not a runtime persistence adapter.
ROOT = next(path for path in Path(__file__).resolve().parents if (path / 'agent-design').is_dir())


class SimulationControlTests(unittest.IsolatedAsyncioTestCase):
    @classmethod
    def setUpClass(cls):
        cls.models = ModelStreamBindings(SchemaCatalogue(ROOT))

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='simulation-control-test-')
        self.addCleanup(self.temporary.cleanup)
        self.executor = SimulationExecutor(SimulationFileStore(Path(self.temporary.name), maximum_record_bytes=100000), self.models)
        self.identity = SimulationIdentity(**{name: str(uuid.uuid4()) for name in SimulationIdentity.model_fields})
        self.settings = ProviderSettings(model_id='SIMULATION:policy', model_version='SIMULATION:v1', max_output_tokens=1000,
            deadline_seconds=30, temperature_enabled=False, reasoning_efforts=['none'], stop_limit=0)
        self.parameters = RequestParameters(max_output_tokens=10, temperature=None, reasoning_effort='none', stop=[], purpose='EVALUATION')
        self.input = ExactInput(messages=[{'role': 'user', 'content': '模拟 café 🧪 e\u0301'}], tools=[])
        self.case = SimulationCase(schema_version='1.0.0', input_data_kind='SIMULATION', case_id=str(uuid.uuid4()), receipt_id=str(uuid.uuid4()),
            settings_sha256=content_hash(self.settings), input_sha256=content_hash(self.input), parameters_sha256=content_hash(self.parameters),
            blocks=[{'type': 'text', 'text': '模拟结果'}], reason={'kind': 'stop', 'failure': None}, usage=None)
        self.caps = SimulationUnitCaps(maximum_input_units=10000, maximum_output_units=4000)
        self.binding = self.make_binding()
        self.snapshot = SimulationSnapshot(schema_version='1.0.0', scope='PURE_SIMULATION_POLICY_COMPONENT', tenant_id=self.identity.tenant_id,
            parent_run_id=self.identity.run_id, counter=current_counter(), enterprise_quota=self.quota(), parent_quota=self.quota(),
            revision=1, invocations=())

    def quota(self, units=100000, calls=20):
        return SimulationQuota(account_id=str(uuid.uuid4()), maximum_units=units, maximum_calls=calls,
            spent_units=0, reserved_units=0, spent_calls=0, reserved_calls=0)

    def make_binding(self):
        request = rfc8785.dumps({'identity': self.identity.model_dump(mode='json'), 'case': self.case.model_dump(mode='json'),
            'settings': self.settings.model_dump(mode='json'), 'parameters': self.parameters.model_dump(mode='json'),
            'exact_input': self.input.model_dump(mode='json'), 'provenance_schema_sha256': self.executor.provenance_schema_sha256,
            'model_result_schema_sha256': self.models.catalogue.digests['model-stream']})
        return SimulationBinding(identity=self.identity, configuration_sha256='a' * 64, case_id=self.case.case_id,
            case_sha256=content_hash(self.case), case_receipt_id=self.case.receipt_id, settings_sha256=self.case.settings_sha256,
            parameters_sha256=self.case.parameters_sha256, local_request_sha256=hashlib.sha256(request).hexdigest(),
            exact_input=measure_input(self.input), caps=self.caps)

    def reserved(self):
        return reserve(self.snapshot, self.binding, self.input).next_snapshot

    def dispatched(self):
        return begin_send(self.reserved(), self.identity.invocation_id).next_snapshot

    async def result(self):
        with (patch('medical_model_gateway.credentials.read_key', side_effect=AssertionError('credentials forbidden')),
              patch('medical_model_gateway.provider.DeepSeekProvider.client', side_effect=AssertionError('external provider forbidden')),
              patch('socket.create_connection', side_effect=AssertionError('network forbidden'))):
            values = [value async for value in self.executor.stream(self.identity, self.case, self.settings, self.parameters, self.input)]
        self.assertIsInstance(values[-1], SimulationExecutionFinished)
        return values[-1]

    async def complete(self, snapshot=None):
        result = await self.result()
        transition = record_result(snapshot or self.dispatched(), self.identity.invocation_id, result.result_bytes, result.provenance_bytes, self.models)
        return transition, result

    def test_measurement_is_exact_canonical_utf8_bytes_and_has_real_digests(self):
        measurement = measure_input(self.input)
        raw = rfc8785.dumps(self.input.model_dump(mode='json'))
        self.assertEqual(measurement.units, len(raw))
        self.assertGreater(measurement.units, len(raw.decode('utf-8')))
        self.assertEqual(measurement.content_sha256, hashlib.sha256(raw).hexdigest())
        from medical_model_gateway import simulation_control
        self.assertEqual(measurement.counter.implementation_sha256, hashlib.sha256(Path(simulation_control.__file__).read_bytes()).hexdigest())
        self.assertEqual(measurement.counter.measurement_schema_sha256, hashlib.sha256(rfc8785.dumps(measurement_schema())).hexdigest())
        self.assertEqual(measurement.counter.unit, 'CANONICAL_UTF8_BYTE')

    def test_property_order_is_canonical_but_unicode_normalization_is_not_applied(self):
        reordered = ExactInput.model_validate({'tools': [], 'messages': [{'content': '模拟 café 🧪 e\u0301', 'role': 'user'}]})
        self.assertEqual(measure_input(self.input), measure_input(reordered))
        normalized = ExactInput(messages=[{'role': 'user', 'content': '模拟 café 🧪 é'}], tools=[])
        self.assertNotEqual(measure_input(self.input), measure_input(normalized))

    def test_finite_explicit_caps_and_integer_overflow_fail(self):
        for value in (0, -1, True, 1.5, JSON_INTEGER_MAX+1):
            with self.subTest(value=value), self.assertRaises(ValidationError):
                SimulationUnitCaps(maximum_input_units=value, maximum_output_units=100)
        with self.assertRaises(ValidationError):
            SimulationUnitCaps(maximum_input_units=100)
        with self.assertRaisesRegex(ValueError, 'SIMULATION_UNIT_OVERFLOW'):
            SimulationBinding.model_validate({**self.binding.model_dump(mode='python'),
                'caps': SimulationUnitCaps(maximum_input_units=10000, maximum_output_units=JSON_INTEGER_MAX)})

    def test_input_cap_is_independent_of_model_max_output_tokens(self):
        with self.assertRaisesRegex(ValueError, 'SIMULATION_INPUT_CAP_EXCEEDED'):
            SimulationBinding.model_validate({**self.binding.model_dump(mode='python'),
                'caps': SimulationUnitCaps(maximum_input_units=1, maximum_output_units=4000)})
        self.assertEqual(self.parameters.max_output_tokens, 10)
        self.assertEqual(self.binding.caps.maximum_output_units, 4000)

    def test_reservation_updates_both_accounts_and_no_provider_tokens(self):
        proposal = reserve(self.snapshot, self.binding, self.input)
        self.assertEqual(proposal.expected_snapshot_sha256, snapshot_sha256(self.snapshot))
        units = measure_input(self.input).units + self.caps.maximum_output_units
        for account in (proposal.next_snapshot.enterprise_quota, proposal.next_snapshot.parent_quota):
            self.assertEqual(account.reserved_units, units)
            self.assertEqual(account.reserved_calls, 1)
            self.assertEqual(account.spent_units, 0)
        row = proposal.next_snapshot.invocations[0]
        self.assertIsNone(row.fixture_usage)
        self.assertEqual(row.usage_origin, 'UNKNOWN')
        self.assertFalse(row.production_eligible)
        self.assertEqual(self.snapshot.invocations, ())
        self.assertNotIn('tokens', type(row).model_fields)

    def test_same_action_replay_is_immutable_and_changed_binding_conflicts(self):
        state = self.reserved()
        replay = reserve(state, self.binding, self.input)
        self.assertEqual(replay.decision, 'REPLAY')
        self.assertEqual(replay.next_snapshot, state)
        fields = ('case_sha256', 'configuration_sha256', 'settings_sha256', 'parameters_sha256', 'local_request_sha256')
        for field in fields:
            changed = self.binding.model_copy(update={field: 'b' * 64})
            with self.subTest(field=field), self.assertRaisesRegex(ValueError, 'SIMULATION_IDEMPOTENCY_CONFLICT'):
                reserve(state, changed, self.input)

    def test_alias_invocation_id_and_cross_scope_rejected(self):
        state = self.reserved()
        for field in ('action_id', 'tenant_id', 'run_id'):
            identity = self.identity.model_copy(update={field: str(uuid.uuid4())})
            with self.subTest(field=field), self.assertRaises(ValueError):
                reserve(state, self.binding.model_copy(update={'identity': identity}), self.input)

    def test_second_account_failure_leaves_original_accounts_unchanged(self):
        before = self.snapshot.model_copy(update={'parent_quota': self.quota(calls=0)})
        original = before.model_dump(mode='json')
        with self.assertRaisesRegex(ValueError, 'SIMULATION_QUOTA_EXCEEDED'):
            reserve(before, self.binding, self.input)
        self.assertEqual(before.model_dump(mode='json'), original)

    def test_exact_quota_boundary_then_next_action_fails(self):
        units = self.binding.exact_input.units+self.caps.maximum_output_units
        state = self.snapshot.model_copy(update={'enterprise_quota': self.quota(units=units), 'parent_quota': self.quota(units=units)})
        state = reserve(state, self.binding, self.input).next_snapshot
        identity = self.identity.model_copy(update={'action_id': str(uuid.uuid4()), 'invocation_id': str(uuid.uuid4())})
        with self.assertRaisesRegex(ValueError, 'SIMULATION_QUOTA_EXCEEDED'):
            reserve(state, self.binding.model_copy(update={'identity': identity}), self.input)

    def test_only_cas_winner_can_use_dispatch_candidate_and_recomputation_is_not_authority(self):
        original = self.reserved()
        left = begin_send(original, self.identity.invocation_id)
        right = begin_send(original, self.identity.invocation_id)
        self.assertTrue(left.dispatch_candidate)
        self.assertEqual(left, right)  # Pure computation is repeatable, never an exactly-once side effect.
        committed = original
        winners = 0
        for proposal in (left, right):
            if snapshot_sha256(committed) == proposal.expected_snapshot_sha256:
                committed = proposal.next_snapshot
                winners += 1
        self.assertEqual(winners, 1)
        self.assertFalse(begin_send(committed, self.identity.invocation_id).dispatch_candidate)
        self.assertEqual(committed.invocations[0].dispatch_generation, 1)
        self.assertTrue(left.requires_atomic_commit)

    def test_unknown_never_redispatches_and_keeps_entire_reservation(self):
        before = self.dispatched()
        state = mark_unknown(before, self.identity.invocation_id).next_snapshot
        self.assertEqual(state.invocations[0].status, 'UNKNOWN')
        self.assertEqual(state.enterprise_quota, before.enterprise_quota)
        for _ in range(5):
            self.assertFalse(begin_send(state, self.identity.invocation_id).dispatch_candidate)
            state = mark_unknown(state, self.identity.invocation_id).next_snapshot
        self.assertEqual(state.invocations[0].reservation_state, 'HELD')

    def test_stale_observations_cannot_reopen_or_erase_unknown(self):
        with self.assertRaisesRegex(ValueError, 'SIMULATION_OBSERVATION_STALE'):
            mark_unknown(self.reserved(), self.identity.invocation_id)
        state = observe_in_flight(self.dispatched(), self.identity.invocation_id).next_snapshot
        self.assertEqual(observe_in_flight(state, self.identity.invocation_id).next_snapshot, state)
        state = mark_unknown(state, self.identity.invocation_id).next_snapshot
        with self.assertRaisesRegex(ValueError, 'SIMULATION_OBSERVATION_STALE'):
            observe_in_flight(state, self.identity.invocation_id)

    async def test_late_result_reconciles_unknown_and_replay_does_not_charge_twice(self):
        unknown = mark_unknown(self.dispatched(), self.identity.invocation_id).next_snapshot
        proposal, result = await self.complete(unknown)
        state = proposal.next_snapshot
        row = state.invocations[0]
        self.assertEqual(row.status, 'SUCCEEDED')
        self.assertEqual(row.output.units, len(result.result_bytes))
        self.assertEqual(row.charged_units, measure_input(self.input).units+len(result.result_bytes))
        self.assertIsNone(row.fixture_usage)
        self.assertEqual(row.provider_charge_amount, '0.00000000')
        self.assertFalse(row.external_request_sent)
        for account in (state.enterprise_quota, state.parent_quota):
            self.assertEqual(account.reserved_units, 0)
            self.assertEqual(account.reserved_calls, 0)
            self.assertEqual(account.spent_calls, 1)
            self.assertEqual(account.spent_units, row.charged_units)
        replay = record_result(state, self.identity.invocation_id, result.result_bytes, result.provenance_bytes, self.models)
        self.assertEqual(replay.decision, 'REPLAY')
        self.assertEqual(replay.next_snapshot, state)
        self.assertFalse(begin_send(state, self.identity.invocation_id).dispatch_candidate)

    async def test_fixture_usage_preserved_without_becoming_measured_tokens(self):
        usage = Usage(uncached_input_tokens=17, cache_read_tokens=None, cache_write_tokens=None,
            output_tokens=2, total_tokens=None, reasoning_tokens=None, source_receipt_id=self.case.receipt_id)
        self.case = self.case.model_copy(update={'usage': usage})
        self.binding = self.make_binding()
        proposal, result = await self.complete()
        row = proposal.next_snapshot.invocations[0]
        self.assertEqual(row.fixture_usage, usage)
        self.assertIsNone(row.fixture_usage.total_tokens)
        self.assertEqual(row.usage_origin, 'SIMULATION_FIXTURE')
        self.assertEqual(row.output.units, len(result.result_bytes))
        self.assertNotEqual(row.output.units, usage.output_tokens)

    async def test_failed_result_consumes_measured_local_units_and_one_call(self):
        self.case = SimulationCase.model_validate({**self.case.model_dump(mode='python'), 'blocks': [], 'reason': {'kind': 'error', 'failure': {
            'code': 'SIMULATION_FAILURE', 'message': '固定模拟失败', 'provider_status': None, 'provider_request_id': None}}})
        self.binding = self.make_binding()
        proposal, _ = await self.complete()
        self.assertEqual(proposal.next_snapshot.invocations[0].status, 'FAILED')
        self.assertEqual(proposal.next_snapshot.parent_quota.spent_calls, 1)

    def test_unsent_cancel_releases_and_never_dispatches(self):
        state = request_cancel(self.reserved(), self.identity.invocation_id).next_snapshot
        self.assertEqual(state.invocations[0].status, 'CANCELLED')
        self.assertEqual(state.invocations[0].dispatch_generation, 0)
        self.assertEqual(state.enterprise_quota.reserved_units, 0)
        self.assertEqual(state.parent_quota.spent_calls, 0)
        self.assertFalse(begin_send(state, self.identity.invocation_id).dispatch_candidate)
        self.assertEqual(request_cancel(state, self.identity.invocation_id).next_snapshot, state)

    async def test_dispatched_cancel_is_only_intent_and_late_result_can_complete(self):
        dispatched = self.dispatched()
        requested = request_cancel(dispatched, self.identity.invocation_id).next_snapshot
        self.assertEqual(requested.invocations[0].status, 'CANCEL_REQUESTED')
        self.assertEqual(requested.enterprise_quota, dispatched.enterprise_quota)
        self.assertEqual(mark_unknown(requested, self.identity.invocation_id).next_snapshot, requested)
        self.assertFalse(begin_send(requested, self.identity.invocation_id).dispatch_candidate)
        proposal, _ = await self.complete(requested)
        self.assertEqual(proposal.next_snapshot.invocations[0].status, 'SUCCEEDED')
        self.assertEqual(request_cancel(proposal.next_snapshot, self.identity.invocation_id).next_snapshot, proposal.next_snapshot)

    async def test_result_without_dispatch_is_not_a_success(self):
        result = await self.result()
        for state in (self.reserved(), request_cancel(self.reserved(), self.identity.invocation_id).next_snapshot):
            with self.assertRaisesRegex(ValueError, 'SIMULATION_RESULT_WITHOUT_DISPATCH'):
                record_result(state, self.identity.invocation_id, result.result_bytes, result.provenance_bytes, self.models)

    async def test_output_over_cap_keeps_unknown_reservation(self):
        self.caps = SimulationUnitCaps(maximum_input_units=10000, maximum_output_units=1)
        self.binding = self.make_binding()
        state = mark_unknown(self.dispatched(), self.identity.invocation_id).next_snapshot
        original = state.model_dump(mode='json')
        result = await self.result()
        with self.assertRaisesRegex(ValueError, 'SIMULATION_OUTPUT_CAP_EXCEEDED'):
            record_result(state, self.identity.invocation_id, result.result_bytes, result.provenance_bytes, self.models)
        self.assertEqual(state.model_dump(mode='json'), original)

    async def test_changed_result_even_with_updated_provenance_cannot_replace_fixed_case(self):
        proposal, result = await self.complete()
        content = json.loads(result.result_bytes)
        content['blocks'][0]['text'] = 'changed'
        changed = rfc8785.dumps(content)
        proof = json.loads(result.provenance_bytes)
        proof['model_result_sha256'] = hashlib.sha256(changed).hexdigest()
        with self.assertRaisesRegex(ValueError, 'SIMULATION_RESULT_BINDING_MISMATCH'):
            record_result(proposal.next_snapshot, self.identity.invocation_id, changed, rfc8785.dumps(proof), self.models)

    async def test_wrong_provenance_id_digest_counter_and_receipt_are_rejected(self):
        result = await self.result()
        for field in ('request_sha256', 'case_sha256', 'settings_sha256', 'input_sha256', 'parameters_sha256', 'model_result_schema_sha256'):
            proof = json.loads(result.provenance_bytes)
            proof[field] = 'b'*64
            with self.subTest(field=field), self.assertRaisesRegex(ValueError, 'SIMULATION_RESULT_BINDING_MISMATCH'):
                record_result(self.dispatched(), self.identity.invocation_id, result.result_bytes, rfc8785.dumps(proof), self.models)
        for field in ('receipt_id', 'run_id', 'case_id'):
            proof = json.loads(result.provenance_bytes)
            proof[field] = str(uuid.uuid4())
            with self.subTest(field=field), self.assertRaisesRegex(ValueError, 'SIMULATION_RESULT_BINDING_MISMATCH'):
                record_result(self.dispatched(), self.identity.invocation_id, result.result_bytes, rfc8785.dumps(proof), self.models)

    async def test_noncanonical_duplicate_key_and_truncated_result_rejected(self):
        result = await self.result()
        for raw in (result.result_bytes+b'\n', result.result_bytes[:-1], b'{"a":1,"a":2}'):
            with self.subTest(raw=raw[:20]), self.assertRaises(ValueError):
                record_result(self.dispatched(), self.identity.invocation_id, raw, result.provenance_bytes, self.models)

    def test_counter_code_or_contract_change_fail_closed(self):
        for field in ('implementation_sha256', 'measurement_schema_sha256'):
            counter = current_counter().model_copy(update={field: 'c'*64})
            state = self.snapshot.model_copy(update={'counter': counter})
            with self.subTest(field=field), self.assertRaisesRegex(ValueError, 'SIMULATION_COUNTER_REVISION_MISMATCH'):
                reserve(state, self.binding, self.input)

    def test_invalid_model_copy_is_revalidated_and_nested_state_is_immutable(self):
        bad = self.reserved().model_copy(update={'invocations': self.reserved().invocations*2})
        with self.assertRaisesRegex(ValueError, 'SIMULATION_LEDGER_BINDING_INVALID'):
            begin_send(bad, self.identity.invocation_id)
        state = self.reserved()
        with self.assertRaises(ValidationError):
            state.invocations[0].binding.caps.maximum_output_units = 10
        self.assertIsInstance(state.invocations, tuple)
        with self.assertRaises(ValueError):
            begin_send(state, str(uuid.uuid4()))

    async def test_standalone_schemas_validate_actual_values_without_catalogue_change(self):
        result = await self.result()
        for schema in (control_schema(), measurement_schema()):
            Draft202012Validator.check_schema(schema)
        control = Draft202012Validator(control_schema())
        control.validate(self.binding.model_dump(mode='json'))
        control.validate(self.snapshot.model_dump(mode='json'))
        control.validate(begin_send(self.reserved(), self.identity.invocation_id).model_dump(mode='json'))
        measurement = Draft202012Validator(measurement_schema())
        measurement.validate(self.input.model_dump(mode='json'))
        measurement.validate(json.loads(result.result_bytes))

    def test_exported_schema_bytes_match_runtime_structural_contracts(self):
        paths = {'simulation-control-component.schema.json': control_schema(),
                 'simulation-byte-measurement.schema.json': measurement_schema()}
        for filename, expected in paths.items():
            self.assertEqual(json.loads((ROOT / 'agent-design' / filename).read_bytes()), expected)
        invalid = self.snapshot.model_dump(mode='json')
        invalid['tenant_id'] = 'not-a-uuid'
        self.assertFalse(Draft202012Validator(control_schema()).is_valid(invalid))

    def test_snapshot_rejects_boolean_generation_and_unbacked_counters(self):
        state = self.dispatched()
        invalid = state.model_dump(mode='python')
        invalid['invocations'][0]['dispatch_generation'] = True
        with self.assertRaises(ValidationError):
            SimulationSnapshot.model_validate(invalid)
        invalid = state.model_dump(mode='python')
        invalid['enterprise_quota']['reserved_units'] = 0
        with self.assertRaisesRegex(ValueError, 'SIMULATION_QUOTA_LEDGER_MISMATCH'):
            SimulationSnapshot.model_validate(invalid)

    def test_reservation_recomputes_input_count_and_hash_instead_of_trusting_metadata(self):
        for changes in ({'units': 1}, {'content_sha256': 'd'*64}):
            measurement = self.binding.exact_input.model_copy(update=changes)
            binding = self.binding.model_copy(update={'exact_input': measurement})
            with self.subTest(changes=changes), self.assertRaisesRegex(ValueError, 'SIMULATION_INPUT_MEASUREMENT_MISMATCH'):
                reserve(self.snapshot, binding, self.input)
        forged = self.binding.model_copy(update={
            'exact_input': self.binding.exact_input.model_copy(update={'units': 1}),
            'caps': SimulationUnitCaps(maximum_input_units=1, maximum_output_units=4000)})
        with self.assertRaisesRegex(ValueError, 'SIMULATION_INPUT_MEASUREMENT_MISMATCH'):
            reserve(self.snapshot, forged, self.input)

    def test_send_cancel_cas_race_has_one_winner_in_both_orders(self):
        before = self.reserved()
        sending = begin_send(before, self.identity.invocation_id)
        cancelling = request_cancel(before, self.identity.invocation_id)
        for candidates in ((sending, cancelling), (cancelling, sending)):
            state, wins = before, 0
            for proposal in candidates:
                if proposal.expected_snapshot_sha256 == snapshot_sha256(state):
                    state, wins = proposal.next_snapshot, wins+1
            self.assertEqual(wins, 1)
            self.assertEqual(state.invocations[0].status, 'DISPATCHING' if candidates[0] == sending else 'CANCELLED')
            self.assertFalse(begin_send(state, self.identity.invocation_id).dispatch_candidate)

    async def test_receipt_cancel_cas_race_settles_once_in_both_orders(self):
        before = self.dispatched()
        result = await self.result()
        def receipt(state):
            return record_result(state, self.identity.invocation_id, result.result_bytes, result.provenance_bytes, self.models)
        completed, cancelled = receipt(before), request_cancel(before, self.identity.invocation_id)
        for candidates in ((completed, cancelled), (cancelled, completed)):
            state, wins = before, 0
            for proposal in candidates:
                if proposal.expected_snapshot_sha256 == snapshot_sha256(state):
                    state, wins = proposal.next_snapshot, wins+1
            self.assertEqual(wins, 1)
            state = receipt(state).next_snapshot
            self.assertEqual(state.invocations[0].status, 'SUCCEEDED')
            self.assertEqual(state.enterprise_quota.spent_calls, 1)
            self.assertEqual(state.enterprise_quota.reserved_calls, 0)
            self.assertEqual(receipt(state).next_snapshot, state)
            self.assertEqual(request_cancel(state, self.identity.invocation_id).next_snapshot, state)

    def test_lost_dispatch_acknowledgment_reload_never_grants_send_again(self):
        committed = self.dispatched()
        reloaded = SimulationSnapshot.model_validate_json(committed.model_dump_json())
        self.assertEqual(reloaded, committed)
        self.assertFalse(begin_send(reloaded, self.identity.invocation_id).dispatch_candidate)
        unknown = mark_unknown(reloaded, self.identity.invocation_id).next_snapshot
        self.assertEqual(unknown.enterprise_quota, committed.enterprise_quota)
        self.assertFalse(begin_send(unknown, self.identity.invocation_id).dispatch_candidate)

    def test_account_addition_overflow_fails_without_mutating_snapshot(self):
        quota = self.quota(units=JSON_INTEGER_MAX).model_copy(update={'spent_units': JSON_INTEGER_MAX})
        state = self.snapshot.model_copy(update={'enterprise_quota': quota})
        original = snapshot_sha256(state)
        with self.assertRaisesRegex(ValueError, 'SIMULATION_UNIT_OVERFLOW'):
            reserve(state, self.binding, self.input)
        self.assertEqual(snapshot_sha256(state), original)


if __name__ == '__main__':
    unittest.main()

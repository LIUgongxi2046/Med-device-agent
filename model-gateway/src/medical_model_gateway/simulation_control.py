"""Pure SIMULATION measurement and transition policy, without authority or I/O.

Transitions are proposals. A repository must atomically compare the complete
expected state and commit the proposal before acting on ``dispatch_candidate``.
Calling this module, including replaying a proposal, never grants execution.
"""

from copy import deepcopy
import hashlib
import json
from pathlib import Path
from typing import Annotated, Literal, Self

from pydantic import Field, TypeAdapter, model_validator
import rfc8785

from medical_contracts.domain import DomainModel, Hash, NonnegativeInt, PositiveInt, WireId, JSON_INTEGER_MAX
from medical_contracts.model_stream import ModelResult, ModelStreamBindings, Usage
from .protocol import ExactInput
from .simulation import SimulationCase, content_hash
from .simulation_execution import SimulationIdentity, SimulationProvenance


class SimulationControlError(ValueError):
    """Stable policy error; no input or output content is included."""


class CounterIdentity(DomainModel):
    unit: Literal['CANONICAL_UTF8_BYTE']
    revision: Literal['LOCAL_SIMULATION_CANONICAL_UTF8_BYTE_V1']
    implementation_sha256: Hash
    measurement_schema_sha256: Hash


class Measurement(DomainModel):
    kind: Literal['EXACT_INPUT', 'MODEL_RESULT']
    counter: CounterIdentity
    content_sha256: Hash
    units: PositiveInt


class SimulationUnitCaps(DomainModel):
    """Local byte caps, deliberately unrelated to model token limits."""

    maximum_input_units: PositiveInt
    maximum_output_units: PositiveInt


class SimulationBinding(DomainModel):
    identity: SimulationIdentity
    configuration_sha256: Hash
    case_id: WireId
    case_sha256: Hash
    case_receipt_id: WireId
    settings_sha256: Hash
    parameters_sha256: Hash
    local_request_sha256: Hash
    exact_input: Measurement
    caps: SimulationUnitCaps

    @model_validator(mode='after')
    def bounded(self) -> Self:
        if self.exact_input.kind != 'EXACT_INPUT':
            raise SimulationControlError('SIMULATION_INPUT_MEASUREMENT_REQUIRED')
        if self.exact_input.units > self.caps.maximum_input_units:
            raise SimulationControlError('SIMULATION_INPUT_CAP_EXCEEDED')
        _sum(self.exact_input.units, self.caps.maximum_output_units)
        return self


class SimulationQuota(DomainModel):
    """Unapproved accounting input. This record is not a quota approval."""

    account_id: WireId
    maximum_units: NonnegativeInt
    maximum_calls: NonnegativeInt
    spent_units: NonnegativeInt
    reserved_units: NonnegativeInt
    spent_calls: NonnegativeInt
    reserved_calls: NonnegativeInt

    @model_validator(mode='after')
    def finite(self) -> Self:
        if (_sum(self.spent_units, self.reserved_units) > self.maximum_units
                or _sum(self.spent_calls, self.reserved_calls) > self.maximum_calls):
            raise SimulationControlError('SIMULATION_QUOTA_INVALID')
        return self


Status = Literal['RESERVED', 'DISPATCHING', 'IN_FLIGHT', 'UNKNOWN', 'CANCEL_REQUESTED', 'SUCCEEDED', 'FAILED', 'CANCELLED']


class SimulationInvocation(DomainModel):
    binding: SimulationBinding
    status: Status
    revision: PositiveInt
    dispatch_generation: Annotated[int, Field(ge=0, le=1)]
    reservation_state: Literal['HELD', 'CONSUMED', 'RELEASED']
    reserved_units: PositiveInt
    charged_units: NonnegativeInt | None
    output: Measurement | None
    receipt_id: WireId | None
    provenance_sha256: Hash | None
    fixture_usage: Usage | None
    usage_origin: Literal['UNKNOWN', 'SIMULATION_FIXTURE']
    external_request_sent: Literal[False] = False
    provider_charge_amount: Literal['0.00000000'] = '0.00000000'
    charge_basis: Literal['LOCAL_SIMULATION_NO_PROVIDER_REQUEST'] = 'LOCAL_SIMULATION_NO_PROVIDER_REQUEST'
    production_eligible: Literal[False] = False

    @model_validator(mode='after')
    def coherent(self) -> Self:
        if self.reserved_units != _sum(self.binding.exact_input.units, self.binding.caps.maximum_output_units):
            raise SimulationControlError('SIMULATION_RESERVATION_MISMATCH')
        final = self.status in ('SUCCEEDED', 'FAILED')
        if final:
            if (self.dispatch_generation != 1 or self.reservation_state != 'CONSUMED' or self.output is None
                    or self.receipt_id != self.binding.case_receipt_id or self.provenance_sha256 is None
                    or self.output.kind != 'MODEL_RESULT' or self.output.counter != self.binding.exact_input.counter
                    or self.output.units > self.binding.caps.maximum_output_units
                    or self.charged_units != _sum(self.binding.exact_input.units, self.output.units)):
                raise SimulationControlError('SIMULATION_RESULT_STATE_INVALID')
        else:
            if any(value is not None for value in (self.charged_units, self.output, self.receipt_id, self.provenance_sha256, self.fixture_usage)):
                raise SimulationControlError('SIMULATION_RESULT_STATE_INVALID')
            if self.status == 'CANCELLED':
                if self.dispatch_generation != 0 or self.reservation_state != 'RELEASED':
                    raise SimulationControlError('SIMULATION_TERMINATION_UNPROVEN')
            elif self.reservation_state != 'HELD' or self.dispatch_generation != (0 if self.status == 'RESERVED' else 1):
                raise SimulationControlError('SIMULATION_DISPATCH_STATE_INVALID')
        if ((self.fixture_usage is None) != (self.usage_origin == 'UNKNOWN')
                or self.fixture_usage is not None and self.fixture_usage.source_receipt_id != self.receipt_id):
            raise SimulationControlError('SIMULATION_USAGE_ORIGIN_INVALID')
        return self


class SimulationSnapshot(DomainModel):
    """One parent run plus both authoritative account snapshots, supplied by caller.

    Account totals may include other runs. The repository must lock/CAS the shared
    enterprise account as well as this parent and all changed invocation records.
    """

    schema_version: Literal['1.0.0']
    scope: Literal['PURE_SIMULATION_POLICY_COMPONENT']
    tenant_id: WireId
    parent_run_id: WireId
    counter: CounterIdentity
    enterprise_quota: SimulationQuota
    parent_quota: SimulationQuota
    revision: PositiveInt
    invocations: tuple[SimulationInvocation, ...]
    production_eligible: Literal[False] = False

    @model_validator(mode='after')
    def coherent(self) -> Self:
        if self.enterprise_quota.account_id == self.parent_quota.account_id:
            raise SimulationControlError('SIMULATION_QUOTA_SCOPE_INVALID')
        identities, actions = set(), set()
        held_units = spent_units = held_calls = spent_calls = 0
        for invocation in self.invocations:
            binding = invocation.binding
            identity = binding.identity
            action = (identity.run_id, identity.step_id, identity.action_id)
            if (identity.tenant_id != self.tenant_id or identity.run_id != self.parent_run_id
                    or binding.exact_input.counter != self.counter or identity.invocation_id in identities or action in actions):
                raise SimulationControlError('SIMULATION_LEDGER_BINDING_INVALID')
            identities.add(identity.invocation_id)
            actions.add(action)
            if invocation.reservation_state == 'HELD':
                held_units = _sum(held_units, invocation.reserved_units)
                held_calls += 1
            elif invocation.reservation_state == 'CONSUMED':
                spent_units = _sum(spent_units, invocation.charged_units)
                spent_calls += 1
        for account in (self.enterprise_quota, self.parent_quota):
            if (held_units > account.reserved_units or held_calls > account.reserved_calls
                    or spent_units > account.spent_units or spent_calls > account.spent_calls):
                raise SimulationControlError('SIMULATION_QUOTA_LEDGER_MISMATCH')
        return self


Decision = Literal['RESERVED', 'DISPATCH_CANDIDATE', 'NO_DISPATCH', 'OBSERVED', 'RECEIPT_RECORDED', 'REPLAY', 'CANCEL_REQUESTED', 'RELEASED_UNSENT']


class SimulationTransition(DomainModel):
    expected_snapshot_sha256: Hash
    next_snapshot: SimulationSnapshot
    decision: Decision
    dispatch_candidate: bool
    requires_atomic_commit: Literal[True] = True

    @model_validator(mode='after')
    def candidate(self) -> Self:
        if self.dispatch_candidate != (self.decision == 'DISPATCH_CANDIDATE'):
            raise SimulationControlError('SIMULATION_TRANSITION_INVALID')
        return self


def _sum(left: int, right: int) -> int:
    total = left + right
    if total > JSON_INTEGER_MAX:
        raise SimulationControlError('SIMULATION_UNIT_OVERFLOW')
    return total


def _hash(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def _uuid_properties(schema: dict) -> dict:
    # WireId validates in Python via AfterValidator. Export the same syntax;
    # otherwise generated structural schemas would silently allow arbitrary IDs.
    names = {
        'SimulationIdentity': tuple(SimulationIdentity.model_fields),
        'SimulationBinding': ('case_id', 'case_receipt_id'),
        'SimulationQuota': ('account_id',),
        'SimulationInvocation': ('receipt_id',),
        'SimulationSnapshot': ('tenant_id', 'parent_run_id'),
        'ModelResult': ('invocation_id', 'context_manifest_id', 'model_config_revision', 'replay_state_id'),
        'Usage': ('source_receipt_id',),
    }
    for definition, fields in names.items():
        model = schema.get('$defs', {}).get(definition)
        if model is None:
            continue
        for field in fields:
            item = model['properties'][field]
            for branch in item.get('anyOf', [item]):
                if branch.get('type') == 'string':
                    branch['format'] = 'uuid'
                    branch['pattern'] = '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    return schema


def measurement_schema() -> dict:
    """Counter contract includes both exact projection schemas and the algorithm."""
    schema = TypeAdapter(ExactInput | ModelResult).json_schema()
    schema['$schema'] = 'https://json-schema.org/draft/2020-12/schema'
    schema['$id'] = 'urn:medical:simulation-byte-measurement:1.0.0'
    schema['description'] = 'Count bytes of RFC8785 UTF-8 canonical JSON, without BOM, newline or Unicode normalization. These are local simulation units, never provider tokens.'
    return _uuid_properties(schema)


def current_counter() -> CounterIdentity:
    # Reading this module's own source is provenance only, never an authorization.
    # Installed wheels contain the same .py bytes. Missing source fails closed.
    return CounterIdentity(unit='CANONICAL_UTF8_BYTE', revision='LOCAL_SIMULATION_CANONICAL_UTF8_BYTE_V1',
        implementation_sha256=_hash(Path(__file__).read_bytes()),
        measurement_schema_sha256=_hash(rfc8785.dumps(measurement_schema())))


def _measure(kind: Literal['EXACT_INPUT', 'MODEL_RESULT'], value: DomainModel) -> Measurement:
    raw = rfc8785.dumps(value.model_dump(mode='json'))
    return Measurement(kind=kind, counter=current_counter(), content_sha256=_hash(raw), units=len(raw))


def measure_input(value: ExactInput) -> Measurement:
    return _measure('EXACT_INPUT', ExactInput.model_validate(value.model_dump(mode='python')))


def measure_result(value: ModelResult) -> Measurement:
    return _measure('MODEL_RESULT', ModelResult.model_validate(value.model_dump(mode='python')))


def snapshot_sha256(snapshot: SimulationSnapshot) -> str:
    return _hash(rfc8785.dumps(snapshot.model_dump(mode='json')))


def _checked(snapshot: SimulationSnapshot) -> SimulationSnapshot:
    snapshot = SimulationSnapshot.model_validate(snapshot.model_dump(mode='python'))
    if snapshot.counter != current_counter():
        raise SimulationControlError('SIMULATION_COUNTER_REVISION_MISMATCH')
    return snapshot


def _changed(value: DomainModel, **changes):
    return type(value).model_validate({**value.model_dump(mode='python'), **changes})


def _find(snapshot: SimulationSnapshot, invocation_id: str) -> SimulationInvocation:
    values = [item for item in snapshot.invocations if item.binding.identity.invocation_id == invocation_id]
    if len(values) != 1:
        raise SimulationControlError('SIMULATION_INVOCATION_NOT_FOUND')
    return values[0]


def _proposal(before: SimulationSnapshot, decision: Decision, invocation: SimulationInvocation | None = None,
              accounts: tuple[SimulationQuota, SimulationQuota] | None = None) -> SimulationTransition:
    after = before
    if invocation is not None:
        items = list(before.invocations)
        matches = [index for index, item in enumerate(items) if item.binding.identity.invocation_id == invocation.binding.identity.invocation_id]
        if matches:
            items[matches[0]] = invocation
        else:
            items.append(invocation)
        enterprise, parent = accounts or (before.enterprise_quota, before.parent_quota)
        after = _changed(before, revision=_sum(before.revision, 1), invocations=tuple(items), enterprise_quota=enterprise, parent_quota=parent)
    return SimulationTransition(expected_snapshot_sha256=snapshot_sha256(before), next_snapshot=after,
        decision=decision, dispatch_candidate=decision == 'DISPATCH_CANDIDATE')


def reserve(snapshot: SimulationSnapshot, binding: SimulationBinding, exact_input: ExactInput) -> SimulationTransition:
    snapshot = _checked(snapshot)
    binding = SimulationBinding.model_validate(binding.model_dump(mode='python'))
    if measure_input(exact_input) != binding.exact_input:
        raise SimulationControlError('SIMULATION_INPUT_MEASUREMENT_MISMATCH')
    identity = binding.identity
    if (identity.tenant_id != snapshot.tenant_id or identity.run_id != snapshot.parent_run_id
            or binding.exact_input.counter != snapshot.counter):
        raise SimulationControlError('SIMULATION_LEDGER_BINDING_INVALID')
    for prior in snapshot.invocations:
        original = prior.binding.identity
        if (original.run_id, original.step_id, original.action_id) == (identity.run_id, identity.step_id, identity.action_id):
            if prior.binding != binding:
                raise SimulationControlError('SIMULATION_IDEMPOTENCY_CONFLICT')
            return _proposal(snapshot, 'REPLAY')
        if original.invocation_id == identity.invocation_id:
            raise SimulationControlError('SIMULATION_IDEMPOTENCY_CONFLICT')
    units = _sum(binding.exact_input.units, binding.caps.maximum_output_units)
    accounts = []
    for account in (snapshot.enterprise_quota, snapshot.parent_quota):
        if (_sum(_sum(account.spent_units, account.reserved_units), units) > account.maximum_units
                or _sum(_sum(account.spent_calls, account.reserved_calls), 1) > account.maximum_calls):
            raise SimulationControlError('SIMULATION_QUOTA_EXCEEDED')
        accounts.append(_changed(account, reserved_units=_sum(account.reserved_units, units), reserved_calls=_sum(account.reserved_calls, 1)))
    invocation = SimulationInvocation(binding=binding, status='RESERVED', revision=1, dispatch_generation=0,
        reservation_state='HELD', reserved_units=units, charged_units=None, output=None, receipt_id=None,
        provenance_sha256=None, fixture_usage=None, usage_origin='UNKNOWN')
    return _proposal(snapshot, 'RESERVED', invocation, tuple(accounts))


def begin_send(snapshot: SimulationSnapshot, invocation_id: str) -> SimulationTransition:
    snapshot = _checked(snapshot)
    invocation = _find(snapshot, invocation_id)
    if invocation.status != 'RESERVED':
        return _proposal(snapshot, 'NO_DISPATCH')
    invocation = _changed(invocation, status='DISPATCHING', dispatch_generation=1, revision=_sum(invocation.revision, 1))
    return _proposal(snapshot, 'DISPATCH_CANDIDATE', invocation)


def observe_in_flight(snapshot: SimulationSnapshot, invocation_id: str) -> SimulationTransition:
    snapshot = _checked(snapshot)
    invocation = _find(snapshot, invocation_id)
    if invocation.status == 'IN_FLIGHT':
        return _proposal(snapshot, 'REPLAY')
    if invocation.status != 'DISPATCHING':
        raise SimulationControlError('SIMULATION_OBSERVATION_STALE')
    return _proposal(snapshot, 'OBSERVED', _changed(invocation, status='IN_FLIGHT', revision=_sum(invocation.revision, 1)))


def mark_unknown(snapshot: SimulationSnapshot, invocation_id: str) -> SimulationTransition:
    snapshot = _checked(snapshot)
    invocation = _find(snapshot, invocation_id)
    if invocation.status in ('UNKNOWN', 'CANCEL_REQUESTED'):
        return _proposal(snapshot, 'REPLAY')
    if invocation.status not in ('DISPATCHING', 'IN_FLIGHT'):
        raise SimulationControlError('SIMULATION_OBSERVATION_STALE')
    return _proposal(snapshot, 'OBSERVED', _changed(invocation, status='UNKNOWN', revision=_sum(invocation.revision, 1)))


def request_cancel(snapshot: SimulationSnapshot, invocation_id: str) -> SimulationTransition:
    snapshot = _checked(snapshot)
    invocation = _find(snapshot, invocation_id)
    if invocation.status in ('SUCCEEDED', 'FAILED', 'CANCELLED', 'CANCEL_REQUESTED'):
        return _proposal(snapshot, 'REPLAY')
    if invocation.status == 'RESERVED':
        accounts = tuple(_changed(account, reserved_units=account.reserved_units-invocation.reserved_units,
                                  reserved_calls=account.reserved_calls-1)
                         for account in (snapshot.enterprise_quota, snapshot.parent_quota))
        return _proposal(snapshot, 'RELEASED_UNSENT', _changed(invocation, status='CANCELLED', reservation_state='RELEASED',
                         revision=_sum(invocation.revision, 1)), accounts)
    return _proposal(snapshot, 'CANCEL_REQUESTED', _changed(invocation, status='CANCEL_REQUESTED', revision=_sum(invocation.revision, 1)))


def _json_bytes(raw: bytes):
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result:
                raise SimulationControlError('SIMULATION_CONTENT_INVALID')
            result[key] = value
        return result
    def constant(_):
        raise SimulationControlError('SIMULATION_CONTENT_INVALID')
    try:
        value = json.loads(raw.decode('utf-8', errors='strict'), object_pairs_hook=pairs, parse_constant=constant)
        if rfc8785.dumps(value) != raw:
            raise SimulationControlError('SIMULATION_CANONICAL_CONTENT_REQUIRED')
        return value
    except (UnicodeError, json.JSONDecodeError) as error:
        raise SimulationControlError('SIMULATION_CONTENT_INVALID') from error


def record_result(snapshot: SimulationSnapshot, invocation_id: str, result_bytes: bytes, provenance_bytes: bytes,
                  bindings: ModelStreamBindings) -> SimulationTransition:
    """Validate supplied local result evidence and propose accounting completion.

    This does not prove on-disk commitment, authenticated origin, object-store
    registration, or permission to read. The repository must establish those.
    """
    snapshot = _checked(snapshot)
    invocation = _find(snapshot, invocation_id)
    binding = invocation.binding
    if len(result_bytes) > binding.caps.maximum_output_units:
        raise SimulationControlError('SIMULATION_OUTPUT_CAP_EXCEEDED')
    if invocation.status in ('RESERVED', 'CANCELLED'):
        raise SimulationControlError('SIMULATION_RESULT_WITHOUT_DISPATCH')
    result = bindings.bind(_json_bytes(result_bytes), 'ModelResult').read()
    proof = SimulationProvenance.model_validate(_json_bytes(provenance_bytes))
    case = SimulationCase(schema_version='1.0.0', input_data_kind='SIMULATION', case_id=binding.case_id,
        receipt_id=binding.case_receipt_id, settings_sha256=binding.settings_sha256,
        input_sha256=binding.exact_input.content_sha256, parameters_sha256=binding.parameters_sha256,
        blocks=result.blocks, reason=result.reason, usage=result.usage)
    if (any(getattr(proof, name) != getattr(binding.identity, name) for name in SimulationIdentity.model_fields)
            or proof.case_id != binding.case_id or proof.case_sha256 != binding.case_sha256 or content_hash(case) != binding.case_sha256
            or proof.receipt_id != binding.case_receipt_id or proof.request_sha256 != binding.local_request_sha256
            or proof.settings_sha256 != binding.settings_sha256 or proof.parameters_sha256 != binding.parameters_sha256
            or proof.input_sha256 != binding.exact_input.content_sha256 or proof.model_result_sha256 != _hash(result_bytes)
            or proof.model_result_schema_ref != bindings.catalogue.schema_ref('model-stream', 'ModelResult')
            or proof.model_result_schema_sha256 != bindings.catalogue.digests['model-stream']
            or result.invocation_id != invocation_id or result.context_manifest_id != binding.identity.context_manifest_id
            or result.model_config_revision != binding.identity.model_config_revision
            or result.provider_request_id != 'SIMULATION:' + binding.case_id or result.replay_state_id is not None
            or proof.usage_origin != ('UNKNOWN' if result.usage is None else 'SIMULATION_FIXTURE')
            or result.usage is not None and result.usage.source_receipt_id != proof.receipt_id
            or result.reason.kind == 'aborted'):
        raise SimulationControlError('SIMULATION_RESULT_BINDING_MISMATCH')
    output = measure_result(result)
    if output.content_sha256 != _hash(result_bytes):
        raise SimulationControlError('SIMULATION_RESULT_BINDING_MISMATCH')
    if invocation.status in ('SUCCEEDED', 'FAILED'):
        if invocation.output != output or invocation.provenance_sha256 != _hash(provenance_bytes):
            raise SimulationControlError('SIMULATION_RECEIPT_CONFLICT')
        return _proposal(snapshot, 'REPLAY')
    units = _sum(binding.exact_input.units, output.units)
    accounts = tuple(_changed(account, reserved_units=account.reserved_units-invocation.reserved_units,
                              reserved_calls=account.reserved_calls-1, spent_units=_sum(account.spent_units, units),
                              spent_calls=_sum(account.spent_calls, 1))
                     for account in (snapshot.enterprise_quota, snapshot.parent_quota))
    invocation = _changed(invocation, status='FAILED' if result.reason.kind == 'error' else 'SUCCEEDED',
        revision=_sum(invocation.revision, 1), reservation_state='CONSUMED', charged_units=units, output=output,
        receipt_id=proof.receipt_id, provenance_sha256=_hash(provenance_bytes), fixture_usage=result.usage,
        usage_origin=proof.usage_origin)
    return _proposal(snapshot, 'RECEIPT_RECORDED', invocation, accounts)


def control_schema() -> dict:
    schema = TypeAdapter(SimulationBinding | SimulationSnapshot | SimulationTransition).json_schema()
    schema['$schema'] = 'https://json-schema.org/draft/2020-12/schema'
    schema['$id'] = 'urn:medical:simulation-control-component:1.0.0'
    schema['description'] = 'Pure local simulation policy proposals. No authorization, persistence, provider token accounting or production eligibility.'
    return deepcopy(_uuid_properties(schema))

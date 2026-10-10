import json
import unittest
import uuid

from medical_contracts.model_stream import RequestParameters
from medical_model_gateway.protocol import ExactInput, ProviderSettings
from medical_model_gateway.simulation import SimulationCase, SimulationProvider, content_hash


class ModelSimulationTests(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.settings = ProviderSettings(model_id='SIMULATION:medical-agent', model_version='SIMULATION:1.0.0',
            max_output_tokens=2048, deadline_seconds=30, temperature_enabled=False, reasoning_efforts=['none'], stop_limit=0)
        self.parameters = RequestParameters(max_output_tokens=1024, temperature=None, reasoning_effort='none', stop=[], purpose='TASK')
        self.input = ExactInput(messages=[{'role': 'user', 'content': '模拟企业A：使用已确认资料生成候选。'}], tools=[])
        self.receipt_id = str(uuid.uuid4())

    def case(self, blocks, kind='stop'):
        return SimulationCase(schema_version='1.0.0', input_data_kind='SIMULATION', case_id=str(uuid.uuid4()),
            receipt_id=self.receipt_id, settings_sha256=content_hash(self.settings), input_sha256=content_hash(self.input),
            parameters_sha256=content_hash(self.parameters), blocks=blocks, reason={'kind': kind, 'failure': None}, usage=None)

    async def test_full_text_is_explicitly_simulated_and_preserves_complete_event_content(self):
        text = '【模拟数据】医疗设备企业A\n型号：MODEL-A；量程：0.001—99.999\n完整候选内容。'
        case = self.case([{'type': 'text', 'text': text}])
        values = [value async for value in SimulationProvider(case).stream(self.settings, self.parameters, self.input, self.receipt_id)]
        self.assertEqual([value.type for value in values[:-1]], ['block_start', 'text_delta', 'block_end'])
        self.assertEqual(values[1].text, text)
        self.assertEqual(values[-1].blocks[0].text, text)
        self.assertEqual(values[-1].input_data_kind, 'SIMULATION')
        self.assertEqual(values[-1].provider_request_id, 'SIMULATION:' + case.case_id)
        self.assertIsNone(values[-1].usage)

    async def test_changed_input_parameters_identity_or_model_is_rejected_before_events(self):
        case = self.case([{'type': 'text', 'text': '【模拟数据】完整内容'}])
        other_input = ExactInput(messages=[{'role': 'user', 'content': '不同输入'}], tools=[])
        other_parameters = self.parameters.model_copy(update={'max_output_tokens': 1000})
        other_settings = self.settings.model_copy(update={'model_id': 'deepseek-flash'})
        for settings, parameters, exact_input, receipt in (
            (self.settings, self.parameters, other_input, self.receipt_id),
            (self.settings, other_parameters, self.input, self.receipt_id),
            (other_settings, self.parameters, self.input, self.receipt_id),
            (self.settings, self.parameters, self.input, str(uuid.uuid4())),
        ):
            with self.assertRaises(ValueError):
                await anext(SimulationProvider(case).stream(settings, parameters, exact_input, receipt))

    async def test_complete_tool_arguments_are_validated_against_actual_published_schema(self):
        self.input = ExactInput(messages=[{'role': 'user', 'content': '模拟检索'}], tools=[{'type': 'function', 'function': {
            'name': 'read_sources', 'description': '读取已授权资料', 'parameters': {'type': 'object', 'additionalProperties': False,
                'required': ['source_id'], 'properties': {'source_id': {'type': 'string', 'minLength': 1}}}}}])
        blocks = [{'type': 'tool-call', 'id': 'simulation-tool-1', 'name': 'read_sources', 'arguments': json.dumps({'source_id': 'simulation-source-A'})}]
        case = self.case(blocks, 'tool-calls')
        values = [value async for value in SimulationProvider(case).stream(self.settings, self.parameters, self.input, self.receipt_id)]
        self.assertEqual(values[-1].blocks[0].arguments, blocks[0]['arguments'])
        self.assertEqual(values[-1].reason.kind, 'tool-calls')
        invalid_case = self.case([{**blocks[0], 'arguments': '{}'}], 'tool-calls')
        with self.assertRaises(ValueError):
            await anext(SimulationProvider(invalid_case).stream(self.settings, self.parameters, self.input, self.receipt_id))


if __name__ == '__main__':
    unittest.main()

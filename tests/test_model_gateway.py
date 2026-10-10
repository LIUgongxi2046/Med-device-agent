import asyncio
from decimal import Decimal
import logging
import os
from pathlib import Path
import secrets
import stat
import unittest
import uuid

from pydantic import ValidationError

from medical_model_gateway import credentials
from medical_model_gateway import pricing
from medical_model_gateway.protocol import ExactInput, ProviderSettings
from medical_model_gateway.provider import DeepSeekProvider


ROOT = Path(__file__).resolve().parents[1]


class CredentialStorageTests(unittest.TestCase):
    def setUp(self):
        self.directory = ROOT / ".implementation-work/model-credential-tests" / str(uuid.uuid4())
        self.directory.mkdir(parents=True, mode=0o700)
        self.key_path = self.directory / "deepseek-api-key"

    def tearDown(self):
        for path in self.directory.iterdir():
            path.unlink()
        self.directory.rmdir()

    def test_given_missing_file_when_prepared_then_private_empty_file_is_created(self):
        credentials.prepare_key_file(self.key_path)
        self.assertEqual(self.key_path.read_bytes(), b"")
        self.assertEqual(stat.S_IMODE(self.key_path.stat().st_mode), 0o600)
        with self.assertRaisesRegex(ValueError, "密钥尚未配置"):
            credentials.read_key(self.key_path)

    def test_given_existing_file_when_prepared_then_original_bytes_are_preserved(self):
        credentials.prepare_key_file(self.key_path)
        original = self.key_path.stat()
        with self.assertRaises(FileExistsError):
            credentials.prepare_key_file(self.key_path)
        self.assertEqual(original.st_ino, self.key_path.stat().st_ino)

    def test_given_real_generated_storage_secret_when_read_then_repr_is_redacted(self):
        # 本地生成的技术秘密仅验证存储，不发送供应商请求。
        value = secrets.token_urlsafe(48)
        descriptor = os.open(self.key_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(descriptor, "w", encoding="ascii") as output:
            output.write(value + "\n")
        loaded = credentials.read_key(self.key_path)
        self.assertEqual(loaded.get_secret_value(), value)
        self.assertNotIn(value, repr(loaded))

    def test_given_public_parent_when_prepare_then_creation_is_refused(self):
        self.directory.chmod(0o755)
        with self.assertRaises(PermissionError):
            credentials.prepare_key_file(self.key_path)
        self.assertFalse(self.key_path.exists())

    def test_given_public_file_when_read_then_secret_is_refused(self):
        self.key_path.touch(mode=0o644)
        self.key_path.chmod(0o644)
        with self.assertRaises(PermissionError):
            credentials.read_key(self.key_path)

    def test_given_symbolic_link_when_read_then_target_is_not_used(self):
        target = self.directory / "target"
        target.touch(mode=0o600)
        self.key_path.symlink_to(target)
        with self.assertRaises(OSError):
            credentials.read_key(self.key_path)

    def test_given_hard_link_when_read_then_shared_secret_is_refused(self):
        self.key_path.touch(mode=0o600)
        os.link(self.key_path, self.directory / "another")
        with self.assertRaises(PermissionError):
            credentials.read_key(self.key_path)

    def test_given_missing_file_when_read_then_missing_configuration_is_reported(self):
        with self.assertRaises(FileNotFoundError):
            credentials.read_key(self.key_path)

    def test_given_multiline_file_when_read_then_format_is_refused(self):
        descriptor = os.open(self.key_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(descriptor, "w", encoding="ascii") as output:
            output.write("\n\n")
        with self.assertRaises(ValueError):
            credentials.read_key(self.key_path)

    def test_given_missing_key_when_client_created_then_configuration_fails_before_network(self):
        with self.assertRaises(FileNotFoundError):
            DeepSeekProvider(self.key_path).client()

    def test_given_storage_secret_when_sdk_created_then_retry_proxy_redirect_are_disabled(self):
        value = secrets.token_urlsafe(48)
        descriptor = os.open(self.key_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(descriptor, "w", encoding="ascii") as output:
            output.write(value)
        logger = logging.getLogger("openai._base_client")
        original_level = logger.level
        logger.setLevel(logging.DEBUG)
        client = DeepSeekProvider(self.key_path).client()
        try:
            self.assertEqual(client.max_retries, 0)
            self.assertEqual(str(client.base_url), DeepSeekProvider.BASE_URL)
            self.assertFalse(client._client.trust_env)
            self.assertFalse(client._client.follow_redirects)
            self.assertEqual(client._client.timeout.connect, 5)
            self.assertEqual(client._client.timeout.read, 60)
            self.assertFalse(logging.getLogger("openai._base_client").isEnabledFor(logging.DEBUG))
        finally:
            asyncio.run(client.close())
            logger.setLevel(original_level)


class PublishInputTests(unittest.TestCase):
    def test_given_missing_publish_limits_when_validated_then_unlimited_defaults_are_refused(self):
        with self.assertRaises(ValidationError):
            ProviderSettings.model_validate({})

    def test_given_empty_messages_when_validated_then_generation_is_refused(self):
        with self.assertRaises(ValidationError):
            ExactInput.model_validate({"messages": [], "tools": []})

    def test_given_caller_endpoint_when_validated_then_unpublished_url_is_refused(self):
        with self.assertRaises(ValidationError) as result:
            ExactInput.model_validate({"messages": [], "tools": [], "base_url": "http://127.0.0.1"})
        self.assertIn("extra_forbidden", {error["type"] for error in result.exception.errors()})


class PriceCalculationTests(unittest.TestCase):
    def test_given_published_million_token_rates_when_reserved_then_full_upper_amount_is_preserved(self):
        # 仅检查百万Token单位的十进制计算，不创建价格批准或供应商用量回执。
        amount = pricing.upper_amount("0.30000000", "0.00600000", "1.20000000", 1_000_000, 1_000_000,
                                      cache_write_rate="0.30000000")
        self.assertEqual(amount, "1.50000000")

    def test_given_fractional_currency_when_reserved_then_rounding_preserves_upper_bound(self):
        amount = pricing.upper_amount("0.00600000", "0.00600000", "0.00600000", 1, 1,
                                      cache_write_rate="0.00600000")
        self.assertGreaterEqual(Decimal(amount), Decimal("0.006") * 2 / Decimal(1_000_000))
        self.assertEqual(amount, "0.00000002")

    def test_given_oversized_currency_when_reserved_then_storage_overflow_is_refused(self):
        with self.assertRaises(ValueError):
            pricing.upper_amount("9999999999999999.00000000", "0.00000000", "0.00000000", (1 << 53) - 1, 1,
                                 cache_write_rate="0.00000000")

    def test_given_negative_rate_when_reserved_then_invalid_price_is_refused(self):
        with self.assertRaises(ValueError):
            pricing.upper_amount("-0.30000000", "0.00600000", "1.20000000", 1, 1,
                                 cache_write_rate="0.30000000")

    def test_given_cache_write_price_when_reserved_then_highest_input_category_is_covered(self):
        # 十进制单位计算使用数学输入，不创建价格批准、调用或供应商用量记录。
        self.assertEqual(pricing.upper_amount("1.00000000", "2.00000000", "4.00000000", 1_000_000,
                                             1_000_000, cache_write_rate="3.00000000"), "7.00000000")

    def test_given_missing_cache_write_price_when_reserved_then_incomplete_price_is_refused(self):
        with self.assertRaises(TypeError):
            pricing.upper_amount("1.00000000", "2.00000000", "4.00000000", 1, 1)

    def test_given_disjoint_counts_when_calculated_then_each_category_is_charged_once(self):
        self.assertEqual(pricing.actual_amount("1.00000000", "2.00000000", "3.00000000", "4.00000000",
                                               1, 2, 3, 4, rounding="ROUND_HALF_EVEN"), "0.00003000")

    def test_given_unknown_paid_category_when_calculated_then_amount_remains_unknown(self):
        for index in range(4):
            counts = [1, 2, 3, 4]
            counts[index] = None
            self.assertIsNone(pricing.actual_amount("1.00000000", "2.00000000", "3.00000000", "4.00000000",
                                                   *counts, rounding="ROUND_HALF_EVEN"))

    def test_given_zero_price_category_when_count_unknown_then_amount_is_exact_without_count_replacement(self):
        self.assertEqual(pricing.actual_amount("1.00000000", "2.00000000", "0.00000000", "4.00000000",
                                               1, 2, None, 4, rounding="ROUND_HALF_EVEN"), "0.00002100")

    def test_given_fractional_amount_when_calculated_then_explicit_rounding_policy_is_applied(self):
        self.assertEqual(pricing.actual_amount("0.00500000", "0.00000000", "0.00000000", "0.00000000",
                                               1, 0, 0, 0, rounding="ROUND_HALF_EVEN"), "0.00000000")
        self.assertEqual(pricing.actual_amount("0.00500000", "0.00000000", "0.00000000", "0.00000000",
                                               1, 0, 0, 0, rounding="ROUND_HALF_UP"), "0.00000001")

    def test_given_missing_or_unknown_rounding_when_calculated_then_policy_is_required(self):
        with self.assertRaises(TypeError):
            pricing.actual_amount("0.00000000", "0.00000000", "0.00000000", "0.00000000", 0, 0, 0, 0)
        with self.assertRaisesRegex(ValueError, "舍入规则"):
            pricing.actual_amount("0.00000000", "0.00000000", "0.00000000", "0.00000000", 0, 0, 0, 0,
                                  rounding="UNKNOWN")

    def test_given_invalid_count_with_unknown_category_when_calculated_then_error_is_not_hidden(self):
        for value in (-1, True, 1.0, (1 << 53)):
            with self.assertRaises(ValidationError):
                pricing.actual_amount("1.00000000", "1.00000000", "1.00000000", "1.00000000",
                                      None, value, 0, 0, rounding="ROUND_HALF_EVEN")

    def test_given_storage_overflow_when_actual_amount_calculated_then_full_amount_is_refused(self):
        with self.assertRaises(ValueError):
            pricing.actual_amount("9999999999999999.00000000", "0.00000000", "0.00000000", "0.00000000",
                                  (1 << 53) - 1, 0, 0, 0, rounding="ROUND_HALF_EVEN")

    def test_given_invalid_price_when_unknown_usage_then_price_error_is_reported(self):
        with self.assertRaises(ValueError):
            pricing.actual_amount("-1.00000000", "0.00000000", "0.00000000", "0.00000000",
                                  None, None, None, None, rounding="ROUND_HALF_EVEN")


if __name__ == "__main__":
    unittest.main(verbosity=2)

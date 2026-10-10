import json
from pathlib import Path
import sys
import unittest
import uuid


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
sys.path.insert(0, str(ROOT / "tests"))
from local_runtime import mysql, settings, verify_application_deployment
from test_local_identity_runtime import ActualBrowserSession, ORIGIN


class EnterpriseRegistrationRuntimeChecks(unittest.TestCase):
    def setUp(self):
        self.deployment = verify_application_deployment()
        self.owner = json.loads((ROOT / ".implementation-work/runtime/owner-credentials.json").read_bytes())
        self.browser = ActualBrowserSession()
        status, _, _ = self.browser.request("POST", "/auth/login", self.owner, ORIGIN, self.browser.csrf())
        self.assertEqual(status, 302)
        self.csrf = self.browser.csrf()

    def tearDown(self):
        status, _, _ = self.browser.request("POST", "/auth/logout", origin=ORIGIN, csrf=self.browser.csrf())
        self.assertEqual(status, 204)

    def test_actual_administrator_can_read_owned_registration_queue(self):
        status, headers, content = self.browser.request("GET", "/api/enterprise-registrations", schema="1.0.0")
        self.assertEqual(status, 200)
        response = json.loads(content)
        self.assertEqual(set(response), {"schema_version", "request_id", "resource_revision", "data"})
        self.assertEqual(headers["X-Request-Id"], response["request_id"])
        self.assertEqual(headers["Cache-Control"], "no-store")
        self.assertEqual(response["data"], {"schema_version": "1.0.0", "items": [], "next_cursor": None})

    def test_actual_administrator_can_read_registration_review_queue(self):
        for state in ("PENDING_REVIEW", "APPROVED", "REJECTED"):
            status, headers, content = self.browser.request("GET", "/api/enterprise-registration-reviews?status=" + state, schema="1.0.0")
            self.assertEqual(status, 200)
            response = json.loads(content)
            self.assertEqual(headers["Cache-Control"], "no-store")
            self.assertEqual(response["data"], {"schema_version": "1.0.0", "items": [], "next_cursor": None})

    def test_unknown_registration_review_is_denied_before_decision_body(self):
        identifier = str(uuid.uuid4())
        status, _, content = self.browser.request("GET", "/api/enterprise-registration-reviews/" + identifier, schema="1.0.0")
        self.assertEqual(status, 403)
        self.assertEqual(json.loads(content)["code"], "ACCESS_DENIED")
        for _ in range(2):
            status, _, content = self.browser.request("POST", "/api/enterprise-registration-reviews/" + identifier + "/decisions",
                json_body={}, origin=ORIGIN, csrf=self.csrf, schema="1.0.0", key=str(uuid.uuid4()))
            self.assertEqual(status, 403)
            self.assertEqual(json.loads(content)["code"], "ACCESS_DENIED")

    def test_review_query_is_closed_and_versioned(self):
        for suffix, version, code in (("", None, "SCHEMA_UNSUPPORTED"), ("?status=UNKNOWN", "1.0.0", "SCHEMA_INVALID"),
                ("?limit=0", "1.0.0", "SCHEMA_INVALID"), ("?status=APPROVED&status=REJECTED", "1.0.0", "SCHEMA_INVALID"),
                ("?tenant_id=" + str(uuid.uuid4()), "1.0.0", "SCHEMA_INVALID")):
            status, _, content = self.browser.request("GET", "/api/enterprise-registration-reviews" + suffix, schema=version)
            self.assertEqual(status, 422)
            self.assertEqual(json.loads(content)["code"], code)

    def test_review_decisions_preserve_zero_business_records_and_csrf(self):
        identifier = str(uuid.uuid4())
        for origin, csrf in ((ORIGIN, None), ("null", self.csrf)):
            status, _, _ = self.browser.request("POST", "/api/enterprise-registration-reviews/" + identifier + "/decisions",
                json_body={}, origin=origin, csrf=csrf, schema="1.0.0", key=str(uuid.uuid4()))
            self.assertEqual(status, 403)
        actual = json.loads(mysql("""
            SELECT JSON_OBJECT('decisions',(SELECT COUNT(*) FROM medical_local.platform_enterprise_registration_decision),
              'decision_audits',(SELECT COUNT(*) FROM medical_local.platform_enterprise_registration_decision_audit),
              'tenants',(SELECT COUNT(*) FROM medical_local.tenant),'members',(SELECT COUNT(*) FROM medical_local.tenant_member),
              'bindings',(SELECT COUNT(*) FROM medical_local.identity_binding),'routes',(SELECT COUNT(*) FROM medical_local.platform_member_route),
              'grants',(SELECT COUNT(*) FROM medical_local.permission_grant),'outbox',(SELECT COUNT(*) FROM medical_local.outbox_event))
            """, settings()["root_password"]).stdout.strip())
        self.assertEqual(actual, dict.fromkeys(("decisions", "decision_audits", "tenants", "members", "bindings", "routes", "grants", "outbox"), 0))

    def test_actual_review_constraints_preserve_unique_approval_and_complete_audit(self):
        actual = json.loads(mysql("""
            SELECT JSON_OBJECT('tables',(SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='medical_local'
              AND table_name IN ('platform_enterprise_registration_decision','platform_enterprise_registration_decision_audit')),
              'company_columns',(SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics
                WHERE table_schema='medical_local' AND table_name='platform_enterprise_registration_decision' AND index_name='uq_registration_approved_company'),
              'audit_reference_columns',(SELECT COUNT(*) FROM information_schema.key_column_usage WHERE table_schema='medical_local'
                AND table_name='platform_enterprise_registration_decision_audit' AND referenced_table_name='platform_enterprise_registration_decision'),
              'constraints',(SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema='medical_local'
                AND table_name IN ('platform_enterprise_registration_decision','platform_enterprise_registration_decision_audit') AND constraint_type='CHECK'))
            """, settings()["root_password"]).stdout.strip())
        self.assertEqual(actual, {"tables": 2, "company_columns": "approved_company_sha256", "audit_reference_columns": 3, "constraints": 7})

    def test_missing_enterprise_data_fails_without_creating_business_records(self):
        for _ in range(2):
            status, _, content = self.browser.request("POST", "/api/enterprise-registrations", json_body={},
                origin=ORIGIN, csrf=self.csrf, schema="1.0.0", key=str(uuid.uuid4()))
            self.assertEqual(status, 422)
            self.assertEqual(json.loads(content)["code"], "SCHEMA_INVALID")
        actual = json.loads(mysql("""
            SELECT JSON_OBJECT('registrations',(SELECT COUNT(*) FROM medical_local.platform_enterprise_registration),
              'audits',(SELECT COUNT(*) FROM medical_local.platform_enterprise_registration_audit),
              'tenants',(SELECT COUNT(*) FROM medical_local.tenant),
              'members',(SELECT COUNT(*) FROM medical_local.tenant_member),
              'grants',(SELECT COUNT(*) FROM medical_local.permission_grant))
            """, settings()["root_password"]).stdout.strip())
        self.assertEqual(actual, {"registrations": 0, "audits": 0, "tenants": 0, "members": 0, "grants": 0})

    def test_header_pagination_and_unowned_identifier_are_rejected(self):
        for path, version, expected in (("/api/enterprise-registrations", None, "SCHEMA_UNSUPPORTED"),
                ("/api/enterprise-registrations?limit=0", "1.0.0", "SCHEMA_INVALID"),
                ("/api/enterprise-registrations?limit=1&limit=2", "1.0.0", "SCHEMA_INVALID"),
                ("/api/enterprise-registrations?tenant_id=" + str(uuid.uuid4()), "1.0.0", "SCHEMA_INVALID")):
            status, _, content = self.browser.request("GET", path, schema=version)
            self.assertEqual(status, 422)
            self.assertEqual(json.loads(content)["code"], expected)
        status, _, content = self.browser.request("GET", "/api/enterprise-registrations/" + str(uuid.uuid4()), schema="1.0.0")
        self.assertEqual(status, 403)
        self.assertEqual(json.loads(content)["code"], "ACCESS_DENIED")

    def test_csrf_and_origin_protect_registration_submission(self):
        for origin, csrf in ((ORIGIN, None), ("null", self.csrf)):
            status, _, _ = self.browser.request("POST", "/api/enterprise-registrations", json_body={},
                origin=origin, csrf=csrf, schema="1.0.0", key=str(uuid.uuid4()))
            self.assertEqual(status, 403)

    def test_account_page_exposes_actual_registration_entry(self):
        status, _, content = self.browser.request("GET", "/local-auth/account.html")
        self.assertEqual(status, 200)
        self.assertIn(b'data-enterprise-registration', content)
        status, _, content = self.browser.request("GET", "/local-auth/enterprise-registration.html")
        self.assertEqual(status, 200)
        self.assertIn("企业登记申请".encode(), content)

    def test_actual_registration_tables_preserve_deduplication_and_linked_audit(self):
        actual = json.loads(mysql("""
            SELECT JSON_OBJECT('tables',(SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='medical_local'
              AND table_name IN ('platform_enterprise_registration','platform_enterprise_registration_audit')),
              'company_columns',(SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics
              WHERE table_schema='medical_local' AND table_name='platform_enterprise_registration' AND index_name='uq_enterprise_registration_company'),
              'request_columns',(SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics
              WHERE table_schema='medical_local' AND table_name='platform_enterprise_registration' AND index_name='uq_enterprise_registration_request'),
              'audit_reference_columns',(SELECT COUNT(*) FROM information_schema.key_column_usage WHERE table_schema='medical_local'
              AND table_name='platform_enterprise_registration_audit' AND referenced_table_name='platform_enterprise_registration'),
              'constraints',(SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema='medical_local'
              AND table_name IN ('platform_enterprise_registration','platform_enterprise_registration_audit') AND constraint_type='CHECK'))
            """, settings()["root_password"]).stdout.strip())
        self.assertEqual(actual, {"tables": 2, "company_columns": "subject_id,company_sha256", "request_columns": "subject_id,idempotency_key",
                                "audit_reference_columns": 4, "constraints": 8})


if __name__ == "__main__":
    unittest.main(verbosity=2)

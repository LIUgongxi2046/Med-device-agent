import json
from pathlib import Path
import sys
import unittest


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
sys.path.insert(0, str(ROOT / "tests"))
from local_runtime import mysql, settings
from test_local_identity_runtime import ActualBrowserSession, ORIGIN


class MemberInvitationFlowRuntimeChecks(unittest.TestCase):
    def setUp(self):
        self.browser = ActualBrowserSession()
        owner = json.loads((ROOT / ".implementation-work/runtime/owner-credentials.json").read_bytes())
        status, _, _ = self.browser.request("POST", "/auth/login", owner, ORIGIN, self.browser.csrf())
        self.assertEqual(status, 302)
        status, _, content = self.browser.request("GET", "/api/session", schema="1.0.0")
        self.assertEqual(status, 200)
        self.session = json.loads(content)["data"]
        self.assertIsNone(self.session["current_context"])
        self.assertEqual(self.session["memberships"], [])

    def tearDown(self):
        status, _, _ = self.browser.request("POST", "/auth/logout", origin=ORIGIN, csrf=self.browser.csrf())
        self.assertEqual(status, 204)

    def submit(self, path="/api/member-invitations", **overrides):
        options = {"json_body": {}, "origin": ORIGIN, "csrf": self.session["csrf"],
                   "schema": "1.0.0", "context": "0", "key": self.session["user"]["user_id"]}
        options.update(overrides)
        return self.browser.request("POST", path, **options)

    def test_creation_requires_current_enterprise_before_reading_business_body(self):
        status, _, content = self.submit()
        self.assertEqual(status, 403)
        self.assertEqual(json.loads(content)["code"], "ACCESS_DENIED")

    def test_acceptance_requires_actual_owned_invitation_route(self):
        status, _, content = self.submit("/api/member-invitations/" + self.session["user"]["user_id"] + "/acceptances")
        self.assertEqual(status, 403)
        self.assertEqual(json.loads(content)["code"], "ACCESS_DENIED")

    def test_schema_and_headers_are_required(self):
        for options, code in (({"schema": None}, "SCHEMA_UNSUPPORTED"), ({"key": None}, "SCHEMA_INVALID"),
                              ({"context": "-1"}, "SCHEMA_INVALID")):
            status, _, content = self.submit(**options)
            self.assertEqual(status, 422)
            self.assertEqual(json.loads(content)["code"], code)

    def test_repeated_denials_preserve_absence_of_customer_records(self):
        for _ in range(2):
            self.assertEqual(self.submit()[0], 403)
        count = mysql("SELECT (SELECT COUNT(*) FROM medical_local.tenant) + "
                      "(SELECT COUNT(*) FROM medical_local.tenant_member) + "
                      "(SELECT COUNT(*) FROM medical_local.identity_binding) + "
                      "(SELECT COUNT(*) FROM medical_local.member_invitation) + "
                      "(SELECT COUNT(*) FROM medical_local.platform_invitation_route) + "
                      "(SELECT COUNT(*) FROM medical_local.operation_record) + "
                      "(SELECT COUNT(*) FROM medical_local.permission_grant)", settings()["root_password"]).stdout.strip()
        self.assertEqual(count, "0")

    def test_csrf_and_origin_are_required(self):
        for options in ({"csrf": None}, {"origin": "null"}):
            self.assertEqual(self.submit(**options)[0], 403)


if __name__ == "__main__":
    unittest.main(verbosity=2)

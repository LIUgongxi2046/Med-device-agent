import json
from pathlib import Path
import sys
import unittest


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from local_runtime import mysql, settings


class TaskStorageRuntimeChecks(unittest.TestCase):
    def query(self, sql):
        return json.loads(mysql(sql, settings()["root_password"]).stdout)

    def columns(self, table):
        self.assertIn(table, {"task_input_revision", "plan_content_revision"})
        result = self.query("SELECT JSON_OBJECT('columns',JSON_ARRAYAGG(JSON_OBJECT('name',COLUMN_NAME,"
            "'type',DATA_TYPE,'nullable',IS_NULLABLE))) FROM information_schema.COLUMNS "
            "WHERE TABLE_SCHEMA='medical_local' AND TABLE_NAME='" + table + "'")
        self.assertIsInstance(result["columns"], list, "固定版本需要实际保存结构")
        return {row["name"]: row for row in result["columns"]}

    def foreign(self, table, constraint):
        self.assertIn(table, {"task_input_revision", "plan_content_revision"})
        result = self.query("SELECT JSON_OBJECT('columns',JSON_ARRAYAGG(JSON_OBJECT('name',COLUMN_NAME,"
            "'target',REFERENCED_TABLE_NAME,'target_column',REFERENCED_COLUMN_NAME,'ordinal',ORDINAL_POSITION))) "
            "FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA='medical_local' "
            "AND TABLE_NAME='" + table + "' AND CONSTRAINT_NAME='" + constraint + "'")
        self.assertIsInstance(result["columns"], list, "固定版本需要完整企业和项目归属")
        return [(row["name"], row["target"], row["target_column"])
            for row in sorted(result["columns"], key=lambda row: row["ordinal"])]

    def test_complete_creation_input_and_scope_have_independent_versioned_storage(self):
        columns = self.columns("task_input_revision")
        self.assertLessEqual({"tenant_id", "project_id", "task_id", "task_revision", "operation_id",
            "document_type", "owner_id", "goal", "based_on_document_revision", "request_json", "request_sha256",
            "scope_json", "scope_sha256", "authority_grant", "authority_revision", "auth_epoch", "governance_epoch",
            "schema_version", "schema_manifest_sha256", "created_by", "created_at", "classification"}, set(columns))
        self.assertEqual(columns["request_json"]["type"], "json")
        self.assertEqual(columns["scope_json"]["type"], "json")
        self.assertEqual(columns["goal"]["type"], "longtext")
        self.assertEqual(columns["based_on_document_revision"]["nullable"], "YES")

    def test_input_is_bound_to_original_task_and_document_in_the_same_project(self):
        self.assertEqual(self.foreign("task_input_revision", "fk_task_input_task"),
            [(name, "task_revision", name) for name in ("tenant_id", "project_id", "task_id", "task_revision")])
        self.assertEqual(self.foreign("task_input_revision", "fk_task_input_document"),
            [("tenant_id", "document_revision", "tenant_id"), ("project_id", "document_revision", "project_id"),
             ("based_on_document_revision", "document_revision", "document_revision")])

    def test_missing_requirements_preserve_full_json_and_independent_digest(self):
        columns = self.columns("task_input_revision")
        self.assertEqual(columns["missing_requirements"]["type"], "json")
        self.assertEqual(columns["missing_requirements"]["nullable"], "NO")
        self.assertEqual(columns["requirements_sha256"]["type"], "char")
        self.assertEqual(columns["requirements_sha256"]["nullable"], "NO")
        result = self.query("SELECT JSON_OBJECT('count',COUNT(*)) FROM information_schema.TABLE_CONSTRAINTS "
            "WHERE TABLE_SCHEMA='medical_local' AND TABLE_NAME='task_input_revision' "
            "AND CONSTRAINT_NAME='ck_task_input_requirements' AND CONSTRAINT_TYPE='CHECK' AND ENFORCED='YES'")
        self.assertEqual(result["count"], 1)

    def test_input_has_actual_operation_actor_owner_and_authority_references(self):
        for constraint, field, table, target in (
            ("fk_task_input_operation", "operation_id", "operation_record", "operation_id"),
            ("fk_task_input_actor", "created_by", "tenant_member", "user_id"),
            ("fk_task_input_owner", "owner_id", "tenant_member", "user_id"),
            ("fk_task_input_authority", "authority_grant", "permission_grant", "grant_id"),
        ):
            with self.subTest(constraint=constraint):
                self.assertEqual(self.foreign("task_input_revision", constraint),
                    [("tenant_id", table, "tenant_id"), (field, table, target)])

    def test_complete_candidate_preserves_original_content_and_draft_basis(self):
        columns = self.columns("plan_content_revision")
        self.assertLessEqual({"tenant_id", "project_id", "task_id", "task_revision", "plan_revision", "template_version",
            "snapshot_id", "content_sha256", "content_json", "base_version", "base_version_sha256", "operation_id",
            "expected_revision", "reason", "schema_ref", "schema_version", "schema_manifest_sha256", "authority_grant",
            "authority_revision", "auth_epoch", "governance_epoch", "created_by", "created_at", "classification"}, set(columns))
        self.assertEqual(columns["content_json"]["type"], "json")
        for name in ("base_version", "base_version_sha256"):
            self.assertEqual(columns[name]["nullable"], "YES")

    def test_plan_and_parent_bind_the_same_task_and_exact_original_digest(self):
        self.assertEqual(self.foreign("plan_content_revision", "fk_plan_content_basis"),
            [(name, "plan_revision", name) for name in ("tenant_id", "project_id", "task_id", "task_revision",
                "plan_revision", "template_version", "snapshot_id", "content_sha256")])
        self.assertEqual(self.foreign("plan_content_revision", "fk_plan_content_parent"),
            [("tenant_id", "plan_revision", "tenant_id"), ("project_id", "plan_revision", "project_id"),
             ("task_id", "plan_revision", "task_id"), ("base_version", "plan_revision", "plan_revision"),
             ("base_version_sha256", "plan_revision", "content_sha256")])

    def test_metadata_and_version_constraints_are_actually_enforced(self):
        result = self.query("""
            SELECT JSON_OBJECT('constraints',JSON_ARRAYAGG(CONSTRAINT_NAME)) FROM information_schema.TABLE_CONSTRAINTS
            WHERE TABLE_SCHEMA='medical_local' AND TABLE_NAME IN ('task_input_revision','plan_content_revision')
              AND CONSTRAINT_TYPE='CHECK' AND ENFORCED='YES'
            """)
        self.assertIsInstance(result["constraints"], list)
        self.assertLessEqual({"ck_task_input_json", "ck_task_input_watermarks", "ck_task_input_schema",
            "ck_plan_content_json", "ck_plan_content_parent", "ck_plan_content_watermarks", "ck_plan_content_schema"},
            set(result["constraints"]))


if __name__ == "__main__":
    unittest.main(verbosity=2)

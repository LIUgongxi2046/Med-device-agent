import json
from pathlib import Path
import sys
import unittest


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from local_runtime import mysql, settings


class TemplateStorageRuntimeChecks(unittest.TestCase):
    def query(self, sql):
        return json.loads(mysql(sql, settings()["root_password"]).stdout)

    def test_human_approval_is_bound_to_original_template_operation_actor_and_authority(self):
        actual = self.query("""
            SELECT JSON_OBJECT('rows',JSON_ARRAYAGG(JSON_OBJECT('constraint',CONSTRAINT_NAME,'column',COLUMN_NAME,
                'target',REFERENCED_TABLE_NAME,'target_column',REFERENCED_COLUMN_NAME,'ordinal',ORDINAL_POSITION)))
            FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA='medical_local'
              AND TABLE_NAME='template_approval_decision' AND REFERENCED_TABLE_NAME IS NOT NULL
            """)
        expected = {
            'fk_template_approval_template': [('tenant_id','template_version','tenant_id'),
                ('template_version','template_version','template_version'),('template_sha256','template_version','content_sha256')],
            'fk_template_approval_operation': [('tenant_id','operation_record','tenant_id'),('operation_id','operation_record','operation_id')],
            'fk_template_approval_actor': [('tenant_id','tenant_member','tenant_id'),('created_by','tenant_member','user_id')],
            'fk_template_approval_authority': [('tenant_id','permission_grant','tenant_id'),('authority_grant','permission_grant','grant_id')]
        }
        self.assertIsInstance(actual['rows'], list)
        for name, columns in expected.items():
            rows = sorted((row for row in actual['rows'] if row['constraint'] == name), key=lambda row: row['ordinal'])
            self.assertEqual([(row['column'],row['target'],row['target_column']) for row in rows], columns)
        self.assertEqual({row['constraint'] for row in actual['rows']}, set(expected))
        actual = self.query("""
            SELECT JSON_OBJECT('constraints',JSON_ARRAYAGG(CONSTRAINT_NAME)) FROM information_schema.TABLE_CONSTRAINTS
            WHERE TABLE_SCHEMA='medical_local' AND TABLE_NAME='template_approval_decision'
            """)
        self.assertTrue({'uq_template_approval_operation','uq_template_approval_revision','ck_template_approval_revision',
                         'ck_template_approval_reason','ck_template_approval_state'}.issubset(actual['constraints']))

    def test_complete_definition_and_original_attachment_requirements_can_be_saved(self):
        actual = self.query("""
            SELECT JSON_OBJECT('tables',COUNT(*)) FROM information_schema.TABLES
            WHERE TABLE_SCHEMA='medical_local' AND TABLE_NAME IN
              ('template_definition','template_attachment_requirement','template_definition_dependency')
            """)
        self.assertEqual(actual["tables"], 3, "完整模板需要保存定义、真实附件要求和全部固定依赖")
        actual = self.query("""
            SELECT JSON_OBJECT('columns',JSON_ARRAYAGG(COLUMN_NAME)) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA='medical_local' AND TABLE_NAME='template_definition'
            """)
        self.assertTrue({"content_json", "content_sha256", "base_version", "base_version_sha256", "reason",
                         "authority_grant", "authority_revision", "revision", "created_by", "created_at"}.issubset(actual["columns"]))

    def test_dependencies_bind_the_original_version_digest_with_enterprise_identity(self):
        actual = self.query("""
            SELECT JSON_OBJECT('rows',JSON_ARRAYAGG(JSON_OBJECT('column',COLUMN_NAME,
              'target',REFERENCED_TABLE_NAME,'target_column',REFERENCED_COLUMN_NAME,'ordinal',ORDINAL_POSITION)))
            FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA='medical_local'
              AND TABLE_NAME='template_definition_dependency' AND CONSTRAINT_NAME='fk_template_dependency_version'
            """)
        self.assertIsInstance(actual["rows"], list, "依赖必须同时绑定企业、原版本和完整摘要")
        rows = sorted(actual["rows"], key=lambda row: row["ordinal"])
        self.assertEqual([(row["column"], row["target"], row["target_column"]) for row in rows],
                         [("tenant_id", "version_registry", "tenant_id"), ("upstream_version", "version_registry", "version_id"),
                          ("upstream_sha256", "version_registry", "content_sha256")])

    def test_attachment_requirements_cannot_cross_template_nodes(self):
        actual = self.query("""
            SELECT JSON_OBJECT('rows',JSON_ARRAYAGG(JSON_OBJECT('column',COLUMN_NAME,
              'target',REFERENCED_TABLE_NAME,'target_column',REFERENCED_COLUMN_NAME,'ordinal',ORDINAL_POSITION)))
            FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA='medical_local'
              AND TABLE_NAME='template_attachment_requirement' AND CONSTRAINT_NAME='fk_template_attachment_node'
            """)
        self.assertIsInstance(actual["rows"], list, "附件要求必须属于同一企业和模板的目录节点")
        rows = sorted(actual["rows"], key=lambda row: row["ordinal"])
        self.assertEqual([(row["column"], row["target"], row["target_column"]) for row in rows],
                         [("tenant_id", "requirement_node", "tenant_id"), ("template_version", "requirement_node", "template_version"),
                          ("node_id", "requirement_node", "node_id")])

    def test_reference_catalogue_has_no_complete_definition_and_cannot_be_saved_as_one(self):
        actual = self.query("""
            SELECT JSON_OBJECT('checks',COUNT(*)) FROM information_schema.TABLE_CONSTRAINTS
            WHERE TABLE_SCHEMA='medical_local' AND TABLE_NAME='template_definition'
              AND CONSTRAINT_NAME='ck_template_definition_content' AND CONSTRAINT_TYPE='CHECK' AND ENFORCED='YES'
            """)
        self.assertEqual(actual["checks"], 1, "参考目录不能代替完整定义")
        reference = json.loads((ROOT / "business/medical-persistence/src/main/resources/registration-directory-2021-121.json").read_bytes())
        self.assertEqual(reference["reference_state"], "REFERENCE_ONLY")
        self.assertNotIn("field_definitions", reference)
        self.assertNotIn("format_profile", reference)


if __name__ == "__main__":
    unittest.main(verbosity=2)

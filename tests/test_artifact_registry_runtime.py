import json
import os
from pathlib import Path
import sys
import unittest
from xml.etree import ElementTree

import sqlglot


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from local_runtime import mysql, settings


TABLES = ("dependency_access_manifest", "artifact_content_binding", "content_read_range")


def query(statement):
    result = mysql("USE medical_local; SET SESSION TRANSACTION READ ONLY; START TRANSACTION; " + statement + "; COMMIT;",
                   settings()["root_password"])
    return json.loads(result.stdout)


class ArtifactRegistryRuntimeChecks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        identity = json.loads((ROOT / ".implementation-work/runtime/service-identity.json").read_bytes())
        actual = query("SELECT JSON_OBJECT('database',DATABASE(),'server_uuid',@@server_uuid,'version',VERSION())")
        if actual != {"database": identity["database"], "server_uuid": identity["server_uuid"], "version": "8.4.3"}:
            raise ValueError("ARTIFACT_REGISTRY_DATABASE_IDENTITY_CHANGED")
        cls.identity = actual
        cls.customer_counts = None
        cls.columns = query("""
            SELECT JSON_OBJECT('rows',JSON_ARRAYAGG(JSON_OBJECT('table',TABLE_NAME,'column',COLUMN_NAME,'type',COLUMN_TYPE,
                'nullable',IS_NULLABLE,'generation',GENERATION_EXPRESSION))) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA='medical_local' AND TABLE_NAME IN
                ('dependency_access_manifest','artifact_content_binding','content_read_range')
            """)["rows"]
        cls.foreign_keys = query("""
            SELECT JSON_OBJECT('rows',JSON_ARRAYAGG(JSON_OBJECT('table',TABLE_NAME,'name',CONSTRAINT_NAME,'column',COLUMN_NAME,
                'target',REFERENCED_TABLE_NAME,'target_column',REFERENCED_COLUMN_NAME,'ordinal',ORDINAL_POSITION)))
            FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA='medical_local' AND REFERENCED_TABLE_NAME IS NOT NULL
                AND TABLE_NAME IN ('dependency_access_manifest','artifact_content_binding','content_read_range')
            """)["rows"]
        cls.constraints = query("""
            SELECT JSON_OBJECT('rows',JSON_ARRAYAGG(JSON_OBJECT('table',t.TABLE_NAME,'name',t.CONSTRAINT_NAME,'enforced',t.ENFORCED,'clause',c.CHECK_CLAUSE)))
            FROM information_schema.TABLE_CONSTRAINTS t JOIN information_schema.CHECK_CONSTRAINTS c
                ON c.CONSTRAINT_SCHEMA=t.CONSTRAINT_SCHEMA AND c.CONSTRAINT_NAME=t.CONSTRAINT_NAME
            WHERE t.TABLE_SCHEMA='medical_local' AND t.TABLE_NAME IN
                ('dependency_access_manifest','artifact_content_binding','content_read_range') AND t.CONSTRAINT_TYPE='CHECK'
            """)["rows"]
        cls.changesets = query("""
            SELECT JSON_OBJECT('rows',JSON_ARRAYAGG(JSON_OBJECT('id',ID,'author',AUTHOR,'filename',FILENAME,
                'checksum',MD5SUM,'execution',EXECTYPE))) FROM medical_local.DATABASECHANGELOG
            WHERE ID IN ('1.0.0-187-dependency-manifest-key','1.0.0-188-artifact-dependency-key',
                '1.0.0-189-content-receipt-key','1.0.0-190-dependency-access-manifest',
                '1.0.0-191-artifact-content-binding','1.0.0-192-content-read-range')
            """)["rows"]

    @classmethod
    def tearDownClass(cls):
        output = Path(os.environ["MEDICAL_ARTIFACT_REGISTRY_EVIDENCE"])
        output.parent.resolve(strict=True).relative_to((ROOT / ".implementation-work").resolve(strict=True))
        with output.open("x", encoding="utf-8") as target:
            json.dump({"scope": "ACTUAL_MYSQL_ARTIFACT_REGISTRY_METADATA", "identity": cls.identity,
                "columns": cls.columns, "foreign_keys": cls.foreign_keys, "constraints": cls.constraints,
                "changesets": cls.changesets, "customer_counts": cls.customer_counts}, target, ensure_ascii=False, indent=2)

    def test_applied_registry_changes_have_explicit_checksums(self):
        self.assertIsInstance(self.changesets, list, "实际数据库必须登记全部追加迁移及其校验值")
        expected = {"1.0.0-187-dependency-manifest-key", "1.0.0-188-artifact-dependency-key",
            "1.0.0-189-content-receipt-key", "1.0.0-190-dependency-access-manifest",
            "1.0.0-191-artifact-content-binding", "1.0.0-192-content-read-range"}
        self.assertEqual({item["id"] for item in self.changesets}, expected)
        self.assertEqual(len(self.changesets), 6)
        logical_path = ElementTree.parse(ROOT / "business/medical-data/src/main/resources/db/changelog/master.xml").getroot().attrib["logicalFilePath"]
        for item in self.changesets:
            self.assertEqual(item["author"], "medical-agent")
            self.assertEqual(item["execution"], "EXECUTED")
            self.assertTrue(item["checksum"])
            self.assertEqual(item["filename"], logical_path)

    def test_fixed_resource_schema_manifest_and_byte_range_fields_exist(self):
        self.assertIsInstance(self.columns, list, "Artifact必须保存原资源、Schema、完整清单与实际字节范围")
        rows = {name: {item["column"]: item for item in self.columns if item["table"] == name} for name in TABLES}
        common = {"tenant_id", "project_id", "project_key", "created_by", "created_at", "classification"}
        expected = {
            "dependency_access_manifest": common | {"access_set_id", "manifest_json", "manifest_sha256", "content_sha256",
                "member_count", "schema_version", "schema_manifest_sha256"},
            "artifact_content_binding": common | {"artifact_id", "resource_kind", "resource_id", "schema_ref",
                "schema_document_sha256", "dependency_access_set_id", "dependency_manifest_sha256"},
            "content_read_range": common | {"read_receipt_id", "artifact_id", "resource_kind", "resource_id", "schema_ref",
                "schema_document_sha256", "dependency_access_set_id", "dependency_manifest_sha256", "object_ref_sha256",
                "start_byte", "end_byte", "size_bytes", "chunk_sha256", "purpose", "authorization_sha256", "context_revision"},
        }
        for name in TABLES:
            self.assertEqual(set(rows[name]), expected[name])
            self.assertEqual(rows[name]["project_id"]["nullable"], "YES")
            self.assertTrue(rows[name]["project_key"]["generation"])
            for field in expected[name] - {"project_id", "project_key"}:
                self.assertEqual(rows[name][field]["nullable"], "NO")
        for field in ("start_byte", "end_byte", "size_bytes", "context_revision"):
            self.assertEqual(rows["content_read_range"][field]["type"], "bigint unsigned")
        self.assertEqual(rows["dependency_access_manifest"]["manifest_json"]["type"], "json")
        self.assertEqual(rows["dependency_access_manifest"]["member_count"]["type"], "bigint unsigned")

    def test_foreign_keys_bind_the_complete_original_identity(self):
        self.assertIsInstance(self.foreign_keys, list, "登记与回执必须通过完整企业及项目外键关联")
        groups = {}
        for item in self.foreign_keys:
            groups.setdefault((item["table"], item["name"]), []).append(item)
        expected = {
            ("dependency_access_manifest", "fk_dependency_manifest_header"): ("dependency_access_set",
                ("tenant_id", "project_key", "access_set_id", "manifest_sha256")),
            ("artifact_content_binding", "fk_artifact_content_dependency"): ("artifact",
                ("tenant_id", "project_key", "artifact_id", "dependency_access_set_id")),
            ("artifact_content_binding", "fk_artifact_content_manifest"): ("dependency_access_manifest",
                ("tenant_id", "project_key", "dependency_access_set_id", "dependency_manifest_sha256")),
            ("content_read_range", "fk_content_range_receipt"): ("content_read_receipt",
                ("tenant_id", "project_key", "read_receipt_id", "artifact_id", "resource_id")),
            ("content_read_range", "fk_content_range_binding"): ("artifact_content_binding",
                ("tenant_id", "project_key", "artifact_id", "resource_kind", "resource_id", "schema_ref",
                 "schema_document_sha256", "dependency_access_set_id", "dependency_manifest_sha256")),
        }
        self.assertEqual(set(groups), set(expected))
        for key, (target, fields) in expected.items():
            ordered = sorted(groups[key], key=lambda item: item["ordinal"])
            self.assertEqual(tuple(item["column"] for item in ordered), fields)
            self.assertTrue(all(item["target"] == target for item in ordered))
            remote = tuple(item["target_column"] for item in ordered)
            if key == ("artifact_content_binding", "fk_artifact_content_manifest"):
                self.assertEqual(remote, ("tenant_id", "project_key", "access_set_id", "manifest_sha256"))
            else:
                self.assertEqual(remote, fields)

    def test_actual_range_constraint_rejects_empty_reversed_and_excess_ranges(self):
        self.assertIsInstance(self.constraints, list, "字节范围必须具有实际执行的数据库约束")
        checks = {(item["table"], item["name"]): item for item in self.constraints}
        self.assertTrue(all(item["enforced"] == "YES" for item in self.constraints))
        rule = checks[("content_read_range", "ck_content_range_bytes")]["clause"]
        expression = sqlglot.parse_one(rule, read="mysql")
        self.assertEqual({item.name for item in expression.find_all(sqlglot.exp.Column)}, {"size_bytes", "start_byte", "end_byte"})
        size = (ROOT / "ui-delivery/assets/public-evidence/clinical-evaluation-2021.docx").stat().st_size
        cases = ((0, size, size, 1), (0, 0, size, 0), (size, 0, size, 0), (0, size + 1, size, 0), (0, 1, 0, 0))
        for start, end, length, allowed in cases:
            actual = query("SELECT JSON_OBJECT('allowed'," + expression.sql(dialect="mysql") + ") FROM (SELECT "
                           + str(start) + " AS start_byte," + str(end) + " AS end_byte," + str(length) + " AS size_bytes) AS original_range")
            self.assertEqual(actual["allowed"], allowed)
        self.assertIn(("dependency_access_manifest", "ck_dependency_manifest_members"), checks)
        self.assertIn(("dependency_access_manifest", "ck_dependency_manifest_identity"), checks)
        self.assertIn(("artifact_content_binding", "ck_artifact_content_kind"), checks)

    def test_local_migration_does_not_create_customer_records(self):
        self.assertIsInstance(self.columns, list, "范围登记结构必须存在")
        self.assertEqual({item["table"] for item in self.columns}, set(TABLES))
        names = ("tenant", "project", "artifact", "dependency_access_set", "dependency_access_member", "content_read_receipt", *TABLES)
        counts = {}
        for name in names:
            counts[name] = query("SELECT JSON_OBJECT('count',COUNT(*)) FROM medical_local." + name)["count"]
            self.assertEqual(counts[name], 0)
        type(self).customer_counts = counts
        print("ARTIFACT_REGISTRY_CHECK scope=ACTUAL_MYSQL_EMPTY_CUSTOMER_DATA_SCHEMA customer_created=false")


if __name__ == "__main__":
    unittest.main(verbosity=2)

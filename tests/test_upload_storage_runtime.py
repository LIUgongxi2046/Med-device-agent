import json
import os
from pathlib import Path
import sys
import subprocess
import unittest
from xml.etree import ElementTree

import sqlglot


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from local_runtime import MYSQL, environment, mysql, settings


TABLES = ("object_upload_session", "object_upload_part", "artifact_upload_binding")


def query(statement):
    result = mysql("USE medical_local; SET SESSION TRANSACTION READ ONLY; START TRANSACTION; " + statement + "; COMMIT;", settings()["root_password"])
    return json.loads(result.stdout)


def actual_definition(table):
    values = environment(); values["MYSQL_PWD"] = settings()["root_password"]
    result = subprocess.run([str(MYSQL / "bin/mysql"), "--no-defaults", "--user=root", "--protocol=TCP",
        "--host=127.0.0.1", "--port=13306", "--xml", "--execute", "SHOW CREATE TABLE medical_local." + table],
        env=values, capture_output=True, text=True, check=True)
    row = ElementTree.fromstring(result.stdout).find("row")
    fields = {field.attrib["name"]: field.text for field in row.findall("field")}
    if fields["Table"] != table:
        raise ValueError("UPLOAD_STORAGE_TABLE_IDENTITY_CHANGED")
    return fields["Create Table"]


class UploadStorageRuntimeChecks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        identity = json.loads((ROOT / ".implementation-work/runtime/service-identity.json").read_bytes())
        cls.identity = query("SELECT JSON_OBJECT('database',DATABASE(),'server_uuid',@@server_uuid,'version',VERSION())")
        if cls.identity != {"database": identity["database"], "server_uuid": identity["server_uuid"], "version": "8.4.3"}:
            raise ValueError("UPLOAD_STORAGE_DATABASE_IDENTITY_CHANGED")
        cls.columns = query("SELECT JSON_OBJECT('rows',JSON_ARRAYAGG(JSON_OBJECT('table',TABLE_NAME,'column',COLUMN_NAME,'type',COLUMN_TYPE,'nullable',IS_NULLABLE,'generation',GENERATION_EXPRESSION))) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='medical_local' AND TABLE_NAME IN ('object_upload_session','object_upload_part','artifact_upload_binding')")["rows"]
        cls.foreign_keys = query("SELECT JSON_OBJECT('rows',JSON_ARRAYAGG(JSON_OBJECT('table',TABLE_NAME,'name',CONSTRAINT_NAME,'column',COLUMN_NAME,'target',REFERENCED_TABLE_NAME,'target_column',REFERENCED_COLUMN_NAME,'ordinal',ORDINAL_POSITION))) FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA='medical_local' AND REFERENCED_TABLE_NAME IS NOT NULL AND TABLE_NAME IN ('object_upload_session','object_upload_part','artifact_upload_binding')")["rows"]
        cls.constraints = query("SELECT JSON_OBJECT('rows',JSON_ARRAYAGG(JSON_OBJECT('table',t.TABLE_NAME,'name',t.CONSTRAINT_NAME,'enforced',t.ENFORCED,'clause',c.CHECK_CLAUSE))) FROM information_schema.TABLE_CONSTRAINTS t JOIN information_schema.CHECK_CONSTRAINTS c ON c.CONSTRAINT_SCHEMA=t.CONSTRAINT_SCHEMA AND c.CONSTRAINT_NAME=t.CONSTRAINT_NAME WHERE t.TABLE_SCHEMA='medical_local' AND t.TABLE_NAME IN ('object_upload_session','object_upload_part','artifact_upload_binding') AND t.CONSTRAINT_TYPE='CHECK'")["rows"]
        cls.changesets = query("SELECT JSON_OBJECT('rows',JSON_ARRAYAGG(JSON_OBJECT('id',ID,'author',AUTHOR,'filename',FILENAME,'checksum',MD5SUM,'execution',EXECTYPE))) FROM medical_local.DATABASECHANGELOG WHERE ID IN ('1.0.0-193-upload-grant-scope','1.0.0-194-upload-grant-document','1.0.0-195-upload-grant-step','1.0.0-196-object-upload-session','1.0.0-197-object-upload-part','1.0.0-198-artifact-upload-binding')")["rows"]
        cls.counts = None
        cls.definitions = {table: actual_definition(table) for table in TABLES}

    def actual_constraint(self, name):
        row = next(item for item in self.constraints if item["name"] == name)
        self.assertEqual(row["enforced"], "YES")
        table = sqlglot.parse_one(self.definitions[row["table"]], read="mysql")
        constraint = next(item for item in table.find_all(sqlglot.exp.Constraint) if item.name == name)
        return next(constraint.find_all(sqlglot.exp.CheckColumnConstraint)).this

    def evaluate_constraint(self, name, values):
        expression = self.actual_constraint(name)
        self.assertEqual({column.name for column in expression.find_all(sqlglot.exp.Column)}, set(values))
        literals = {key: sqlglot.exp.convert(value) for key, value in values.items()}
        expression = expression.transform(lambda node: literals[node.name].copy() if isinstance(node, sqlglot.exp.Column) else node)
        return query("SELECT JSON_OBJECT('allowed'," + expression.sql(dialect="mysql") + ")")["allowed"]

    @classmethod
    def tearDownClass(cls):
        path = Path(os.environ["MEDICAL_UPLOAD_STORAGE_EVIDENCE"])
        path.parent.resolve(strict=True).relative_to((ROOT / ".implementation-work").resolve(strict=True))
        with path.open("x", encoding="utf-8") as output:
            json.dump({"scope": "ACTUAL_MYSQL_UPLOAD_STORAGE_METADATA", "identity": cls.identity, "columns": cls.columns,
                "foreign_keys": cls.foreign_keys, "constraints": cls.constraints, "definitions": cls.definitions,
                "changesets": cls.changesets, "customer_counts": cls.counts,
                "formal_evidence_eligible": False, "customer_records_created": False}, output, ensure_ascii=False, indent=2)

    def test_all_fixed_upload_fields_exist_with_exact_types(self):
        self.assertIsInstance(self.columns, list, "完整上传必须保存独立身份、原许可和每片固定对象范围")
        common = {"tenant_id", "project_id", "project_key", "created_by", "created_at", "classification", "schema_version", "schema_manifest_sha256"}
        expected = {
            "object_upload_session": common | {"upload_id", "subject_id", "purpose", "source_upload_id", "grant_id", "expected_size", "expected_sha256", "received_size", "media_type", "object_key", "object_key_sha256", "max_part_bytes", "expires_at", "state", "object_version", "content_sha256", "object_ref", "revision", "updated_at"},
            "object_upload_part": common | {"upload_id", "part_no", "write_id", "total_size", "max_part_bytes", "start_byte", "end_byte", "size_bytes", "sha256", "object_key", "object_key_sha256", "state", "object_version", "object_ref", "revision", "updated_at"},
            "artifact_upload_binding": common | {"grant_id", "upload_id", "subject_id", "purpose", "document_id", "run_id", "step_id", "expected_revision", "context_revision", "principal_id", "lease_generation", "dependency_access_set_id", "dependency_manifest_sha256", "schema_document_sha256", "authorization_manifest", "authorization_sha256"},
        }
        for table in TABLES:
            rows = {item["column"]: item for item in self.columns if item["table"] == table}
            self.assertEqual(set(rows), expected[table]); self.assertTrue(rows["project_key"]["generation"])
            self.assertEqual(rows["project_id"]["nullable"], "YES")
        types = {(item["table"], item["column"]): item["type"] for item in self.columns}
        for table, fields in {"object_upload_session": ("expected_size", "received_size", "max_part_bytes", "revision"),
                "object_upload_part": ("start_byte", "end_byte", "size_bytes", "total_size", "max_part_bytes", "revision"),
                "artifact_upload_binding": ("expected_revision", "context_revision", "lease_generation")}.items():
            for field in fields: self.assertEqual(types[(table, field)], "bigint unsigned")
        self.assertEqual(types[("object_upload_part", "part_no")], "int unsigned")

    def test_foreign_keys_bind_scope_subject_original_and_dependency(self):
        self.assertIsInstance(self.foreign_keys, list, "上传必须通过完整企业/项目及主体关联原许可和来源")
        groups = {}
        for row in self.foreign_keys: groups.setdefault((row["table"], row["name"]), []).append(row)
        expected = {
            ("object_upload_session", "fk_object_upload_subject"): ("tenant_member", ("tenant_id", "subject_id"), ("tenant_id", "user_id")),
            ("object_upload_session", "fk_object_upload_project"): ("project", ("tenant_id", "project_id"), ("tenant_id", "project_id")),
            ("object_upload_session", "fk_object_upload_source"): ("upload_item", ("tenant_id", "project_id", "source_upload_id"), ("tenant_id", "project_id", "upload_id")),
            ("object_upload_session", "fk_object_upload_grant"): ("artifact_upload_grant", ("tenant_id", "project_key", "grant_id", "subject_id", "purpose"), ("tenant_id", "project_key", "grant_id", "subject_id", "purpose")),
            ("object_upload_part", "fk_object_part_upload"): ("object_upload_session", ("tenant_id", "project_key", "upload_id", "total_size", "max_part_bytes"), ("tenant_id", "project_key", "upload_id", "expected_size", "max_part_bytes")),
            ("artifact_upload_binding", "fk_upload_binding_grant"): ("artifact_upload_grant", ("tenant_id", "project_key", "grant_id", "subject_id", "purpose"), ("tenant_id", "project_key", "grant_id", "subject_id", "purpose")),
            ("artifact_upload_binding", "fk_upload_binding_session"): ("object_upload_session", ("tenant_id", "project_key", "upload_id", "grant_id", "subject_id", "purpose"), ("tenant_id", "project_key", "upload_id", "grant_id", "subject_id", "purpose")),
            ("artifact_upload_binding", "fk_upload_binding_document"): ("artifact_upload_grant", ("tenant_id", "project_key", "grant_id", "subject_id", "document_id"), ("tenant_id", "project_key", "grant_id", "subject_id", "document_id")),
            ("artifact_upload_binding", "fk_upload_binding_step"): ("artifact_upload_grant", ("tenant_id", "project_key", "grant_id", "subject_id", "run_id", "step_id"), ("tenant_id", "project_key", "grant_id", "subject_id", "run_id", "step_id")),
            ("artifact_upload_binding", "fk_upload_binding_principal"): ("run_principal", ("tenant_id", "project_key", "principal_id", "run_id"), ("tenant_id", "project_key", "principal_id", "run_id")),
            ("artifact_upload_binding", "fk_upload_binding_dependency"): ("dependency_access_manifest", ("tenant_id", "project_key", "dependency_access_set_id", "dependency_manifest_sha256"), ("tenant_id", "project_key", "access_set_id", "manifest_sha256")),
        }
        self.assertEqual(set(groups), set(expected))
        for key, (target, local, remote) in expected.items():
            rows = sorted(groups[key], key=lambda row: row["ordinal"])
            self.assertEqual(tuple(row["column"] for row in rows), local)
            self.assertEqual(tuple(row["target_column"] for row in rows), remote)
            self.assertTrue(all(row["target"] == target for row in rows))

    def test_actual_part_constraints_reject_gaps_overflow_and_wrong_size(self):
        self.assertIsInstance(self.constraints, list, "分片必须有当前数据库执行的范围约束")
        rows = {row["name"]: row for row in self.constraints}
        self.assertTrue(all(row["enforced"] == "YES" for row in self.constraints))
        expression = self.actual_constraint("ck_object_part_range")
        length = (ROOT / "ui-delivery/assets/public-evidence/clinical-evaluation-2021.docx").stat().st_size
        for start, end, size, allowed in ((0, length, length, 1), (1, length, length - 1, 0), (0, 0, 0, 0), (0, length + 1, length + 1, 0), (0, length, length - 1, 0)):
            actual = query("SELECT JSON_OBJECT('allowed'," + expression.sql(dialect="mysql") + ") FROM (SELECT 1 AS part_no,8388608 AS max_part_bytes," + str(length) + " AS total_size," + str(start) + " AS start_byte," + str(end) + " AS end_byte," + str(size) + " AS size_bytes) AS actual_range")
            self.assertEqual(actual["allowed"], allowed)
        self.assertIn("ck_object_upload_origin", rows); self.assertIn("ck_upload_binding_execution", rows)
        self.assertIn("ck_object_upload_object", rows); self.assertIn("ck_object_part_object", rows)

    def test_mutually_exclusive_source_and_artifact_origin_is_enforced(self):
        self.assertIsInstance(self.constraints, list, "来源上传和制品许可必须保留独立原始身份")
        expression = self.actual_constraint("ck_object_upload_origin")
        actual = query("SELECT JSON_OBJECT('allowed'," + expression.sql(dialect="mysql") + ") FROM (SELECT 'SOURCE' AS purpose,NULL AS source_upload_id,NULL AS grant_id,NULL AS project_id) AS unbound_upload")
        self.assertEqual(actual["allowed"], 0)
        actual = query("SELECT JSON_OBJECT('allowed'," + expression.sql(dialect="mysql") + ") FROM (SELECT 'DOCUMENT_BODY' AS purpose,NULL AS source_upload_id,NULL AS grant_id,NULL AS project_id) AS unbound_upload")
        self.assertEqual(actual["allowed"], 0)
        actual = query("SELECT JSON_OBJECT('allowed'," + expression.sql(dialect="mysql") + ") FROM (SELECT 'AGENT_ARTIFACT' AS purpose,NULL AS source_upload_id,NULL AS grant_id,NULL AS project_id) AS unbound_upload")
        self.assertEqual(actual["allowed"], 0)

    def test_document_and_agent_bindings_reject_absent_execution_identity(self):
        fields = {"project_id": None, "document_id": None, "expected_revision": None, "context_revision": None,
            "run_id": None, "step_id": None, "principal_id": None, "lease_generation": None}
        for purpose in ("DOCUMENT_BODY", "AGENT_ARTIFACT", "SOURCE"):
            self.assertEqual(self.evaluate_constraint("ck_upload_binding_execution", {**fields, "purpose": purpose}), 0)
        for purpose in ("DOCUMENT_BODY", "AGENT_ARTIFACT"):
            self.assertEqual(self.evaluate_constraint("ck_object_upload_digest", {"purpose": purpose, "expected_sha256": None}), 0)

    def test_all_applied_constraints_have_complete_native_definitions(self):
        for table, definition in self.definitions.items():
            parsed = sqlglot.parse_one(definition, read="mysql")
            actual = {item.name for item in parsed.find_all(sqlglot.exp.Constraint)
                if next(item.find_all(sqlglot.exp.CheckColumnConstraint), None) is not None}
            registered = {item["name"] for item in self.constraints if item["table"] == table}
            self.assertEqual(actual, registered)
            self.assertTrue(all(item["enforced"] == "YES" for item in self.constraints if item["table"] == table))

    def test_full_object_constraints_reject_missing_numeric_and_changed_metadata(self):
        originals = json.loads((ROOT / ".implementation-work/IC-05-OBJECT-STORAGE-20261005-OBJECT-STORAGE-FINAL-01/objects.json").read_bytes())["objects"]
        for original in originals:
            reference = {"key": original["object_key"], "version_id": original["object_version"], "sha256": original["sha256"],
                "size_bytes": int(original["size_bytes"]), "media_type": original["media_type"]}
            values = {"object_key": original["object_key"], "object_version": original["object_version"],
                "content_sha256": original["sha256"], "expected_sha256": original["sha256"], "expected_size": int(original["size_bytes"]),
                "media_type": original["media_type"], "object_ref": json.dumps(reference)}
            self.assertEqual(self.evaluate_constraint("ck_object_upload_object", values), 1)
            for field in reference:
                missing = dict(reference); del missing[field]
                self.assertEqual(self.evaluate_constraint("ck_object_upload_object", {**values, "object_ref": json.dumps(missing)}), 0)
            for field, changed in (("size_bytes", original["size_bytes"]), ("size_bytes", int(original["size_bytes"]) + 1),
                    ("version_id", None), ("sha256", originals[1 - originals.index(original)]["sha256"])):
                self.assertEqual(self.evaluate_constraint("ck_object_upload_object", {**values, "object_ref": json.dumps({**reference, field: changed})}), 0)
            self.assertEqual(self.evaluate_constraint("ck_object_upload_object", {**values, "object_ref": json.dumps({**reference, "source_ref": original["source_ref"]})}), 0)
            absent = {**values, "object_version": None, "content_sha256": None, "object_ref": None}
            self.assertEqual(self.evaluate_constraint("ck_object_upload_object", absent), 1)
            self.assertEqual(self.evaluate_constraint("ck_object_upload_complete", {"state": "COMPLETED", "object_version": None,
                "object_ref": None, "received_size": int(original["size_bytes"]), "expected_size": int(original["size_bytes"])}), 0)

    def test_part_object_and_stored_state_require_complete_original_reference(self):
        original = json.loads((ROOT / ".implementation-work/IC-05-OBJECT-STORAGE-20261005-OBJECT-STORAGE-FINAL-01/objects.json").read_bytes())["objects"][0]
        reference = {"key": original["object_key"], "version_id": original["object_version"], "sha256": original["sha256"],
            "size_bytes": int(original["size_bytes"]), "media_type": "application/octet-stream"}
        values = {"object_key": original["object_key"], "object_version": original["object_version"], "sha256": original["sha256"],
            "size_bytes": int(original["size_bytes"]), "object_ref": json.dumps(reference)}
        self.assertEqual(self.evaluate_constraint("ck_object_part_object", values), 1)
        for field in reference:
            missing = dict(reference); del missing[field]
            self.assertEqual(self.evaluate_constraint("ck_object_part_object", {**values, "object_ref": json.dumps(missing)}), 0)
        self.assertEqual(self.evaluate_constraint("ck_object_part_object", {**values,
            "object_ref": json.dumps({**reference, "media_type": original["media_type"]})}), 0)
        self.assertEqual(self.evaluate_constraint("ck_object_part_stored", {"state": "STORED", "object_version": None, "object_ref": None}), 0)
        self.assertEqual(self.evaluate_constraint("ck_object_part_stored", {"state": "WRITING", "object_version": None, "object_ref": None}), 1)

    def test_new_migrations_have_actual_applied_checksums(self):
        self.assertIsInstance(self.changesets, list, "全部上传结构迁移必须在当前目标实际执行")
        self.assertEqual(len(self.changesets), 6)
        expected = {"1.0.0-193-upload-grant-scope", "1.0.0-194-upload-grant-document", "1.0.0-195-upload-grant-step", "1.0.0-196-object-upload-session", "1.0.0-197-object-upload-part", "1.0.0-198-artifact-upload-binding"}
        self.assertEqual({row["id"] for row in self.changesets}, expected)
        logical = ElementTree.parse(ROOT / "business/medical-data/src/main/resources/db/changelog/master.xml").getroot().attrib["logicalFilePath"]
        for row in self.changesets:
            self.assertEqual(row["author"], "medical-agent"); self.assertEqual(row["filename"], logical)
            self.assertEqual(row["execution"], "EXECUTED"); self.assertTrue(row["checksum"])

    def test_migration_does_not_create_customer_uploads(self):
        self.assertIsInstance(self.columns, list, "上传持久化结构必须存在")
        self.assertEqual({row["table"] for row in self.columns}, set(TABLES))
        counts = {name: query("SELECT JSON_OBJECT('count',COUNT(*)) FROM medical_local." + name)["count"] for name in ("tenant", "project", "artifact_upload_grant", "upload_item", "upload_part", "artifact", *TABLES)}
        self.assertTrue(all(value == 0 for value in counts.values())); type(self).counts = counts


if __name__ == "__main__":
    unittest.main(verbosity=2)

"""Offline AST contracts only: no database connections or DDL execution."""
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import unittest
from xml.etree import ElementTree

import sqlglot
from sqlglot import exp

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from run_migration import resolve_java

RESOURCES = ROOT / "business/medical-data/src/main/resources"
MIGRATION = "db/migration/058-annotation-decisions.sql"


class AnnotationDecisionMigrationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.sql = (RESOURCES / MIGRATION).read_text()
        repository = ROOT / ".implementation-work/maven-repository"
        jars = [repository / name for name in (
            "com/github/jsqlparser/jsqlparser/5.2/jsqlparser-5.2.jar",
            "com/fasterxml/jackson/core/jackson-databind/2.21.4/jackson-databind-2.21.4.jar",
            "com/fasterxml/jackson/core/jackson-core/2.21.4/jackson-core-2.21.4.jar",
            "com/fasterxml/jackson/core/jackson-annotations/2.21/jackson-annotations-2.21.jar")]
        result = subprocess.run([str(resolve_java(ROOT)), "--class-path", os.pathsep.join(map(str, jars)),
                                 str(ROOT / "tests/InspectAlterMigrations.java"), str(RESOURCES / MIGRATION)],
                                cwd=ROOT, capture_output=True, text=True, timeout=30, check=True)
        cls.parsed = json.loads(result.stdout)
        cls.table = sqlglot.parse_one(next(item["create"] for item in cls.parsed if "create" in item), read="mysql")

    def test_registration_exact_table_coverage_and_unique_changesets(self):
        master = ElementTree.parse(RESOURCES / "db/changelog/master.xml")
        entries = master.findall("{http://www.liquibase.org/xml/ns/dbchangelog}include")
        self.assertEqual(1, sum(entry.attrib["file"] == MIGRATION for entry in entries))
        files = [entry.attrib["file"] for entry in entries]
        position = files.index(MIGRATION)
        self.assertEqual([MIGRATION, "db/migration/059-annotation-anchor-lifecycle.sql",
                          "db/migration/060-simulation-invocation-ledger.sql"], files[position:position + 3])
        coverage = json.loads((RESOURCES / "db/migration/coverage.json").read_bytes())
        self.assertEqual(1, coverage["native_sql_migrations"].count(MIGRATION))
        self.assertEqual(["annotation_decision"], coverage["required_tables"]["annotation_decisions"])
        identities = []
        for file in (RESOURCES / "db/migration").glob("*.sql"):
            identities.extend(re.findall(r"^--changeset ([^ ]+)", file.read_text(), re.M))
        self.assertEqual(len(identities), len(set(identities)))
        self.assertIn("medical-agent:1.0.0-313-annotation-document-identity", identities)
        self.assertIn("medical-agent:1.0.0-314-annotation-decision", identities)

    def test_only_adds_exact_document_identity_key_and_history(self):
        self.assertEqual(2, len(self.parsed))
        alteration = next(item for item in self.parsed if "operation" in item)
        self.assertEqual("annotation", alteration["table"])
        self.assertEqual("ADD", alteration["operation"])
        self.assertIn("uq_annotation_document_identity", alteration["definition"])
        self.assertEqual("annotation_decision", self.table.this.this.name)
        self.assertNotIn("review_decision", self.sql)
        self.assertNotRegex(self.sql, r"(?im)^\s*(UPDATE|DELETE|INSERT|DROP)\b")

    def test_exact_append_history_columns_and_unique_result_revision(self):
        columns = {column.name for column in self.table.this.expressions if isinstance(column, exp.ColumnDef)}
        self.assertEqual({"tenant_id", "project_id", "decision_id", "annotation_id", "document_revision",
                          "decision", "reason", "previous_status", "status", "previous_revision", "revision",
                          "actor_id", "created_by", "created_at", "classification", "schema_version",
                          "schema_manifest_sha256"}, columns)
        primary = next(self.table.find_all(exp.PrimaryKey))
        self.assertEqual(["tenant_id", "project_id", "decision_id"], [column.name for column in primary.expressions])
        unique = {tuple(column.name for column in key.this.expressions) for key in self.table.find_all(exp.UniqueColumnConstraint)}
        self.assertIn(("tenant_id", "project_id", "annotation_id", "revision"), unique)

    def test_foreign_keys_preserve_real_document_and_annotation_identity(self):
        references = {(key.args["reference"].this.this.name, tuple(column.name for column in key.expressions),
                       tuple(column.name for column in key.args["reference"].this.expressions))
                      for key in self.table.find_all(exp.ForeignKey)}
        self.assertEqual({
            ("annotation", ("tenant_id", "project_id", "annotation_id", "document_revision"), ("tenant_id", "project_id", "annotation_id", "document_revision")),
            ("document_revision_meta", ("tenant_id", "project_id", "document_revision"), ("tenant_id", "project_id", "document_revision")),
            ("tenant_member", ("tenant_id", "actor_id"), ("tenant_id", "user_id")),
            ("tenant_member", ("tenant_id", "created_by"), ("tenant_id", "user_id"))}, references)

    def test_bounded_unicode_reason_and_exact_transition_revision_guards(self):
        self.assertIn("reason TEXT NOT NULL", self.sql)
        self.assertIn("DEFAULT CHARSET=utf8mb4", self.sql)
        self.assertIn("CHAR_LENGTH(reason) BETWEEN 1 AND 16000", self.sql)
        self.assertIn("CHAR_LENGTH(TRIM(reason)) > 0", self.sql)
        self.assertIn("decision='RESOLVE' AND previous_status='OPEN' AND status='RESOLVED'", self.sql)
        self.assertIn("decision='REOPEN' AND previous_status='RESOLVED' AND status='OPEN'", self.sql)
        self.assertIn("previous_revision > 0 AND revision=previous_revision+1", self.sql)
        self.assertIn("CHECK (actor_id=created_by)", self.sql)
        self.assertEqual(4, len(list(self.table.find_all(exp.CheckColumnConstraint))))


if __name__ == "__main__":
    unittest.main()

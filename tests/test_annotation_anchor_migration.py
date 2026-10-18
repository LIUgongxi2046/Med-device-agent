"""Offline parser contracts only; no database connections and no DDL execution."""
import hashlib
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
MIGRATION = "db/migration/059-annotation-anchor-lifecycle.sql"
TABLES = {"annotation_anchor", "annotation_effective_anchor", "annotation_transition"}


def columns(table):
    return {column.name for column in table.this.expressions if isinstance(column, exp.ColumnDef)}


def references(table):
    return {(key.args["reference"].this.this.name, tuple(column.name for column in key.expressions),
             tuple(column.name for column in key.args["reference"].this.expressions))
            for key in table.find_all(exp.ForeignKey)}


class AnnotationAnchorMigrationTest(unittest.TestCase):
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
        cls.tables = {}
        for entry in cls.parsed:
            if "create" in entry:
                table = sqlglot.parse_one(entry["create"], read="mysql")
                cls.tables[table.this.this.name] = table
        cls.fills = [sqlglot.parse_one(entry["data_fill"], read="mysql")
                     for entry in cls.parsed if "data_fill" in entry]

    def test_exact_registration_coverage_and_changeset_identities(self):
        entries = ElementTree.parse(RESOURCES / "db/changelog/master.xml").findall(
            "{http://www.liquibase.org/xml/ns/dbchangelog}include")
        self.assertEqual(1, sum(entry.attrib["file"] == MIGRATION for entry in entries))
        files = [entry.attrib["file"] for entry in entries]
        position = files.index(MIGRATION)
        self.assertEqual(["db/migration/058-annotation-decisions.sql", MIGRATION,
                          "db/migration/060-simulation-invocation-ledger.sql"], files[position - 1:position + 2])
        manifest = json.loads((RESOURCES / "db/migration/coverage.json").read_bytes())
        self.assertEqual(1, manifest["native_sql_migrations"].count(MIGRATION))
        self.assertEqual(TABLES, set(manifest["required_tables"]["annotation_anchors"]))
        self.assertEqual(TABLES, set(self.tables))
        self.assertEqual(281, sum(map(len, manifest["required_tables"].values())))
        self.assertEqual(318, sum(map(len, manifest["required_tables"].values()))
                         + len(manifest["platform_tables"]) + len(manifest["framework_tables"]))
        identities = re.findall(r"^--changeset ([^ ]+) dbms:mysql runInTransaction:false failOnError:true$", self.sql, re.M)
        self.assertEqual([
            "medical-agent:1.0.0-315-annotation-anchor",
            "medical-agent:1.0.0-316-annotation-effective-anchor",
            "medical-agent:1.0.0-317-annotation-transition",
            "medical-agent:1.0.0-318-annotation-origin-anchor-backfill",
            "medical-agent:1.0.0-319-annotation-effective-anchor-backfill"], identities)
        all_ids = [identity for file in (RESOURCES / "db/migration").glob("*.sql")
                   for identity in re.findall(r"^--changeset ([^ ]+)", file.read_text(), re.M)]
        self.assertEqual(len(all_ids), len(set(all_ids)))

    def test_exact_columns_preserve_independent_anchor_history_and_pointer(self):
        common = {"tenant_id", "project_id", "annotation_id", "created_by", "created_at", "classification"}
        self.assertEqual(common | {"project_key", "anchor_id", "document_revision", "node_id", "selection",
            "schema_version", "schema_manifest_sha256", "selection_artifact_id", "selection_sha256", "selection_object_version", "previous_anchor_id",
            "annotation_revision", "reason", "actor_id"}, columns(self.tables["annotation_anchor"]))
        self.assertEqual(common | {"anchor_id", "revision", "updated_at"}, columns(self.tables["annotation_effective_anchor"]))
        self.assertEqual(common | {"transition_id", "kind", "previous_revision", "revision", "previous_status",
            "status", "previous_anchor_id", "anchor_id", "document_revision", "actor_id", "reason"},
            columns(self.tables["annotation_transition"]))
        for name, identity in (("annotation_anchor", "anchor_id"), ("annotation_effective_anchor", "annotation_id"),
                               ("annotation_transition", "transition_id")):
            self.assertEqual(["tenant_id", "project_id", identity],
                             [column.name for column in next(self.tables[name].find_all(exp.PrimaryKey)).expressions])
        for name, revision in (("annotation_anchor", "annotation_revision"), ("annotation_transition", "revision")):
            unique = {tuple(column.name for column in key.this.expressions)
                      for key in self.tables[name].find_all(exp.UniqueColumnConstraint)}
            self.assertIn(("tenant_id", "project_id", "annotation_id", revision), unique)

    def test_exact_selection_artifact_bytes_and_node_scope_foreign_keys(self):
        refs = references(self.tables["annotation_anchor"])
        self.assertIn(("annotation", ("tenant_id", "project_id", "annotation_id"),
                       ("tenant_id", "project_id", "annotation_id")), refs)
        self.assertIn(("document_node", ("tenant_id", "project_id", "document_revision", "node_id"),
                       ("tenant_id", "project_id", "document_revision", "node_id")), refs)
        self.assertIn(("artifact", ("tenant_id", "project_key", "selection_artifact_id", "selection_sha256", "selection_object_version"),
                       ("tenant_id", "project_key", "artifact_id", "sha256", "object_version")), refs)
        self.assertEqual(6, len(refs))
        for name, fields in (("annotation_anchor", ["previous_anchor_id"]),
                            ("annotation_effective_anchor", ["anchor_id"]),
                            ("annotation_transition", ["previous_anchor_id", "anchor_id"])):
            refs = references(self.tables[name])
            for field in fields:
                self.assertIn(("annotation_anchor", ("tenant_id", "project_id", "annotation_id", field),
                               ("tenant_id", "project_id", "annotation_id", "anchor_id")), refs)
        self.assertIn(("document_revision_meta", ("tenant_id", "project_id", "document_revision"),
                       ("tenant_id", "project_id", "document_revision")), references(self.tables["annotation_transition"]))

    def test_exact_transition_guards_and_human_reanchor_always_open(self):
        checks = {name: [check.this.sql(dialect="mysql") for check in table.find_all(exp.CheckColumnConstraint)]
                  for name, table in self.tables.items()}
        expected = """(kind='INVALIDATE' AND previous_status IN ('OPEN','RESOLVED','RETURNED','REANCHOR_REQUIRED')
          AND status='REANCHOR_REQUIRED' AND previous_anchor_id=anchor_id)
          OR (kind='REANCHOR' AND previous_status='REANCHOR_REQUIRED' AND status='OPEN'
          AND previous_anchor_id<>anchor_id AND reason IS NOT NULL
          AND CHAR_LENGTH(reason) BETWEEN 1 AND 16000 AND CHAR_LENGTH(TRIM(reason))>0)"""
        self.assertIn(sqlglot.parse_one(expected, read="mysql").sql(dialect="mysql"), checks["annotation_transition"])
        self.assertIn(sqlglot.parse_one("previous_revision>0 AND revision=previous_revision+1", read="mysql").sql(dialect="mysql"), checks["annotation_transition"])
        self.assertEqual(3, len(checks["annotation_transition"]))
        self.assertEqual(3, len(checks["annotation_anchor"]))
        self.assertEqual(1, len(checks["annotation_effective_anchor"]))
        self.assertIn("previous_anchor_id IS NULL AND anchor_id=annotation_id AND annotation_revision=1 AND reason IS NULL", self.sql)
        self.assertIn("previous_anchor_id IS NOT NULL AND previous_anchor_id<>anchor_id AND annotation_revision>1", self.sql)

    def test_backfill_is_insert_only_and_copies_original_selection_and_real_artifact_version(self):
        self.assertEqual(5, len(self.parsed))
        self.assertEqual(2, len(self.fills))
        self.assertNotRegex(self.sql, r"(?im)^\s*(ALTER|UPDATE|DELETE|DROP|TRUNCATE)\b")
        first, second = self.fills
        self.assertIsInstance(first, exp.Insert)
        self.assertEqual("annotation_anchor", first.this.this.name)
        fields = [column.name for column in first.this.expressions]
        selected = dict(zip(fields, first.expression.expressions, strict=True))
        for target, source in {"anchor_id": "a.annotation_id", "annotation_id": "a.annotation_id",
                "document_revision": "a.document_revision", "node_id": "a.node_id", "selection": "a.selection",
                "schema_version": "a.schema_version", "schema_manifest_sha256": "a.schema_manifest_sha256",
                "selection_artifact_id": "a.selection_artifact_id", "selection_sha256": "a.quoted_sha256",
                "selection_object_version": "r.object_version", "annotation_revision": "1",
                "previous_anchor_id": "NULL", "reason": "NULL"}.items():
            self.assertEqual(source, selected[target].sql(dialect="mysql"))
        join = next(first.find_all(exp.Join))
        self.assertEqual("LEFT", join.side)
        self.assertEqual("artifact", join.this.name)
        self.assertEqual(sqlglot.parse_one("r.tenant_id=a.tenant_id AND r.project_key=a.project_id AND r.artifact_id=a.selection_artifact_id", read="mysql"), join.args["on"])
        self.assertIsNone(first.expression.args.get("where"))
        self.assertEqual("annotation_effective_anchor", second.this.this.name)
        self.assertEqual(["tenant_id", "project_id", "annotation_id", "annotation_id", "1", "created_by",
                          "created_at", "classification", "updated_at"],
                         [item.sql(dialect="mysql") for item in second.expression.expressions])

    def test_058_history_migration_bytes_remain_unchanged(self):
        self.assertEqual("daffd632e88ef21eba05570514b877b9df0f3d77562fae5b71c2e78b5f832bd2", hashlib.sha256((RESOURCES / "db/migration/058-annotation-decisions.sql").read_bytes()).hexdigest())


if __name__ == "__main__":
    unittest.main(verbosity=2)

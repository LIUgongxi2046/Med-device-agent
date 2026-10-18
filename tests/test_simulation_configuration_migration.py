"""Static migration contracts for the SIMULATION draft variant; never connects to MySQL."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import unittest
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from build_toolchains import resolve_toolchains

RESOURCES = ROOT / "business/medical-data/src/main/resources"
MIGRATION = "db/migration/057-simulation-model-configuration.sql"


class SimulationConfigurationMigrationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.sql = (RESOURCES / MIGRATION).read_text()
        home, _ = resolve_toolchains(ROOT)
        repo = ROOT / ".implementation-work/maven-repository"
        jars = [repo / name for name in (
            "com/github/jsqlparser/jsqlparser/5.2/jsqlparser-5.2.jar",
            "com/fasterxml/jackson/core/jackson-databind/2.21.4/jackson-databind-2.21.4.jar",
            "com/fasterxml/jackson/core/jackson-core/2.21.4/jackson-core-2.21.4.jar",
            "com/fasterxml/jackson/core/jackson-annotations/2.21/jackson-annotations-2.21.jar")]
        result = subprocess.run([str(home / "bin/java"), "--class-path", os.pathsep.join(map(str, jars)),
                                 str(ROOT / "tests/InspectAlterMigrations.java"), str(RESOURCES / MIGRATION)],
                                cwd=ROOT, capture_output=True, text=True, timeout=30, check=True)
        cls.alters = json.loads(result.stdout)

    def test_additive_migration_registered_once_with_unique_changesets(self):
        master = ElementTree.parse(RESOURCES / "db/changelog/master.xml")
        entries = master.findall("{http://www.liquibase.org/xml/ns/dbchangelog}include")
        self.assertEqual(1, sum(entry.attrib["file"] == MIGRATION for entry in entries))
        coverage = json.loads((RESOURCES / "db/migration/coverage.json").read_bytes())
        self.assertEqual(1, coverage["native_alter_migrations"].count(MIGRATION))
        identities = []
        for file in (RESOURCES / "db/migration").glob("*.sql"):
            identities.extend(re.findall(r"^--changeset ([^ ]+)", file.read_text(), re.M))
        self.assertEqual(len(identities), len(set(identities)))
        self.assertIn("medical-agent:1.0.0-311-simulation-model-head", identities)
        self.assertIn("medical-agent:1.0.0-312-simulation-model-revision", identities)

    def test_parsed_migration_only_changes_the_two_config_tables(self):
        self.assertEqual({"configuration_head", "model_config_revision"}, {item["table"] for item in self.alters})
        self.assertTrue(all(item["operation"] in {"ADD", "MODIFY"} for item in self.alters))
        self.assertNotIn("INSERT INTO", self.sql)
        self.assertNotIn("connection_verification", self.sql)
        self.assertNotIn("ALTER TABLE invocation", self.sql)
        self.assertNotIn("APPROVED", self.sql)

    def test_nullable_conversion_is_atomic_with_conditional_required_fields(self):
        statements = re.split(r";\s*(?:\n|$)", self.sql)
        revision = [part for part in statements if "ALTER TABLE model_config_revision" in part]
        self.assertEqual(1, len(revision))
        for column in ("base_url", "auth_ref", "credential_reference_id", "credential_revision", "price_version", "currency", "data_policy"):
            self.assertRegex(revision[0], r"MODIFY COLUMN " + column + r" [^\n]+ NULL")
            self.assertIn(column + " IS NOT NULL", revision[0])
            self.assertIn(column + " IS NULL", revision[0])
        self.assertIn("ADD CONSTRAINT ck_model_execution_mode CHECK", revision[0])
        self.assertIn("execution_mode='SIMULATION' AND status='DRAFT'", revision[0])

    def test_mode_is_pinned_for_all_historical_revisions_and_simulation_cannot_publish(self):
        self.assertIn("UNIQUE KEY uq_configuration_execution_mode (tenant_id,project_key,config_id,kind,execution_mode)", self.sql)
        self.assertIn("FOREIGN KEY (tenant_id,project_key,config_id,configuration_kind,execution_mode)", self.sql)
        self.assertIn("REFERENCES configuration_head (tenant_id,project_key,config_id,kind,execution_mode)", self.sql)
        self.assertIn("execution_mode='SIMULATION' AND kind='MODEL' AND status='DRAFT'", self.sql)
        self.assertEqual(2, self.sql.count("execution_mode VARCHAR(16) NOT NULL DEFAULT 'EXTERNAL'"))
        self.assertIn("JSON_CONTAINS_PATH(simulation_definition,'all'", self.sql)
        self.assertIn("JSON_TYPE(JSON_EXTRACT(simulation_definition,'$.production_eligible'))='BOOLEAN'", self.sql)
        self.assertIn("'$.case_binding.validation'))='DECLARED_ONLY'", self.sql)

    def test_component_provenance_is_copied_exactly_and_not_catalogue_extension(self):
        source = ROOT / "model-gateway/src/medical_model_gateway/simulation-execution.schema.json"
        resources = ROOT / "business/medical-persistence/src/main/resources"
        self.assertEqual(source.read_bytes(), (resources / "model-simulation-provenance.schema.json").read_bytes())
        digest = hashlib.sha256(source.read_bytes()).hexdigest()
        contract = json.loads((resources / "simulation-model-configuration.schema.json").read_bytes())
        self.assertEqual(digest, contract["$defs"]["Definition"]["properties"]["provenance_schema_sha256"]["const"])
        self.assertIn(digest, self.sql)
        self.assertEqual("dcd72acbb7a45775373662e0ffe4557030d4740b0afacdee8375e7768e761915",
                         hashlib.sha256((ROOT / "contracts/catalogue.json").read_bytes()).hexdigest())


if __name__ == "__main__":
    unittest.main()

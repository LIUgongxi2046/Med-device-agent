from collections import Counter
import hashlib
import json
import os
import re
from pathlib import Path
import subprocess
import sys
import unittest
from xml.etree import ElementTree

import sqlglot
from sqlglot import exp
import sqlparse


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from run_migration import resolve_java

RESOURCES = ROOT / "business/medical-data/src/main/resources"
NAMESPACE = {"lb": "http://www.liquibase.org/xml/ns/dbchangelog"}


def normalize_liquibase_comments(sql):
    # Only complete known Liquibase metadata lines qualify; executable SQL is unchanged.
    directive = r"(?:liquibase formatted sql|changeset [A-Za-z0-9_.:-]+ dbms:mysql runInTransaction:false failOnError:true)"
    return re.sub(r"(?m)^--(?=" + directive + r"\r?$)", "-- ", sql)


def sqlglot_migration_source(path):
    # MySQL requires whitespace after --. Never rewrite historical migration bytes.
    return normalize_liquibase_comments(path.read_text(encoding="utf-8"))


def parse_068_native_statements(path):
    # JSQLParser 5.2 times out on 068's unchanged compound DROP CHECK/ADD CHECK ALTER.
    # SQLGlot parses EVERY original statement/constraint; existing bundle checks still apply.
    if path.name != "068-document-technical-check.sql":
        raise ValueError("068_COMPATIBILITY_SCOPE_ONLY")
    from migration_076_compat import _first_statement, parse_workflow_alter, expected_alter, OLD_JOBS, PRIOR_SHA256
    if hashlib.sha256(path.read_bytes()).hexdigest() != PRIOR_SHA256:
        raise ValueError("068_ORIGINAL_BYTES_CHANGED")
    first, suffix = _first_statement(sqlglot_migration_source(path))
    statements = [parse_workflow_alter(first), *sqlglot.parse(suffix, read="mysql")]
    if statements[0] != expected_alter(OLD_JOBS) or any(list(s.find_all(exp.Command)) for s in statements):
        raise ValueError("068_COMPLETE_WORKFLOW_AST_REQUIRED")
    if len(statements) != 3 or not isinstance(statements[0], exp.Alter) or not all(
            isinstance(statement, exp.Create) and statement.kind == "TABLE" for statement in statements[1:]):
        raise ValueError("068_COMPLETE_STATEMENT_SHAPE_REQUIRED")
    return statements


class MigrationCommentNormalizationTests(unittest.TestCase):
    def test_only_complete_liquibase_metadata_comment_lines_are_normalized(self):
        header = "--liquibase formatted sql"
        change = "--changeset medical-agent:1.0.0-315-annotation-anchor dbms:mysql runInTransaction:false failOnError:true"
        sql = header + "\n" + change + "\nCREATE TABLE example (id INT);\n"
        self.assertEqual("-- " + header[2:] + "\n-- " + change[2:] + "\nCREATE TABLE example (id INT);\n",
                         normalize_liquibase_comments(sql))

    def test_inline_sql_literals_and_nonmetadata_comments_are_unchanged(self):
        sql = ("SELECT '--liquibase formatted sql'; --changeset x\n"
               "-- ordinary SQL comment\n--not_metadata\n"
               "--liquibase formatted sql; SELECT 1;\n"
               "--changeset unexpected syntax\n"
               "SELECT 1; --liquibase formatted sql\n")
        self.assertEqual(sql, normalize_liquibase_comments(sql))

    def test_crlf_comments_preserve_line_endings(self):
        self.assertEqual("-- liquibase formatted sql\r\nSELECT 1;\r\n",
                         normalize_liquibase_comments("--liquibase formatted sql\r\nSELECT 1;\r\n"))


def framework_index_signature(schema):
    result = []
    for constraint in schema.expressions:
        if isinstance(constraint, exp.PrimaryKey):
            result.append(("PRIMARY", True, tuple(column.name for column in constraint.expressions)))
        elif isinstance(constraint, exp.UniqueColumnConstraint):
            result.append((constraint.this.this.name if constraint.this.this else constraint.this.expressions[0].name,
                True, tuple(column.name for column in constraint.this.expressions)))
        elif isinstance(constraint, exp.IndexColumnConstraint):
            result.append((constraint.this.name, False, tuple(column.this.name for column in constraint.expressions)))
    return result


class MigrationBundleChecks(unittest.TestCase):
    def test_field_definition_fill_preserves_existing_field_values(self):
        fills = [entry for entry in self.native_xml if "data_fill" in entry]
        self.assertEqual(1, len(fills))
        fill = sqlglot.parse_one(fills[0]["data_fill"], read="mysql")
        self.assertIsInstance(fill, exp.Update)
        self.assertEqual("field_value", fill.this.name)
        self.assertIsNone(fill.args.get("where"))
        self.assertEqual(1, len(fill.expressions))
        assignment = fill.expressions[0]
        self.assertIsInstance(assignment, exp.EQ)
        self.assertEqual("field_definition_id", assignment.this.name)
        self.assertIsInstance(assignment.expression, exp.Column)
        self.assertEqual("field_id", assignment.expression.name)
        self.assertEqual("1.0.0-253-field-definition-identity", fills[0]["change_set"])
        change = next(entry for entry in self.xml_changes if entry.attrib["id"] == fills[0]["change_set"])
        removed = change.find("lb:dropForeignKeyConstraint", NAMESPACE)
        self.assertEqual({"baseTableName": "field_value", "constraintName": "field_value_ibfk_2"}, removed.attrib)
        added = change.find("lb:addForeignKeyConstraint", NAMESPACE)
        self.assertEqual("tenant_id,template_version,field_definition_id", added.attrib["baseColumnNames"])
        self.assertEqual("field_definition", added.attrib["referencedTableName"])
        self.assertEqual("tenant_id,template_version,field_id", added.attrib["referencedColumnNames"])

    @classmethod
    def setUpClass(cls):
        cls.java = resolve_java(ROOT)
        cls.manifest = json.loads((RESOURCES / "db/migration/coverage.json").read_bytes())
        cls.changelog = ElementTree.parse(RESOURCES / "db/changelog/master.xml")
        cls.statements = []
        cls.paths = []
        cls.xml_changes = []
        cls.native_alters = []
        cls.native_xml = []
        cls.triggers = []
        for entry in cls.changelog.findall("lb:changeSet/lb:sqlFile", NAMESPACE):
            path = entry.attrib["path"]
            actual = ROOT / path if path == "data-design/core.sql" else RESOURCES / path
            cls.paths.append(actual)
            cls.statements.extend(sqlglot.parse(sqlglot_migration_source(actual), read="mysql"))
        for entry in cls.changelog.findall("lb:include", NAMESPACE):
            actual = RESOURCES / entry.attrib["file"]
            cls.paths.append(actual)
            if actual.suffix == ".xml":
                document = ElementTree.parse(actual)
                cls.xml_changes.extend(document.findall("lb:changeSet", NAMESPACE))
                structured = entry.attrib["file"] in cls.manifest["structured_xml_migrations"]
                if structured or entry.attrib["file"] in cls.manifest["native_xml_migrations"]:
                    repository = ROOT / ".implementation-work/maven-repository"
                    libraries = [repository / "com/github/jsqlparser/jsqlparser/5.2/jsqlparser-5.2.jar",
                                 repository / "com/fasterxml/jackson/core/jackson-databind/2.21.4/jackson-databind-2.21.4.jar",
                                 repository / "com/fasterxml/jackson/core/jackson-core/2.21.4/jackson-core-2.21.4.jar",
                                 repository / "com/fasterxml/jackson/core/jackson-annotations/2.21/jackson-annotations-2.21.jar",
                                 repository / "org/liquibase/liquibase-core/4.31.1/liquibase-core-4.31.1.jar",
                                 repository / "org/apache/commons/commons-lang3/3.20.0/commons-lang3-3.20.0.jar",
                                 repository / "org/apache/commons/commons-text/1.13.0/commons-text-1.13.0.jar",
                                 repository / "org/apache/commons/commons-collections4/4.5.0/commons-collections4-4.5.0.jar",
                                 repository / "commons-io/commons-io/2.22.0/commons-io-2.22.0.jar",
                                 repository / "com/opencsv/opencsv/5.9/opencsv-5.9.jar",
                                 repository / "org/yaml/snakeyaml/2.4/snakeyaml-2.4.jar"]
                    from native_parser_cache import run_json
                    definitions = run_json([str(cls.java), "-Dfile.encoding=UTF-8", "-Djava.io.tmpdir=" + str(ROOT / ".implementation-work/java-temp"),
                                             "--class-path", os.pathsep.join(str(path.resolve(strict=True)) for path in libraries),
                                             str(ROOT / "tests/InspectXmlMigrations.java"), str(actual), *(["LIQUIBASE"] if structured else [])],
                                            source_bytes=actual.read_bytes(), libraries=libraries,
                                            parser=ROOT / "tests/InspectXmlMigrations.java", mode="XML_LIQUIBASE" if structured else "XML",
                                            stdout=subprocess.PIPE, text=True, check=True, timeout=60)
                    cls.native_xml.extend(definitions)
                    for definition in definitions:
                        if "create" in definition:
                            created = sqlglot.parse_one(definition["create"], read="mysql")
                            if not isinstance(created, exp.Create) or created.kind != "TABLE":
                                raise ValueError("CREATE_TABLE_REQUIRED")
                            cls.statements.append(created)
                        for change in definition.get("changes", []):
                            projected = sqlglot.parse_one(change["projection"], read="mysql")
                            if isinstance(projected, exp.Alter):
                                cls.statements.append(projected)
                            elif isinstance(projected, exp.Create) and isinstance(projected.this, exp.Schema):
                                cls.statements.append(exp.Alter(this=projected.this.this, actions=projected.this.expressions))
                            else:
                                raise ValueError("ALTER_DEFINITION_REQUIRED")
            elif actual.name == "068-document-technical-check.sql":
                cls.statements.extend(parse_068_native_statements(actual))
            elif actual.name == "099-task-professional-runtime.sql":
                from migration_099_compat import parse_099_native_statements
                cls.statements.extend(parse_099_native_statements(actual))
            elif actual.name == "076-formal-model-invocation.sql":
                from migration_076_compat import parse_076_native_statements
                cls.statements.extend(parse_076_native_statements(actual))
            elif entry.attrib["file"] in cls.manifest["native_alter_migrations"] + cls.manifest["native_sql_migrations"]:
                from test_simulation_input_artifact_migration import inspect_migration
                alterations, _ = inspect_migration(actual)
                cls.triggers.extend(item["trigger"] for item in alterations if "trigger" in item)
                cls.native_alters.extend({**item, "migration": entry.attrib["file"]} for item in alterations if "operation" in item)
                for alteration in alterations:
                    if "data_fill" in alteration:
                        fill = sqlglot.parse_one(alteration["data_fill"], read="mysql")
                        if not isinstance(fill, (exp.Update, exp.Insert)):
                            raise ValueError("DATA_FILL_DEFINITION_REQUIRED")
                        cls.statements.append(fill)
                    if "create" in alteration:
                        definition = sqlglot.parse_one(alteration["create"], read="mysql")
                        if not isinstance(definition, exp.Create) or definition.kind != "TABLE":
                            raise ValueError("CREATE_TABLE_REQUIRED")
                        cls.statements.append(definition)
                    if "definition" in alteration:
                        from migration_source_parser import parse_schema_source
                        definitions, _ = parse_schema_source(alteration["definition"])
                        if len(definitions) != 1:
                            raise ValueError("SINGLE_ALTER_DEFINITION_REQUIRED")
                        definition = definitions[0]
                        if isinstance(definition, exp.Alter):
                            cls.statements.append(definition)
                        elif isinstance(definition, exp.Create) and isinstance(definition.this, exp.Schema):
                            cls.statements.append(exp.Alter(this=definition.this.this, actions=definition.this.expressions))
                        else:
                            raise ValueError("ALTER_DEFINITION_REQUIRED")
            else:
                cls.statements.extend(sqlglot.parse(sqlglot_migration_source(actual), read="mysql"))
        cls.table_definitions = [statement for statement in cls.statements if isinstance(statement, exp.Create) and statement.kind == "TABLE"]
        cls.tables = {
            statement.this.this.name: statement
            for statement in cls.table_definitions
        }

    def test_generated_scope_keys_match_ascii_foreign_key_parents(self):
        for name in ('task_run_replay_intent', 'task_run_replay_registration', 'candidate_comparison_completion'):
            column = next(column for column in self.tables[name].this.expressions
                          if isinstance(column, exp.ColumnDef) and column.name == 'project_key')
            self.assertIn('CHARACTER SET ascii', column.sql('mysql'), name)
            self.assertEqual('VARCHAR(36)', column.kind.sql('mysql'))
            self.assertEqual(1, len(list(column.find_all(exp.ComputedColumnConstraint))))
        self.assertIn(('tenant_id', 'project_key', 'artifact_id'),
                      [tuple(key.expressions[i].name for i in range(len(key.expressions)))
                       for key in self.tables['candidate_comparison_completion'].find_all(exp.ForeignKey)
                       if key.args['reference'].this.this.name == 'artifact'])

    def test_tenant_run_identity_exists_before_dependent_journals(self):
        identity = next(index for index, statement in enumerate(self.statements)
                        if isinstance(statement, exp.Alter) and statement.this.name == 'task_run'
                        and any(tuple(column.name for column in key.this.expressions) == ('tenant_id', 'run_id')
                                for key in statement.find_all(exp.UniqueColumnConstraint)))
        first_dependent = next(index for index, statement in enumerate(self.statements)
                               if isinstance(statement, exp.Create)
                               and statement.this.this.name == 'task_section_writer_exact_intent')
        self.assertLess(identity, first_dependent)

    def test_trigger_targets_and_names_are_complete(self):
        names = [trigger["name"] for trigger in self.triggers]
        self.assertEqual(len(names), len(set(names)), "Duplicate trigger name")
        for trigger in self.triggers:
            self.assertIn(trigger["table"], self.tables)
            self.assertTrue(trigger["body"])

    def test_every_designed_entity_has_a_physical_migration(self):
        expected = [name for group in self.manifest["required_tables"].values() for name in group]
        expected.extend(self.manifest["platform_tables"])
        expected.extend(self.manifest["framework_tables"])
        self.assertEqual(len(expected), len(set(expected)))
        self.assertSetEqual(set(self.tables), set(expected))

    def test_plan_relations_preserve_full_version_and_task_ownership(self):
        expected = set(self.manifest["required_tables"]["task_plan_relations"])
        self.assertEqual({item["table"] for item in self.native_xml if "create" in item
                          and item["change_set"].startswith(tuple("1.0.0-" + str(value) for value in range(238, 250)))}, expected)
        for table in expected:
            with self.subTest(table=table):
                columns = {column.name for column in self.tables[table].this.expressions if isinstance(column, exp.ColumnDef)}
                self.assertLessEqual({"tenant_id", "project_id", "created_by", "created_at", "classification"}, columns)
        question_nodes = self.tables["plan_question_node"]
        references = {(key.args["reference"].this.this.name, tuple(item.name for item in key.expressions))
                      for key in question_nodes.find_all(exp.ForeignKey)}
        self.assertIn(("plan_question", ("tenant_id", "project_id", "plan_revision", "question_id", "section_id")), references)
        self.assertIn(("plan_section_node", ("tenant_id", "project_id", "plan_revision", "section_id", "node_id")), references)
        question_dependencies = self.tables["plan_question_dependency"]
        references = {tuple(item.name for item in key.expressions) for key in question_dependencies.find_all(exp.ForeignKey)
                      if key.args["reference"].this.this.name == "plan_question"}
        self.assertEqual(references, {("tenant_id", "project_id", "plan_revision", "question_id"),
                                      ("tenant_id", "project_id", "plan_revision", "depends_on_question_id")})
        changes = {entry.attrib["id"]: entry for entry in self.xml_changes}
        foreign_key = changes["1.0.0-238-plan-section-shape"].find("lb:dropForeignKeyConstraint", NAMESPACE)
        self.assertEqual(foreign_key.attrib, {"baseTableName": "plan_section", "constraintName": "plan_section_ibfk_2"})
        for suffix, table in (("238-plan-section-shape", "plan_section"), ("241-plan-dependency-order", "plan_dependency"),
                              ("243-task-question-identity", "research_question")):
            guard = changes["1.0.0-" + suffix].find("lb:preConditions", NAMESPACE)
            self.assertEqual(guard.attrib, {"onFail": "HALT", "onError": "HALT"})
            check = guard.find("lb:sqlCheck", NAMESPACE)
            self.assertEqual(check.attrib["expectedResult"], "0")
            self.assertEqual(sqlglot.parse_one(check.text, read="mysql"), sqlglot.parse_one("SELECT COUNT(*) FROM " + table, read="mysql"))

    def test_original_management_tables_preserve_complete_framework_definitions(self):
        names = {"sys_oper_log", "sys_dict_type", "sys_dict_data", "sys_logininfor", "sys_job", "sys_job_log",
                 "sys_notice", "sys_notice_read", "gen_table", "gen_table_column"}
        sources = []
        for statement in sqlparse.parse((ROOT / "business/sql/ry_20260417.sql").read_text(encoding="utf-8")):
            if statement.get_type() == "CREATE" and any(isinstance(token, sqlparse.sql.Identifier)
                    and token.get_real_name() in names for token in statement.tokens):
                sources.append(str(statement))
        repository = ROOT / ".implementation-work/maven-repository"
        libraries = [repository / "com/github/jsqlparser/jsqlparser/5.2/jsqlparser-5.2.jar",
                     repository / "com/fasterxml/jackson/core/jackson-databind/2.21.4/jackson-databind-2.21.4.jar",
                     repository / "com/fasterxml/jackson/core/jackson-core/2.21.4/jackson-core-2.21.4.jar",
                     repository / "com/fasterxml/jackson/core/jackson-annotations/2.21/jackson-annotations-2.21.jar"]
        result = subprocess.run([str(self.java),
            "-Dfile.encoding=UTF-8", "-Djava.io.tmpdir=" + str(ROOT / ".implementation-work/java-temp"),
            "--class-path", os.pathsep.join(str(path.resolve(strict=True)) for path in libraries),
            str(ROOT / "tests/InspectFrameworkTables.java")], input=json.dumps(sources, ensure_ascii=False),
            capture_output=True, text=True, check=True, timeout=60)
        original = {}
        original_indexes = {}
        for statement in json.loads(result.stdout):
            parsed = sqlglot.parse_one(statement["definition"], read="mysql")
            self.assertIsInstance(parsed, exp.Create)
            self.assertIsInstance(parsed.this, exp.Schema)
            name = parsed.this.this.name
            self.assertNotIn(name, original)
            original[name] = parsed
            original_indexes[name] = [(index["name"], index["unique"], tuple(index["columns"])) for index in statement["indexes"]]
        self.assertEqual(set(original), names)
        self.assertLessEqual(names, set(self.manifest["framework_tables"]))
        self.assertLessEqual(names, set(self.tables))
        for name, definition in original.items():
            with self.subTest(table=name):
                expected = definition.this.copy()
                actual = self.tables[name].this.copy()
                self.assertCountEqual(framework_index_signature(actual), original_indexes[name])
                actual.set("expressions", [column for column in actual.expressions if isinstance(column, exp.ColumnDef)])
                for schema in (expected, actual):
                    schema.comments = None
                    for node in schema.walk():
                        node.comments = None
                    for column in schema.find_all(exp.ColumnDef):
                        # MySQL 8.4不保留整数显示宽度；其余原字段和约束逐项保持。
                        if column.kind.this in (exp.DataType.Type.INT, exp.DataType.Type.BIGINT):
                            column.kind.set("expressions", [])
                        column.set("constraints", [constraint for constraint in column.constraints
                            if not isinstance(constraint.kind, exp.CommentColumnConstraint)])
                self.assertEqual(actual, expected)
        path = RESOURCES / "db/migration/034-ruoyi-management-tables.sql"
        self.assertIn(path, self.paths)
        operations = [operation for operation in self.table_definitions if operation.this.this.name in names]
        self.assertEqual(len(operations), len(names))
        self.assertTrue(all(isinstance(operation, exp.Create) and operation.kind == "TABLE" for operation in operations))

    def test_native_member_identity_changes_preserve_explicit_binding(self):
        identity_alters = [entry for entry in self.native_alters if entry["table"] == "identity_binding"]
        self.assertEqual(len(identity_alters), 8)
        self.assertEqual([entry["operation"] for entry in identity_alters],
                         ["ADD", "ALTER", "MODIFY", "MODIFY", "ADD", "ADD", "ADD", "ADD"])
        self.assertEqual(identity_alters[1]["drop_defaults"], ["mode"])
        columns = {}
        for entry in identity_alters:
            if "definition" in entry:
                columns.update({column.name: column for column in sqlglot.parse_one(entry["definition"], read="mysql")
                                .find_all(exp.ColumnDef)})
        self.assertEqual(set(columns), {"mode", "issuer", "subject", "local_user_id"})
        self.assertEqual(columns["local_user_id"].kind.sql(dialect="mysql"), "CHAR(36)")
        subject_collation = [constraint.kind.this.name for constraint in columns["subject"].constraints
                             if isinstance(constraint.kind, exp.CollateColumnConstraint)]
        self.assertEqual(subject_collation, ["utf8mb4_0900_bin"])
        definitions = [sqlglot.parse_one(entry["definition"], read="mysql") for entry in identity_alters
                       if "definition" in entry]
        foreign_keys = [item for definition in definitions for item in definition.find_all(exp.ForeignKey)]
        self.assertEqual(len(foreign_keys), 1)
        self.assertEqual([item.name for item in foreign_keys[0].expressions], ["local_user_id"])
        reference = foreign_keys[0].args["reference"].this
        self.assertEqual(reference.this.name, "platform_local_account")
        self.assertEqual([item.name for item in reference.expressions], ["user_id"])
        unique = [item for definition in definitions for item in definition.find_all(exp.UniqueColumnConstraint)]
        self.assertEqual(len(unique), 1)
        self.assertEqual([item.name for item in unique[0].this.expressions], ["tenant_id", "local_user_id"])
        checks = [item for definition in definitions for item in definition.find_all(exp.CheckColumnConstraint)]
        self.assertEqual(len(checks), 1)
        approved = sqlglot.parse_one("(mode = 'local' AND local_user_id IS NOT NULL AND user_id = local_user_id "
                                     "AND issuer IS NULL AND subject IS NULL) OR (mode = 'oidc' AND local_user_id "
                                     "IS NULL AND issuer IS NOT NULL AND subject IS NOT NULL AND CHAR_LENGTH(issuer) "
                                     "> 0 AND CHAR_LENGTH(subject) > 0)", read="mysql")
        self.assertEqual(checks[0].this, approved)

    def test_native_operation_retention_preserves_explicit_policy_and_expiry(self):
        original = [entry for entry in self.native_alters if entry["migration"] in {
            "db/migration/017-member-identity-mode.sql", "db/migration/024-operation-retention.sql",
            "db/migration/027-template-definition.sql", "db/migration/032-task-plan-content.sql"}]
        self.assertEqual(len(original), 17)
        self.assertEqual({entry["table"] for entry in original},
                         {"identity_binding", "operation_record", "version_registry", "template_version", "plan_revision"})
        plan_alters = [entry for entry in self.native_alters if entry["table"] == "plan_revision"]
        self.assertEqual([entry["operation"] for entry in plan_alters], ["ADD", "ADD"])
        plan_keys = {tuple(item.name for item in key.this.expressions) for entry in plan_alters
                     for key in sqlglot.parse_one(entry["definition"], read="mysql").find_all(exp.UniqueColumnConstraint)}
        self.assertEqual(plan_keys, {
            ("tenant_id", "project_id", "task_id", "task_revision", "plan_revision", "template_version", "snapshot_id", "content_sha256"),
            ("tenant_id", "project_id", "task_id", "plan_revision", "content_sha256"),
        })
        alterations = [entry for entry in self.native_alters if entry["table"] == "operation_record"]
        self.assertEqual(len(alterations), 4)
        self.assertEqual([entry["operation"] for entry in alterations], ["ADD"] * 4)
        definitions = [sqlglot.parse_one(entry["definition"], read="mysql") for entry in alterations]
        columns = {column.name: column for definition in definitions for column in definition.find_all(exp.ColumnDef)}
        self.assertEqual(set(columns), {"dedup_expires_at", "dedup_policy_revision"})
        self.assertEqual(columns["dedup_policy_revision"].kind.sql(dialect="mysql"), "CHAR(36)")
        for column in columns.values():
            self.assertEqual([constraint.kind.args["allow_null"] for constraint in column.constraints
                              if isinstance(constraint.kind, exp.NotNullColumnConstraint)], [True])
        foreign_keys = [item for definition in definitions for item in definition.find_all(exp.ForeignKey)]
        self.assertEqual(len(foreign_keys), 1)
        reference = foreign_keys[0].args["reference"].this
        self.assertEqual([item.name for item in foreign_keys[0].expressions], ["tenant_id", "dedup_policy_revision"])
        self.assertEqual(reference.this.name, "version_registry")
        self.assertEqual([item.name for item in reference.expressions], ["tenant_id", "version_id"])
        checks = [item for definition in definitions for item in definition.find_all(exp.CheckColumnConstraint)]
        self.assertEqual(len(checks), 1)
        approved = sqlglot.parse_one("(dedup_expires_at IS NULL AND dedup_policy_revision IS NULL) OR "
                                     "(dedup_expires_at IS NOT NULL AND dedup_policy_revision IS NOT NULL AND dedup_expires_at > created_at)", read="mysql")
        self.assertEqual(checks[0].this, approved)

    def test_local_account_operations_preserve_deduplication_and_secret_free_audit(self):
        self.assertLessEqual({"platform_local_operation", "platform_local_audit"}, set(self.tables))
        operation = self.tables["platform_local_operation"]
        fields = {column.name for column in operation.this.expressions if isinstance(column, exp.ColumnDef)}
        self.assertEqual(fields, {"operation_id", "subject_id", "operation_name", "idempotency_key", "request_hmac_sha256",
                                 "hmac_key_id", "http_status", "response_ref", "target_id", "schema_version",
                                 "schema_manifest_sha256", "created_by", "created_at", "classification"})
        keys = {tuple(item.name for item in key.this.expressions) for key in operation.find_all(exp.UniqueColumnConstraint)}
        self.assertIn(("subject_id", "operation_name", "idempotency_key"), keys)
        audit = self.tables["platform_local_audit"]
        fields = {column.name for column in audit.this.expressions if isinstance(column, exp.ColumnDef)}
        self.assertEqual(fields, {"audit_id", "operation_id", "subject_id", "target_id", "action", "resource_ref",
                                 "schema_version", "schema_manifest_sha256", "created_by", "created_at", "classification"})
        keys = {tuple(item.name for item in key.this.expressions) for key in audit.find_all(exp.UniqueColumnConstraint)}
        self.assertIn(("operation_id",), keys)

    def test_core_ddl_has_one_authoritative_source(self):
        self.assertEqual(Counter(self.paths)[ROOT / "data-design/core.sql"], 1)
        copied = list(RESOURCES.rglob("core.sql"))
        self.assertEqual(copied, [])
        core = sqlglot.parse((ROOT / "data-design/core.sql").read_text(encoding="utf-8"), read="mysql")
        self.assertEqual(len(core), 18)
        for table in core:
            self.assertEqual(self.tables[table.this.this.name], table)

    def test_context_identity_migration_preserves_existing_sessions(self):
        path = RESOURCES / "db/migration/015-platform-context-identity.xml"
        self.assertTrue(path.is_file(), "实际会话上下文必须具有稳定的内部 UUID")
        self.assertIn(path, self.paths)
        changes = ElementTree.parse(path).findall("lb:changeSet", NAMESPACE)
        self.assertEqual(len(changes), 4)
        self.assertEqual([list(change)[0].tag.rsplit("}", 1)[1] for change in changes],
                         ["addColumn", "sql", "addNotNullConstraint", "addUniqueConstraint"])
        column = changes[0].find("lb:addColumn/lb:column", NAMESPACE)
        self.assertEqual(column.attrib, {"name": "context_id", "type": "CHAR(36) CHARACTER SET ascii"})
        self.assertEqual(changes[0].find("lb:addColumn", NAMESPACE).attrib["tableName"], "platform_session_context")
        update = sqlglot.parse_one(changes[1].find("lb:sql", NAMESPACE).text, read="mysql")
        self.assertIsInstance(update, exp.Update)
        self.assertEqual(update.this.name, "platform_session_context")
        self.assertEqual(len(update.expressions), 1)
        self.assertEqual(update.expressions[0].this.name, "context_id")
        self.assertEqual(update.expressions[0].expression.sql(dialect="mysql"), "UUID()")
        self.assertEqual(update.args["where"].this.this.name, "context_id")
        self.assertIsInstance(update.args["where"].this, exp.Is)
        self.assertIsInstance(update.args["where"].this.expression, exp.Null)
        self.assertEqual(changes[2].find("lb:addNotNullConstraint", NAMESPACE).attrib,
                         {"tableName": "platform_session_context", "columnName": "context_id",
                          "columnDataType": "CHAR(36) CHARACTER SET ascii"})
        self.assertEqual(changes[3].find("lb:addUniqueConstraint", NAMESPACE).attrib,
                         {"tableName": "platform_session_context", "columnNames": "context_id",
                          "constraintName": "uq_platform_context_identity"})

    def test_platform_directory_preserves_the_oidc_identity_pair(self):
        self.assertIn("platform_identity", set(self.tables))
        table = self.tables["platform_identity"]
        columns = {column.name: column for column in table.this.expressions if isinstance(column, exp.ColumnDef)}
        self.assertNotIn("tenant_id", columns)
        self.assertLessEqual({"identity_id", "issuer", "subject", "user_id", "status", "created_by", "created_at", "classification", "revision", "updated_at"}, set(columns))
        unique = {tuple(item.name for item in key.this.expressions) for key in table.find_all(exp.UniqueColumnConstraint)}
        self.assertIn(("issuer", "subject"), unique)
        self.assertEqual([(item.name) for item in next(table.find_all(exp.PrimaryKey)).expressions], ["identity_id"])
        for name in ("issuer", "subject"):
            collations = [constraint.kind.this.name for constraint in columns[name].constraints if isinstance(constraint.kind, exp.CollateColumnConstraint)]
            self.assertEqual(collations, ["utf8mb4_0900_bin"])

    def test_application_sessions_preserve_identity_and_revocation_fields(self):
        self.assertIn("platform_oidc_session", set(self.tables))
        table = self.tables["platform_oidc_session"]
        columns = {column.name: column for column in table.this.expressions if isinstance(column, exp.ColumnDef)}
        self.assertNotIn("tenant_id", columns)
        self.assertSetEqual(set(columns), {"session_id", "identity_id", "registration_id", "client_id",
                           "provider_session_id", "oidc_issued_at", "status", "revoked_at", "created_by",
                           "created_at", "classification", "revision", "updated_at"})
        self.assertEqual([item.name for item in next(table.find_all(exp.PrimaryKey)).expressions], ["session_id"])
        references = list(table.find_all(exp.ForeignKey))
        self.assertEqual(len(references), 1)
        self.assertEqual([item.name for item in references[0].expressions], ["identity_id"])
        reference = references[0].args["reference"].this
        self.assertEqual(reference.this.name, "platform_identity")
        self.assertEqual([item.name for item in reference.expressions], ["identity_id"])
        for name in ("session_id", "registration_id", "client_id", "provider_session_id"):
            collations = [constraint.kind.this.name for constraint in columns[name].constraints if isinstance(constraint.kind, exp.CollateColumnConstraint)]
            self.assertEqual(collations, ["utf8mb4_0900_bin"])
        self.assertEqual(len(list(table.find_all(exp.CheckColumnConstraint))), 2)

    def test_logout_coordination_and_receipts_preserve_recovery_fields(self):
        expected = {
            "platform_oidc_client": {"client_key_sha256", "issuer", "client_id", "registration_id", "created_by",
                                     "created_at", "classification", "revision", "updated_at"},
            "platform_logout_receipt": {"logout_key_sha256", "client_key_sha256", "jti", "subject", "provider_session_id",
                                        "token_sha256", "oidc_issued_at", "oidc_expires_at", "status", "completed_at",
                                        "created_by", "created_at", "classification", "revision", "updated_at"},
            "platform_logout_session": {"logout_key_sha256", "session_id", "created_by", "created_at", "classification"},
        }
        for name, fields in expected.items():
            self.assertIn(name, set(self.tables))
            table = self.tables[name]
            columns = {column.name: column for column in table.this.expressions if isinstance(column, exp.ColumnDef)}
            self.assertSetEqual(set(columns), fields)
            self.assertNotIn("tenant_id", columns)
        self.assertEqual([item.name for item in next(self.tables["platform_logout_session"].find_all(exp.PrimaryKey)).expressions],
                         ["logout_key_sha256", "session_id"])
        self.assertEqual(len(list(self.tables["platform_oidc_client"].find_all(exp.CheckColumnConstraint))), 1)
        self.assertEqual(len(list(self.tables["platform_logout_receipt"].find_all(exp.CheckColumnConstraint))), 5)
        for name, field in (("platform_oidc_client", "issuer"), ("platform_oidc_client", "client_id"),
                            ("platform_oidc_client", "registration_id"), ("platform_logout_receipt", "jti"),
                            ("platform_logout_receipt", "subject"), ("platform_logout_receipt", "provider_session_id"),
                            ("platform_logout_session", "session_id")):
            column = next(item for item in self.tables[name].this.expressions if isinstance(item, exp.ColumnDef) and item.name == field)
            collations = [constraint.kind.this.name for constraint in column.constraints if isinstance(constraint.kind, exp.CollateColumnConstraint)]
            self.assertEqual(collations, ["utf8mb4_0900_bin"])

    def test_local_accounts_and_sessions_preserve_independent_identity(self):
        expected = {
            "platform_local_account": {"user_id", "username", "password_hash", "display_name", "status", "platform_role",
                                       "failed_attempts", "locked_until", "credential_revision", "created_by", "created_at",
                                       "classification", "revision", "updated_at"},
            "platform_local_session": {"session_id", "user_id", "credential_revision", "status", "revoked_at",
                                       "created_by", "created_at", "classification", "revision", "updated_at"},
            "platform_local_bootstrap": {"coordination_key", "created_by", "created_at", "classification", "revision", "updated_at"},
        }
        for name, fields in expected.items():
            self.assertIn(name, set(self.tables))
            columns = {column.name for column in self.tables[name].this.expressions if isinstance(column, exp.ColumnDef)}
            self.assertSetEqual(columns, fields)
            self.assertNotIn("tenant_id", columns)
        account = self.tables["platform_local_account"]
        unique = {tuple(item.name for item in key.this.expressions) for key in account.find_all(exp.UniqueColumnConstraint)}
        self.assertIn(("username",), unique)
        self.assertEqual([item.name for item in next(account.find_all(exp.PrimaryKey)).expressions], ["user_id"])

    def test_primary_and_foreign_keys_preserve_enterprise_identity(self):
        self.assertEqual(len(self.table_definitions), len(self.tables))
        business = [name for group in self.manifest["required_tables"].values() for name in group]
        # 054 adds one definition table, 055 adds two rule workflow tables,
        # and 056 adds three evaluation tables. 057 only alters existing tables.
        # 058 adds exactly one immutable annotation-decision history table.
        # 059 adds three independent anchor lifecycle tables, preserving original identities.
        additions = {
            "task_field_target": {'task_field_mapping_head', 'task_field_target_entry', 'task_field_mapping_revision', 'task_field_target_attempt', 'task_field_target_intent', 'task_field_source_read_receipt', 'task_field_target_input_capability', 'task_field_target_registration', 'task_field_target_input_capability_decision', 'task_field_target_input_capability_state'},
            'formal_evaluation_node_input_object': {'formal_evaluation_node_input_intent', 'formal_evaluation_node_input_attempt', 'formal_evaluation_node_input_registered'},
            'task_run_bootstrap': {'task_run_replay_intent', 'task_run_replay_attempt', 'task_run_replay_registration', 'task_run_workflow_launch', 'task_run_workflow_attempt', 'task_run_workflow_acceptance'},
            'task_delegation': {'task_delegation_rule', 'task_delegation_state', 'task_delegation_decision'},
            'task_run_replay_governance': {'task_run_replay_governance_manifest', 'task_run_replay_governance_member', 'task_run_replay_governance_edge'},
            'formal_evaluation_plan_preparation': {'formal_evaluation_plan_preparation_attempt', 'formal_evaluation_plan_preparation_registered', 'formal_evaluation_plan_preparation_intent'},
            'formal_evaluation_exact_input_object': {'formal_evaluation_exact_input_intent', 'formal_evaluation_exact_input_registered', 'formal_evaluation_exact_input_attempt'},
            'formal_agent_publication': {'formal_agent_acceptance', 'formal_agent_publication_intent', 'formal_agent_publication_attempt', 'formal_agent_publication_registration', 'formal_agent_publication_disable'},
            'formal_evaluation_context': {'formal_evaluation_context_version', 'formal_evaluation_context_decision', 'formal_evaluation_context_state', 'formal_evaluation_execution_context'},
            'model_enablement_preparation': {'model_enablement_policy_preparation'},
            'formal_evaluation_runtime': {'professional_runtime_observation', 'formal_evaluation_cell', 'formal_evaluation_execution', 'formal_evaluation_node_result', 'formal_evaluation_node', 'professional_runtime_session'},
            'formal_agent_candidate': {'formal_agent_candidate_binding'},
            'formal_evaluation_policy': {'formal_evaluation_policy_input'},
            'model_verification_input': {'model_verification_input_preparation'},
            'expert_qualification': {'expert_qualification', 'expert_score_binding', 'expert_qualification_record', 'expert_qualification_state', 'expert_score_authority_witness'},
            'persistent_run_runtime': {'step_runtime_attempt', 'runtime_object_intent', 'runtime_governance_manifest', 'run_runtime_intent', 'runtime_registered_object', 'runtime_governance_edge', 'runtime_governance_member', 'runtime_object_attempt', 'runtime_object_state', 'runtime_node_definition', 'run_runtime_receipt'},
            'formal_model_invocation': {'formal_invocation_object_attempt', 'formal_invocation_binding', 'formal_invocation_object_state', 'formal_model_verification_job', 'formal_invocation_registered_object', 'formal_invocation_cancellation', 'formal_invocation_object_intent', 'formal_invocation_accounting', 'formal_invocation_uncertainty', 'formal_invocation_attempt', 'formal_model_manifest'},
            'model_eligibility_connection': {'runtime_connection_start', 'model_eligibility', 'runtime_connection_intent', 'runtime_connection_completed'},
            'agent_execution_policy': {'agent_execution_policy_version', 'agent_execution_policy_component', 'agent_execution_policy_state', 'agent_execution_policy_decision'},
            'formal_evaluation_input': {'formal_evaluation_input_registration'},
            'formal_capability_cancellation': {'formal_capability_cancellation'},
            "agent_publication_authoring": {"agent_publication_record", "evaluation_acceptance_policy_decision"},
            "runtime_retention": {"runtime_retention_policy", "runtime_retention_state", "runtime_retention_retirement", "runtime_retention_cancellation"},
            "document_comparison": {"document_comparison_intent", "document_comparison_registration", "document_comparison_attempt"},
            "gru_record_export": {"gru_record_export_intent", "gru_record_export", "gru_record_export_attempt"},
            "document_technical_check_recovery": {"document_technical_check_intent", "document_technical_check_attempt"},
            "document_technical_check": {"document_technical_check_manifest", "document_technical_check"},
            "document_return_for_revision": {"document_revision_return", "document_revision_return_assignment"},
            "gru_records": {"gru_conversation_context", "gru_message_record"},
            "simulation_evaluation_preparation": {"simulation_evaluation_preparation"},
            "simulation_input_artifact": {"simulation_fixture_input_artifact", "simulation_input_artifact_operation"},
            "simulation_rule_binding": {"simulation_rule_binding", "simulation_rule_approval_binding"},
            "simulation_result_settlement": {"simulation_result_persistence", "simulation_quota_settlement"},
            "simulation_evaluation_job": {"simulation_scope_manifest", "simulation_evaluation_job", "simulation_context_dependency_manifest"},
            "simulation_ledger": {"simulation_quota_account", "simulation_execution_authority", "simulation_invocation_provenance", "simulation_quota_reservation", "simulation_invocation_fact", "simulation_fixture_registration", "simulation_retention_policy"},
            "annotation_anchors": {"annotation_anchor", "annotation_effective_anchor", "annotation_transition"},
            "annotation_decisions": {"annotation_decision"},
            "model_configuration": {"model_configuration_definition"},
            "rule_workflow": {"rule_definition", "rule_approval_decision"},
            "evaluation_results": {"evaluation_definition", "evaluation_score_observation", "evaluation_result"},
        }
        additions.update({'task_run_planned_graph': {'task_run_planned_edge', 'task_run_planned_graph'}, 'task_node_input': {'task_node_input_attempt', 'task_node_input_intent', 'task_node_input_registered'}, 'task_node_producer': {'task_node_producer_state', 'task_node_producer_rule', 'task_node_producer_decision'}, 'task_professional_runtime': {'professional_runtime_supervision', 'runtime_professional_object_binding'}})
        additions["task_professional_package_authoring"] = {
            "task_package_authoring_intent", "task_package_authoring_registration",
            "task_package_authoring_rejection",
        }
        additions["strict_comparison_governance"] = {
            "strict_comparison_policy_revision", "strict_comparison_policy_state",
            "strict_comparison_policy_decision", "strict_decimal_evidence_revision",
        }
        additions['task_plan_draft'] = {'task_plan_draft_job', 'task_plan_draft_object_intent', 'task_plan_draft_object_attempt', 'task_plan_draft_candidate', 'task_plan_draft_check', 'task_plan_draft_tool_receipt', 'task_plan_draft_origin', 'task_plan_draft_runtime_prepare'}
        additions['task_plan_draft_launch'] = {'task_plan_draft_launch_intent', 'task_plan_draft_launch_attempt', 'task_plan_draft_launch_receipt', 'task_plan_draft_completion'}
        additions['task_plan_draft_invocation'] = {'task_plan_draft_exact_intent', 'task_plan_draft_exact_attempt', 'task_plan_draft_exact_registration'}
        additions['task_strict_field'] = {'task_strict_field_input', 'task_strict_field_source_read', 'task_strict_field_result'}
        additions['task_strict_field_typed'] = {'task_strict_field_context_revision', 'task_strict_field_context_decision', 'task_strict_field_typed_revision', 'task_strict_field_typed_decision', 'task_strict_field_context_state', 'task_strict_field_typed_state', 'task_strict_field_typed_intent', 'task_strict_field_typed_registration'}
        additions["task_section_writer_producer"] = {"task_section_writer_producer_rule", "task_section_writer_producer_state", "task_section_writer_producer_decision"}
        additions["task_strict_field_output"] = {'task_strict_field_rule_set', 'task_strict_field_output_intent', 'task_strict_field_output_registration', 'task_strict_field_method_read'}
        additions['task_section_writer'] = {'task_section_writer_object_intent', 'task_section_writer_object_attempt', 'task_section_writer_object', 'task_section_writer_input', 'task_section_writer_submission'}
        additions['task_section_writer_invocation'] = {'task_section_writer_exact_intent', 'task_section_writer_exact_attempt', 'task_section_writer_exact_registration'}
        additions['task_section_writer_runtime'] = {'task_section_writer_runtime_prepare'}
        additions['document_initial_revision'] = {'document_initial_intent', 'document_initial_object', 'document_initial_registration'}
        additions['task_source_verifier'] = {'task_source_verifier_object_intent', 'task_source_verifier_object_attempt', 'task_source_verifier_object', 'task_source_verifier_input', 'task_source_verifier_producer_rule', 'task_source_verifier_producer_state', 'task_source_verifier_producer_decision', 'task_source_verifier_rule_set', 'task_source_verifier_submission', 'task_source_verifier_issue_proposal'}
        additions['task_source_verifier_runtime'] = {'task_source_verifier_runtime_prepare', 'task_source_verifier_context_read', 'task_source_verifier_independent_read'}
        additions['task_source_verifier_invocation'] = {'task_source_verifier_exact_intent', 'task_source_verifier_exact_attempt', 'task_source_verifier_exact_registration'}
        additions['document_professional_review'] = {'document_review_intent', 'document_review_object_intent', 'document_review_object', 'document_review_result', 'document_review_object_attempt'}
        additions['task_candidate_assembly'] = {'task_candidate_assembly_producer_decision', 'task_candidate_assembly_object_intent', 'task_candidate_assembly_object_attempt', 'task_candidate_assembly_rule_set', 'task_candidate_assembly_producer_state', 'task_candidate_assembly_object', 'task_candidate_assembly_producer_rule'}
        additions['document_check_execution'] = {'document_review_launch_receipt', 'document_review_execution', 'document_review_completion', 'document_review_launch_intent', 'document_review_launch_attempt'}
        additions['document_review_runtime'] = {'document_review_runtime_prepare'}
        additions['document_review_invocation'] = {'document_review_exact_registration', 'document_review_exact_intent', 'document_review_exact_attempt'}
        additions['document_review_context_read'] = {'document_review_independent_read', 'document_review_context_read'}
        additions['template_application'] = {'template_application_head', 'template_application_decision'}
        additions['document_candidate_adoption'] = {'document_adoption_completion', 'document_adoption_intent', 'document_adoption_preview'}
        additions['document_adoption_output'] = {'document_adoption_save', 'document_adoption_output'}
        additions['professional_issue_lifecycle'] = {'professional_issue_decision', 'professional_issue_occurrence'}
        additions['human_evidence_review'] = {'human_evidence_decision', 'human_evidence_batch'}
        additions['strict_field_human'] = {'strict_field_human_decision', 'strict_field_evidence_question', 'strict_field_human_head'}
        additions['shared_fact_lifecycle'] = {'shared_fact_policy_head', 'shared_fact_adopted_head', 'shared_fact_policy', 'shared_fact_lifecycle_operation', 'shared_fact_check', 'shared_fact_verification', 'shared_fact_check_rule', 'shared_fact_proposal_head', 'shared_fact_adoption', 'shared_fact_proposal'}
        additions['document_review_confirmations'] = {'document_review_revision_assignment', 'document_review_confirmation_receipt', 'document_review_resubmission'}
        additions['candidate_comparison'] = {'candidate_comparison_intent', 'candidate_comparison_completion'}
        additions['version_change_impact'] = {'change_revision_entry', 'change_impact_application', 'change_impact_decision', 'change_impact_resolution', 'change_impact_operation', 'change_proposal_scope'}
        additions['document_signing'] = {'document_signing_policy', 'document_signing_policy_head', 'document_signed_content', 'document_signing_operation', 'document_signing_callback', 'document_signing_challenge_binding', 'document_signing_policy_approval'}
        additions['task_run_continuation'] = {'task_run_continuation', 'task_input_continuation', 'task_run_preruntime_termination'}
        additions['formal_evaluation_jobs'] = {'formal_evaluation_launch_intent', 'formal_evaluation_job', 'formal_evaluation_launch_receipt', 'formal_evaluation_launch_attempt'}
        additions['formal_evaluation_node_runtime'] = {'formal_evaluation_node_dispatch', 'formal_evaluation_runtime_prepare', 'formal_evaluation_node_outcome', 'formal_evaluation_host_launch_attempt', 'formal_evaluation_node_waiting', 'formal_evaluation_cell_outcome', 'formal_evaluation_cancel_intent'}
        additions['formal_evaluation_professional_completion'] = {'formal_evaluation_professional_completion'}
        additions['formal_evaluation_invocation'] = {'formal_evaluation_invocation_context'}
        additions['formal_evaluation_context_read'] = {'formal_evaluation_context_read'}
        additions['formal_evaluation_runtime_objects'] = {'formal_evaluation_runtime_object_registered', 'formal_evaluation_runtime_object_intent', 'formal_evaluation_runtime_object_attempt'}
        additions['scenario_specification'] = {'scenario_task_entry', 'scenario_record'}
        additions['formal_evaluation_call_exact_input'] = {'formal_evaluation_call_exact_registration', 'formal_evaluation_call_exact_intent', 'formal_evaluation_call_exact_attempt'}
        additions['formal_evaluation_tool_receipts'] = {'formal_evaluation_tool_receipt', 'formal_evaluation_tool_question'}
        additions['formal_evaluation_stage_input'] = {'formal_evaluation_stage_input_intent', 'formal_evaluation_case_approval', 'formal_evaluation_stage_input_attempt', 'formal_evaluation_case_original', 'formal_evaluation_stage_input_registered'}
        additions['export_layout_recovery'] = {'export_layout_profile', 'export_layout_verification'}
        additions['task_deterministic_execution'] = {'task_deterministic_execution'}
        additions['formal_evaluation_deterministic'] = {'formal_evaluation_deterministic_object_intent', 'formal_evaluation_deterministic_object_attempt', 'formal_evaluation_deterministic_object_registered', 'formal_evaluation_deterministic_execution', 'formal_evaluation_deterministic_outcome'}
        additions['dense_retrieval'] = {'dense_index_configuration', 'dense_operation', 'dense_budget_reservation', 'dense_index_vector', 'dense_operation_source'}
        additions['task_success_reuse'] = {'task_run_success_reuse', 'task_run_assembly_success_reuse'}
        additions['gru_execution'] = {'gru_execution_action', 'gru_execution_message', 'gru_owner_memory', 'gru_execution_presentation_context'}
        additions['approved_web_acquisition'] = {'approved_web_query', 'approved_web_query_decision', 'approved_web_acquisition', 'approved_web_candidate'}
        additions['customer_connector'] = {'customer_connector_head', 'customer_connector_definition', 'customer_connector_decision', 'customer_connector_cursor', 'customer_connector_run', 'customer_connector_item'}
        additions['operations_feedback'] = {'operations_feedback_event'}
        additions['source_copy_authority'] = {'source_copy_authority'}
        additions['framework_binding'] = {'framework_binding_manifest', 'framework_binding_approval', 'framework_binding_operation'}
        additions['knowledge_feedback'] = {'knowledge_feedback_origin', 'knowledge_feedback_event'}
        additions['framework_form_publication'] = {'framework_form_schema', 'framework_form_approval', 'framework_form_operation'}
        for group, expected in additions.items():
            self.assertSetEqual(set(self.manifest["required_tables"][group]), expected)
        self.assertEqual(len(business), 178 + sum(len(tables) for tables in additions.values()))
        self.assertEqual(526, len(business))
        self.assertEqual(565, len(self.tables))
        for name in business:
            table = self.tables[name]
            with self.subTest(table=name):
                columns = {column.name for column in table.this.expressions if isinstance(column, exp.ColumnDef)}
                self.assertIn("tenant_id", columns)
                primary = list(table.find_all(exp.PrimaryKey))
                self.assertEqual(len(primary), 1)
                self.assertEqual(primary[0].expressions[0].name, "tenant_id")
                for foreign in table.find_all(exp.ForeignKey):
                    self.assertEqual(foreign.expressions[0].name, "tenant_id")
                    reference = foreign.args["reference"].this
                    self.assertEqual(reference.expressions[0].name, "tenant_id")
                    self.assertIn(reference.this.name, self.tables)

    def test_member_routes_and_session_context_keep_subject_and_enterprise_bindings(self):
        expected = {
            "platform_member_route": {"user_id", "tenant_id", "member_id", "created_by", "created_at",
                                      "classification", "revision", "updated_at"},
            "platform_session_context": {"session_id", "user_id", "tenant_id", "context_revision", "auth_epoch",
                                         "governance_epoch", "created_by", "created_at", "classification", "revision", "updated_at"},
        }
        for name, fields in expected.items():
            self.assertIn(name, self.tables)
            columns = {column.name for column in self.tables[name].this.expressions if isinstance(column, exp.ColumnDef)}
            self.assertSetEqual(columns, fields)
        route = self.tables["platform_member_route"]
        self.assertEqual([item.name for item in next(route.find_all(exp.PrimaryKey)).expressions], ["user_id", "tenant_id"])
        references = list(route.find_all(exp.ForeignKey))
        self.assertEqual(len(references), 1)
        self.assertEqual([item.name for item in references[0].expressions], ["tenant_id", "member_id"])
        self.assertEqual(references[0].args["reference"].this.this.name, "tenant_member")
        context = self.tables["platform_session_context"]
        self.assertEqual([item.name for item in next(context.find_all(exp.PrimaryKey)).expressions], ["session_id"])
        self.assertEqual(len(list(context.find_all(exp.CheckColumnConstraint))), 2)

    def test_foreign_keys_reference_complete_unique_keys(self):
        keys = {}
        columns = {}
        for name, table in self.tables.items():
            columns[name] = {column.name: column for column in table.this.expressions if isinstance(column, exp.ColumnDef)}
            keys[name] = {tuple(item.name for item in key.expressions) for key in table.find_all(exp.PrimaryKey)}
            keys[name].update(tuple(item.name for item in key.this.expressions) for key in table.find_all(exp.UniqueColumnConstraint))
        for statement in self.statements:
            if isinstance(statement, exp.Alter):
                keys[statement.this.name].update(tuple(item.name for item in key.this.expressions) for key in statement.find_all(exp.UniqueColumnConstraint))
                columns[statement.this.name].update({column.name: column for column in statement.find_all(exp.ColumnDef)})
        # XML 029 renames the physical identity table and retains the old name
        # only as a read view. Historical FKs follow the rename in MySQL; newly
        # declared framework ledgers must point at the physical identity PK.
        identity_xml = ElementTree.parse(RESOURCES / 'db/migration/029-ruoyi-account-binding.xml')
        rename = identity_xml.find('lb:changeSet/lb:renameTable', NAMESPACE)
        self.assertEqual({'oldTableName': 'platform_local_account',
                          'newTableName': 'platform_local_identity_metadata'}, rename.attrib)
        view = identity_xml.find('lb:changeSet/lb:createView', NAMESPACE)
        self.assertEqual('platform_local_account', view.attrib['viewName'])
        keys[rename.attrib['newTableName']] = keys[rename.attrib['oldTableName']]
        columns[rename.attrib['newTableName']] = columns[rename.attrib['oldTableName']]
        for statement in self.statements:
            if isinstance(statement, exp.Create):
                source = statement.this.this.name
            elif isinstance(statement, exp.Alter):
                source = statement.this.name
            elif isinstance(statement, (exp.Insert, exp.Update)):
                continue
            else:
                raise ValueError("迁移包含未登记的语句类型")
            for foreign in statement.find_all(exp.ForeignKey):
                reference = foreign.args["reference"].this
                target = reference.this.name
                local = tuple(item.name for item in foreign.expressions)
                remote = tuple(item.name for item in reference.expressions)
                with self.subTest(source=source, target=target, columns=local):
                    if source in self.manifest["platform_tables"]:
                        if source in ("platform_framework_job_command", "platform_framework_job_revision"):
                            expected_job_fk = {"actor_id": ("platform_local_identity_metadata", "user_id"),
                                               "created_by": ("platform_local_identity_metadata", "user_id"),
                                               "job_id": ("sys_job", "job_id")}
                            if source == "platform_framework_job_revision":
                                expected_job_fk = {"job_id": ("sys_job", "job_id"),
                                                   "command_id": ("platform_framework_job_command", "command_id"),
                                                   "created_by": ("platform_local_identity_metadata", "user_id")}
                            self.assertEqual(len(local), 1)
                            self.assertEqual(len(remote), 1)
                            self.assertIn(local[0], expected_job_fk)
                            self.assertEqual((target, remote[0]), expected_job_fk[local[0]])
                            self.assertIn(remote, keys[target])
                            self.assertEqual(columns[source][local[0]].kind, columns[target][remote[0]].kind)
                            continue
                        if source in ("platform_local_operation", "platform_local_audit"):
                            expected_local = {
                                "subject_id": ("platform_local_account", "user_id"),
                                "target_id": ("platform_local_account", "user_id"),
                                "created_by": ("platform_local_account", "user_id"),
                            }
                            if source == "platform_local_audit":
                                expected_local["operation_id"] = ("platform_local_operation", "operation_id")
                            self.assertEqual(len(local), 1)
                            self.assertIn(local[0], expected_local)
                            self.assertEqual((target, remote[0]), expected_local[local[0]])
                            self.assertEqual(len(remote), 1)
                            self.assertIn(remote, keys[target])
                            self.assertEqual(columns[source][local[0]].kind, columns[target][remote[0]].kind)
                            continue
                        expected = {
                            ("platform_enterprise_registration", "platform_local_account"): None,
                            ("platform_enterprise_registration_audit", "platform_local_account"): None,
                            ("platform_enterprise_registration_audit", "platform_enterprise_registration"):
                                ("registration_id", "operation_id", "subject_id", "first_admin_user_id"),
                            ("platform_enterprise_registration_decision", "platform_enterprise_registration"): ("registration_id",),
                            ("platform_enterprise_registration_decision", "platform_local_account"): None,
                            ("platform_enterprise_registration_decision", "tenant_member"): ("tenant_id", "member_id"),
                            ("platform_enterprise_registration_decision_audit", "platform_enterprise_registration_decision"):
                                ("registration_id", "operation_id", "subject_id"),
                            ("platform_oidc_session", "platform_identity"): ("identity_id",),
                            ("platform_logout_receipt", "platform_oidc_client"): ("client_key_sha256",),
                            ("platform_logout_session", "platform_logout_receipt"): ("logout_key_sha256",),
                            ("platform_logout_session", "platform_oidc_session"): ("session_id",),
                            ("platform_local_session", "platform_local_account"): ("user_id",),
                            ("platform_member_route", "tenant_member"): ("tenant_id", "member_id"),
                            ("platform_invitation_route", "member_invitation"): ("tenant_id", "invitation_id", "user_id"),
                            ("platform_session_context", "tenant"): ("tenant_id",),
                        }
                        self.assertIn((source, target), expected)
                        if source in ("platform_enterprise_registration", "platform_enterprise_registration_audit", "platform_enterprise_registration_decision") and target == "platform_local_account":
                            self.assertIn(local, (("subject_id",), ("first_admin_user_id",), ("created_by",)))
                            self.assertEqual(remote, ("user_id",))
                            self.assertIn(remote, keys[target])
                            self.assertEqual(columns[source][local[0]].kind, columns[target][remote[0]].kind)
                            continue
                        self.assertEqual(local, expected[(source, target)])
                        self.assertEqual(remote, expected[(source, target)])
                    else:
                        if source == "identity_binding" and target == "platform_local_account":
                            self.assertEqual(local, ("local_user_id",))
                            self.assertEqual(remote, ("user_id",))
                            self.assertIn(remote, keys[target])
                            self.assertEqual(columns[source][local[0]].kind, columns[target][remote[0]].kind)
                            continue
                        self.assertEqual(local[0], "tenant_id")
                        self.assertEqual(remote[0], "tenant_id")
                    self.assertEqual(len(local), len(remote))
                    self.assertIn(remote, keys[target])
                    for left, right in zip(local, remote, strict=True):
                        self.assertIn(left, columns[source])
                        self.assertIn(right, columns[target])
                        self.assertEqual(columns[source][left].kind, columns[target][right].kind)
                        # MySQL rejects equal SQL types with incompatible character sets (3780).
                        def charset(column):
                            declared = list(column.find_all(exp.CharacterSetColumnConstraint))
                            return declared[0].this.name.lower() if declared else 'utf8mb4'
                        self.assertEqual(charset(columns[source][left]), charset(columns[target][right]),
                                         source + '.' + left + ' -> ' + target + '.' + right)

    def test_supplemental_rows_preserve_audit_and_json_schema_identity(self):
        core = set(self.manifest["required_tables"]["core"])
        for name, table in self.tables.items():
            if name in core or name in self.manifest["framework_tables"]:
                continue
            columns = {column.name: column for column in table.this.expressions if isinstance(column, exp.ColumnDef)}
            for statement in self.statements:
                if isinstance(statement, exp.Alter) and statement.this.name == name:
                    columns.update({column.name: column for column in statement.find_all(exp.ColumnDef)})
            expected = {"created_by", "created_at", "classification"}
            if any(column.kind.this == exp.DataType.Type.JSON for column in columns.values()):
                expected.update({"schema_version", "schema_manifest_sha256"})
            # These two closed sidecars inherit only the named fields from their exact owner.
            inherited = {
                "professional_runtime_observation": ("professional_runtime_session", ("tenant_id", "session_id"), {"classification"}),
                "formal_evaluation_policy_input": ("agent_publication_record", ("tenant_id", "record_id"), {"created_by", "created_at", "classification"}),
            }
            if name in inherited:
                parent, identity, fields = inherited[name]
                references = {(key.args["reference"].this.this.name, tuple(item.name for item in key.expressions),
                               tuple(item.name for item in key.args["reference"].this.expressions))
                              for key in table.find_all(exp.ForeignKey)}
                self.assertIn((parent, identity, identity), references)
                parent_table = self.tables[parent]
                unique_keys = {tuple(item.name for item in key.expressions) for key in parent_table.find_all(exp.PrimaryKey)}
                unique_keys.update(tuple(item.name for item in key.this.expressions) for key in parent_table.find_all(exp.UniqueColumnConstraint))
                self.assertIn(identity, unique_keys)
                parent_columns = {column.name: column for column in parent_table.this.expressions if isinstance(column, exp.ColumnDef)}
                for field in fields | {"schema_version", "schema_manifest_sha256"}:
                    self.assertIn(field, parent_columns)
                    self.assertTrue(any(isinstance(c.kind, exp.NotNullColumnConstraint) for c in parent_columns[field].constraints))
                for field in identity:
                    self.assertTrue(any(isinstance(c.kind, exp.NotNullColumnConstraint) for c in columns[field].constraints))
                source_root = ROOT / "business/medical-persistence/src/main/java/com/ruoyi/medical/persistence"
                if name == "formal_evaluation_policy_input":
                    source = (source_root / "EvaluationAcceptancePolicyService.java").read_text()
                    for guard in ['var row=journal.read(a,id)', 'values.put("recordId",id)', 'snapshots.snapshot(values)',
                                  'id.equals(stored.recordId())', 'a.context().tenant_id().equals(stored.tenantId())',
                                  'row.contentSha256().equals(stored.contentSha256())', 'row.classification().equals(content.path("classification").asText())',
                                  'journal.save(a,project,id,kind,resource,key,request,content,auth,classification,time)']:
                        self.assertIn(guard, source)
                    self.assertIn(("version_registry", ("tenant_id", "record_id", "content_sha256"),
                                   ("tenant_id", "version_id", "content_sha256")), references)
                else:
                    source = (source_root / "ProfessionalRuntimeObservations.java").read_text()
                    for guard in ['FROM professional_runtime_session WHERE tenant_id=? AND session_id=? AND run_id=? AND step_id=?',
                                  'AND lease_generation=? AND service_uri=? AND certificate_sha256=? AND trust_revision=?',
                                  'FROM professional_runtime_observation WHERE tenant_id=? AND session_id=? AND kind=? AND observation_sha256=?',
                                  'INSERT INTO professional_runtime_observation (tenant_id,session_id,observation_id,kind,canonical_observation,observation_sha256,created_by,created_at)']:
                        self.assertIn(guard, source)
                    self.assertLess(source.index('FROM professional_runtime_session'), source.index('INSERT INTO professional_runtime_observation'))
                expected -= fields
            with self.subTest(table=name):
                self.assertLessEqual(expected, set(columns))
                for field in expected:
                    self.assertTrue(any(isinstance(constraint.kind, exp.NotNullColumnConstraint) for constraint in columns[field].constraints))

    def test_source_metadata_is_copied_from_original_records(self):
        expected = {
            "retrieval_hit": {("created_by", "created_by"), ("classification", "classification"),
                              ("schema_version", "schema_version"), ("schema_manifest_sha256", "schema_manifest_sha256")},
            "source_lifecycle_decision": {("created_by", "actor_id"), ("created_at", "decided_at")},
            "source_lifecycle_state": {("created_by", "created_by"), ("created_at", "created_at"), ("classification", "classification")},
        }
        for table, assignments in expected.items():
            updates = [statement for statement in self.statements if isinstance(statement, exp.Update)
                       and statement.this.name == table and any(item.this.name == "created_by" for item in statement.expressions)]
            self.assertEqual(len(updates), 1)
            actual = set()
            aliases = {reference.alias_or_name: reference.name for reference in updates[0].find_all(exp.Table)}
            for assignment in updates[0].expressions:
                self.assertIsInstance(assignment, exp.EQ)
                self.assertIsInstance(assignment.expression, exp.Column)
                source_table = aliases[assignment.expression.table] if assignment.expression.table else table
                columns = {column.name for column in self.tables[source_table].this.expressions if isinstance(column, exp.ColumnDef)}
                for statement in self.statements:
                    if isinstance(statement, exp.Alter) and statement.this.name == source_table:
                        columns.update(column.name for column in statement.find_all(exp.ColumnDef))
                self.assertIn(assignment.expression.name, columns, source_table)
                actual.add((assignment.this.name, assignment.expression.name))
            self.assertEqual(actual, assignments)

    def test_completion_rows_keep_full_upload_and_consumer_identity(self):
        expected = {
            "source_completion_checkpoint": {"tenant_id", "event_id", "consumer_id", "upload_id", "operation_id", "input_sha256",
                "scan_command", "scan_receipt", "write_intent", "object_ref", "status", "revision"},
            "source_upload_result": {"tenant_id", "event_id", "consumer_id", "upload_id", "operation_id", "source_id", "source_version",
                "rights_revision", "parse_version", "attempt_id", "original_object_ref", "input_sha256", "result_ref"},
        }
        for table, fields in expected.items():
            columns = {column.name for column in self.tables[table].this.expressions if isinstance(column, exp.ColumnDef)}
            self.assertLessEqual(fields, columns)
        references = {(key.args["reference"].this.this.name, tuple(item.name for item in key.expressions))
                      for key in self.tables["source_upload_result"].find_all(exp.ForeignKey)}
        self.assertIn(("source_completion_checkpoint", ("tenant_id", "event_id", "consumer_id", "upload_id", "operation_id")), references)

    def test_changeset_failures_stop_execution_and_have_stable_identity(self):
        identities = []
        for entry in [*self.changelog.findall("lb:changeSet", NAMESPACE), *self.xml_changes]:
            self.assertEqual(entry.attrib["failOnError"], "true")
            self.assertEqual(entry.attrib["runInTransaction"], "false")
            self.assertEqual(entry.attrib["dbms"], "mysql")
            identities.append((entry.attrib["id"], entry.attrib["author"]))
        self.assertEqual(len(identities), len(set(identities)))
        conditions = self.changelog.find("lb:preConditions", NAMESPACE)
        self.assertEqual(conditions.attrib, {"onFail": "HALT", "onError": "HALT"})
        print(json.dumps({
            "scope": "MIGRATION_STATIC_CHECK",
            "tables": len(self.tables),
            "inputs": {str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest() for path in self.paths},
        }, ensure_ascii=False), flush=True)


if __name__ == "__main__":
    unittest.main(verbosity=2)

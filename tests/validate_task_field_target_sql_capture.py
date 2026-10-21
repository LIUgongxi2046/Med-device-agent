"""Offline SQL-shape validation of freshly emitted 096 JDBC calls. No database connection.

Usage: python tests/validate_task_field_target_sql_capture.py CAPTURE.jsonl --report REPORT.json
SQLGlot parses every core/migration statement and every captured SQL statement. Parameter
values are intentionally absent; this is not data-level FK/CHECK, isolation or MySQL evidence.
"""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import sys
from collections import Counter, defaultdict
import sqlglot
from sqlglot import exp

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tests"))
from test_migration_bundle import MigrationBundleChecks, sqlglot_migration_source

FORMAT = "task-field-target-jdbc-shape-v1"
EXPECTED_096 = {
    "task_field_mapping_revision", "task_field_mapping_head", "task_field_source_read_receipt",
    "task_field_target_intent", "task_field_target_entry", "task_field_target_attempt",
    "task_field_target_registration", "task_field_target_input_capability",
    "task_field_target_input_capability_state", "task_field_target_input_capability_decision",
}
PRODUCERS = {"mapping", "source", "ledger-and-registration", "registration-completion", "capability"}
REQUIRED_EXISTING_INSERTS = {
    "version_registry", "operation_record", "audit_event", "outbox_event", "artifact",
    "dependency_access_manifest", "dependency_access_member", "artifact_content_binding", "lineage_edge",
}
REQUIRED_UPDATES = {"task_field_mapping_head", "task_field_target_input_capability_state", "operation_record", "tenant"}
UNEXERCISED = [
    "MyBatis ArtifactRegistryMapper/SourceReadMapper/VersionResourceMapper/SourceExactMapper and PermissionMapper SQL are mocked at their interface boundary in these fixtures; no emitted SQL claim is made for those mapper methods.",
    "Preparation/controller/session routing and production configuration do not add emitted JDBC coverage unless they call one of the explicitly captured fixture boundaries.",
    "No SQL is executed against MySQL. No data-level FK/CHECK enforcement, parameter value/type coercion, transaction isolation, concurrency, migration application, real grants, S3 credentials or business approvals are established.",
]


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def reconstruct_columns(statements, native_alters=()):
    """Apply parsed column definitions in master changelog order, including ALTER projections."""
    columns = {}
    for tree in statements:
        if tree is None:
            continue
        require(not any(isinstance(node, exp.Command) for node in tree.walk()), f"Opaque migration statement cannot establish schema: {tree}")
        if isinstance(tree, exp.Create) and tree.kind == "TABLE":
            require(isinstance(tree.this, exp.Schema), f"Explicit CREATE schema required: {tree}")
            table = tree.this.this.name
            require(table not in columns, f"Duplicate CREATE TABLE: {table}")
            columns[table] = {c.name: c.copy() for c in tree.this.expressions if isinstance(c, exp.ColumnDef)}
        elif isinstance(tree, exp.Alter):
            table = tree.this.name
            require(table in columns, f"ALTER before table definition: {table}")
            for definition in tree.find_all(exp.ColumnDef):
                columns[table][definition.name] = definition.copy()
            for action in tree.args.get("actions") or []:
                if isinstance(action, exp.Drop) and action.args.get("kind") == "COLUMN":
                    require(action.this.name in columns[table], f"DROP unknown column: {tree}")
                    columns[table].pop(action.this.name)
                elif isinstance(action, exp.AlterColumn):
                    name = action.this.name
                    require(name in columns[table], f"ALTER unknown column: {tree}")
                    require(action.args.get("drop") and set(k for k, v in action.args.items() if v is not None) <= {"this", "drop"},
                            f"Unreviewed ALTER COLUMN shape: {tree}")
                    definition = columns[table][name]
                    definition.set("constraints", [c for c in definition.constraints if not isinstance(c.kind, exp.DefaultColumnConstraint)])
    # The existing JSQLParser adapter records DROP DEFAULT directly instead of an
    # SQLGlot projection. No later migration reinstates these identity defaults.
    for alteration in native_alters:
        for name in alteration.get("drop_defaults", []):
            definition = columns[alteration["table"]][name]
            definition.set("constraints", [c for c in definition.constraints if not isinstance(c.kind, exp.DefaultColumnConstraint)])
    return columns


def load_schema():
    # Reuse the full reviewed bundle parser: core + every master include, including
    # structured/native XML, ALTER projections, and pinned 076 compatibility.
    # The parser helpers inspect source only. They never connect to a database.
    MigrationBundleChecks.setUpClass()
    bundle = MigrationBundleChecks
    # The bundle's 068 wrapper retains a nested Command for DROP CHECK. Reuse the
    # already-reviewed 076 exact-token parser for that same hash-pinned prior
    # statement. Do not skip or accept any opaque action.
    from migration_076_compat import PRIOR_SHA256, OLD_JOBS, _first_statement, parse_workflow_alter, expected_alter
    prior = ROOT / "business/medical-data/src/main/resources/db/migration/068-document-technical-check.sql"
    require(hashlib.sha256(prior.read_bytes()).hexdigest() == PRIOR_SHA256, "068 original bytes changed")
    first, _ = _first_statement(sqlglot_migration_source(prior))
    complete = parse_workflow_alter(first)
    require(complete == expected_alter(OLD_JOBS), "068 exact CHECK replacement differs")
    statements = []
    replaced = 0
    for tree in bundle.statements:
        if tree is not None and any(isinstance(node, exp.Command) for node in tree.walk()):
            require(isinstance(tree, exp.Alter) and tree.this.name == "system_job_run" and
                    len(tree.args.get("actions", [])) == 1 and isinstance(tree.args["actions"][0], exp.Command) and
                    tree.args["actions"][0].this == "DROP" and
                    tree.args["actions"][0].expression == sqlglot.parse_one(first, read="mysql").args["actions"][0].expression,
                    "Unexpected opaque migration action")
            statements.append(complete)
            replaced += 1
        else:
            statements.append(tree)
    require(replaced <= 1, "Duplicate opaque 068 workflow ALTER")
    columns = reconstruct_columns(statements, bundle.native_alters)
    path = ROOT / "business/medical-data/src/main/resources/db/migration/096-task-field-target.sql"
    new_tables = {tree.this.this.name for tree in sqlglot.parse(sqlglot_migration_source(path), read="mysql")
                  if isinstance(tree, exp.Create) and tree.kind == "TABLE"}
    require(new_tables == EXPECTED_096, f"096 CREATE table set differs: {new_tables ^ EXPECTED_096}")
    sources = [{"path": str(path.relative_to(ROOT)), "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
               for path in bundle.paths]
    return columns, sources, len([tree for tree in bundle.statements if tree is not None])


def generated(column):
    return any(isinstance(c.kind, exp.ComputedColumnConstraint) for c in column.constraints)


def mandatory(column):
    kinds = [c.kind for c in column.constraints]
    return any(isinstance(k, exp.NotNullColumnConstraint) and not k.args.get("allow_null") for k in kinds) and not generated(column) and not any(
        isinstance(k, (exp.DefaultColumnConstraint, exp.AutoIncrementColumnConstraint)) for k in kinds)


def validate_record(record, columns):
    require(set(record) == {"format", "producer", "sql", "binding_mode", "parameter_count", "parameter_names", "parameter_types"}, "Unexpected capture fields; values must never be exported")
    require(record["format"] == FORMAT, "Unknown SQL capture format")
    require(record["producer"] in PRODUCERS, "Unknown SQL capture producer")
    sql = record["sql"]
    parsed = [tree for tree in sqlglot.parse(sql, read="mysql") if tree is not None]
    require(len(parsed) == 1, f"Expected exactly one emitted statement: {sql}")
    tree = parsed[0]
    require(isinstance(tree, (exp.Insert, exp.Select, exp.Update)), f"Unreviewed emitted SQL kind: {sql}")
    placeholders = list(tree.find_all(exp.Placeholder))
    require(record["parameter_count"] == len(record["parameter_types"]) == len(placeholders), f"Parameter arity mismatch: {sql}")
    require(all(isinstance(value, str) and (value == "null" or value.startswith(("java.", "["))) for value in record["parameter_types"]), f"Unexpected argument type metadata: {sql}")
    if record["binding_mode"] == "POSITIONAL":
        require(record["parameter_names"] == [] and all(not p.this for p in placeholders), f"Named bind in positional JDBC: {sql}")
    else:
        require(record["binding_mode"] == "NAMED", f"Unknown bind mode: {sql}")
        require(Counter(record["parameter_names"]) == Counter(p.this for p in placeholders) and all(p.this for p in placeholders), f"Named bind shape mismatch: {sql}")

    tables = list(tree.find_all(exp.Table))
    table_names = {table.name for table in tables}
    for table in table_names:
        require(table in columns, f"Unknown table {table}: {sql}")
    aliases = {table.alias_or_name: table.name for table in tables}
    aliases.update({table.name: table.name for table in tables})
    selected_aliases = {alias.alias for alias in tree.find_all(exp.Alias)}
    for column in tree.find_all(exp.Column):
        if column.name == "*":
            if column.table:
                require(column.table in aliases, f"Unknown star qualifier {column.table}: {sql}")
            continue
        if column.table:
            require(column.table in aliases, f"Unknown column qualifier {column.table}: {sql}")
            require(column.name in columns[aliases[column.table]], f"Unknown qualified column {column.sql()}: {sql}")
        else:
            owners = [name for name in table_names if column.name in columns[name]]
            in_alias_scope = column.find_ancestor(exp.Order, exp.Group, exp.Having) is not None
            require(len(owners) == 1 or (column.name in selected_aliases and in_alias_scope), f"Unknown or ambiguous unqualified column {column.name}: {sql}")
    target = None
    if isinstance(tree, exp.Insert):
        require(isinstance(tree.this, exp.Schema), f"Explicit INSERT columns required: {sql}")
        target = tree.this.this.name
        inserted = [column.name for column in tree.this.expressions]
        require(len(inserted) == len(set(inserted)), f"Duplicate INSERT column: {sql}")
        for name in inserted:
            require(name in columns[target], f"Unknown INSERT column {target}.{name}: {sql}")
            require(not generated(columns[target][name]), f"Explicit write to generated column {target}.{name}: {sql}")
        require(isinstance(tree.expression, exp.Values), f"Only reviewed INSERT VALUES shape supported: {sql}")
        for row in tree.expression.expressions:
            require(len(row.expressions) == len(inserted), f"INSERT column/value arity mismatch: {sql}")
        omitted = {name for name, definition in columns[target].items() if mandatory(definition)} - set(inserted)
        require(not omitted, f"Omitted required INSERT columns {target}:{sorted(omitted)}: {sql}")
        if "tenant_id" in columns[target]:
            require("tenant_id" in inserted, f"INSERT missing tenant identity: {sql}")
    elif isinstance(tree, exp.Update):
        target = tree.this.name
        for assignment in tree.expressions:
            require(isinstance(assignment, exp.EQ) and isinstance(assignment.this, exp.Column), f"Unexpected UPDATE assignment shape: {sql}")
            require(not generated(columns[target][assignment.this.name]), f"UPDATE of generated column: {sql}")
    if table_names and isinstance(tree, (exp.Select, exp.Update)):
        if any("tenant_id" in columns[name] for name in table_names):
            where = tree.args.get("where")
            require(where is not None and any(column.name == "tenant_id" for column in where.find_all(exp.Column)), f"Tenant predicate absent from emitted WHERE: {sql}")
    return tree.key.upper(), target, table_names


def validate_capture(path, report_path=None):
    require(path.is_file(), f"Fresh Java SQL capture is required: {path}")
    records = [json.loads(line) for line in path.read_text().splitlines() if line.strip()]
    require(records, "SQL capture is empty")
    columns, sources, ddl_count = load_schema()
    kinds, producers, inserts, updates, selected = Counter(), defaultdict(Counter), set(), set(), set()
    unique_sql = set()
    for record in records:
        kind, target, tables = validate_record(record, columns)
        kinds[kind] += 1; producers[record["producer"]][kind] += 1; unique_sql.add(record["sql"])
        if kind == "INSERT": inserts.add(target)
        if kind == "UPDATE": updates.add(target)
        if kind == "SELECT": selected.update(tables)
    require(set(producers) == PRODUCERS, f"Missing actual producer capture: {PRODUCERS - set(producers)}")
    require(EXPECTED_096 <= inserts, f"096 INSERT paths not executed/captured: {sorted(EXPECTED_096-inserts)}")
    require(EXPECTED_096 <= selected, f"096 SELECT paths not executed/captured: {sorted(EXPECTED_096-selected)}")
    require(REQUIRED_EXISTING_INSERTS <= inserts, f"Existing registration/journal INSERT paths missing: {sorted(REQUIRED_EXISTING_INSERTS-inserts)}")
    require(REQUIRED_UPDATES <= updates, f"Expected CAS/completion UPDATE paths missing: {sorted(REQUIRED_UPDATES-updates)}")
    report = {"format": "task-field-target-sql-validation-v1", "result": "PASS", "database_executed": False,
              "capture_path": str(path), "capture_sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
              "capture_records": len(records), "unique_sql_statements": len(unique_sql), "statement_kinds": dict(sorted(kinds.items())),
              "producers": {name: dict(sorted(counts.items())) for name, counts in sorted(producers.items())},
              "all_096_tables_have_captured_insert_and_select": True,
              "insert_tables": sorted(inserts), "update_tables": sorted(updates), "select_tables": sorted(selected),
              "schema_tables": len(columns), "schema_columns": sum(map(len, columns.values())), "parsed_schema_statements": ddl_count, "schema_sources": sources, "limitations": UNEXERCISED}
    if report_path:
        report_path.parent.mkdir(parents=True, exist_ok=True)
        report_path.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps({key: value for key, value in report.items() if key != "schema_sources"}, indent=2))
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("capture", type=Path)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    validate_capture(args.capture, args.report)

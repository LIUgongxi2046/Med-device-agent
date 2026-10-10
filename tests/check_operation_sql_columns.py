import json
from pathlib import Path
import sys

import sqlglot
from sqlglot import exp
from test_migration_bundle import MigrationBundleChecks


def main():
    MigrationBundleChecks.setUpClass()
    columns = {name: {item.name for item in table.this.expressions if isinstance(item, exp.ColumnDef)}
               for name, table in MigrationBundleChecks.tables.items()}
    for statement in MigrationBundleChecks.statements:
        if isinstance(statement, exp.Alter):
            columns[statement.this.name].update(item.name for item in statement.find_all(exp.ColumnDef))
    queries = json.loads(Path(sys.argv[1]).read_bytes())
    if set(queries) != {"lookup", "operation", "readers"}:
        raise ValueError("OPERATION_SQL_COVERAGE_INVALID")
    for name, query in queries.items():
        statement = sqlglot.parse_one(query, read="mysql")
        if not isinstance(statement, exp.Select):
            raise ValueError("OPERATION_SQL_READ_ONLY_REQUIRED " + name)
        tables = list(statement.find_all(exp.Table))
        if not tables or any(table.name not in columns for table in tables):
            raise ValueError("OPERATION_SQL_TABLE_INVALID " + name)
        available = set().union(*(columns[table.name] for table in tables))
        aliases = {table.alias_or_name: columns[table.name] for table in tables}
        for column in statement.find_all(exp.Column):
            if column.name not in (aliases[column.table] if column.table else available):
                raise ValueError("OPERATION_SQL_COLUMN_INVALID " + name + " " + column.name)
        if name in ("lookup", "operation"):
            projected = {column.name for expression in statement.expressions for column in expression.find_all(exp.Column)}
            if not projected <= {"operation_id", "subject_id", "target_id", "state", "dedup_expires_at", "dedup_policy_revision"}:
                raise ValueError("OPERATION_SQL_SAFE_METADATA_REQUIRED " + name)
    print("OPERATION_SQL_COLUMNS_CHECK queries=3 source=PUBLISHED_DDL scope=READ_ONLY_SAFE_METADATA")


if __name__ == "__main__":
    main()

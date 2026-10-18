import json
from pathlib import Path
import sys

import sqlglot
from sqlglot import exp
from test_migration_bundle import MigrationBundleChecks


ROOT = Path(__file__).resolve().parents[1]


def main():
    queries = json.loads(Path(sys.argv[1]).read_bytes())
    # Reuse the real migration loader, including Liquibase/native ALTER contracts.
    # Parsing formatted SQL as arbitrary text lost comments and omitted XML/ALTER columns.
    MigrationBundleChecks.setUpClass()
    columns = {name: {item.name for item in table.this.expressions if isinstance(item, exp.ColumnDef)}
               for name, table in MigrationBundleChecks.tables.items()}
    for entry in MigrationBundleChecks.native_alters:
        if "definition" in entry:
            columns[entry["table"]].update(item.name for item in sqlglot.parse_one(entry["definition"], read="mysql").find_all(exp.ColumnDef))
    if len(queries) != 15:
        raise ValueError("PERMISSION_QUERY_COVERAGE_INVALID")
    for name, sql in queries.items():
        statement = sqlglot.parse_one(sql, read="mysql")
        if name == "CONFIGURATION":
            if not isinstance(statement, exp.Union) or statement.args.get("distinct"):
                raise ValueError("PERMISSION_CONFIGURATION_UNION_REQUIRED")
            branches=list(statement.find_all(exp.Select))
            if len(branches)!=2 or {t.name for t in statement.find_all(exp.Table)} != {"configuration_head","agent_execution_policy_version"}:
                raise ValueError("PERMISSION_CONFIGURATION_OWNERS_INVALID")
        else:
            if isinstance(statement, exp.Union):
                raise ValueError("PERMISSION_UNEXPECTED_UNION")
            branches=[statement]
        for branch in branches:
            tables = list(branch.find_all(exp.Table))
            if len(tables) != 1:
                raise ValueError("PERMISSION_QUERY_TABLE_INVALID")
            for column in branch.find_all(exp.Column):
                if column.name not in columns[tables[0].name]:
                    raise ValueError("PERMISSION_QUERY_COLUMN_INVALID " + name + " " + column.name)
    print("PERMISSION_SQL_COLUMNS_CHECK queries=15 source=PUBLISHED_DDL")


if __name__ == "__main__":
    main()

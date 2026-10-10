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
    queries = json.loads(Path(sys.argv[1]).read_bytes())
    if len(queries) != 16:
        raise ValueError("ROLE_SQL_COVERAGE_INVALID")
    for name, query in queries.items():
        statement = sqlglot.parse_one(query, read="mysql")
        tables = list(statement.find_all(exp.Table))
        if not tables or any(table.name not in columns for table in tables):
            raise ValueError("ROLE_SQL_TABLE_INVALID " + name)
        available = set().union(*(columns[table.name] for table in tables))
        aliases = {table.alias_or_name: columns[table.name] for table in tables}
        for column in statement.find_all(exp.Column):
            if column.name not in (aliases[column.table] if column.table else available):
                raise ValueError("ROLE_SQL_COLUMN_INVALID " + name + " " + column.name)
        if isinstance(statement, exp.Insert):
            if not isinstance(statement.this, exp.Schema) or any(item.name not in columns[statement.this.this.name] for item in statement.this.expressions):
                raise ValueError("ROLE_SQL_INSERT_INVALID " + name)
    print("ROLE_SQL_COLUMNS_CHECK queries=16 source=PUBLISHED_DDL")


if __name__ == "__main__":
    main()

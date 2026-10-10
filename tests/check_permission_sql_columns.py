import json
from pathlib import Path
import sys

import sqlglot
from sqlglot import exp


ROOT = Path(__file__).resolve().parents[1]


def main():
    queries = json.loads(Path(sys.argv[1]).read_bytes())
    sources = [ROOT / "data-design/core.sql"]
    sources.extend(sorted((ROOT / "business/medical-data/src/main/resources/db/migration").glob("*.sql")))
    columns = {}
    for source in sources:
        for statement in sqlglot.parse(source.read_text(encoding="utf-8"), read="mysql"):
            if isinstance(statement, exp.Create) and statement.kind == "TABLE":
                columns[statement.this.this.name] = {item.name for item in statement.this.expressions if isinstance(item, exp.ColumnDef)}
    if len(queries) != 15:
        raise ValueError("PERMISSION_QUERY_COVERAGE_INVALID")
    for name, sql in queries.items():
        statement = sqlglot.parse_one(sql, read="mysql")
        tables = list(statement.find_all(exp.Table))
        if len(tables) != 1:
            raise ValueError("PERMISSION_QUERY_TABLE_INVALID")
        for column in statement.find_all(exp.Column):
            if column.name not in columns[tables[0].name]:
                raise ValueError("PERMISSION_QUERY_COLUMN_INVALID " + name + " " + column.name)
    print("PERMISSION_SQL_COLUMNS_CHECK queries=15 source=PUBLISHED_DDL")


if __name__ == "__main__":
    main()

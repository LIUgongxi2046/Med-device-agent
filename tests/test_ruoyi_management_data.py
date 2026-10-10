import json
import unittest

from sqlglot import exp
import sqlglot

from test_local_identity_runtime import mysql, settings
import test_migration_bundle as bundle


TABLES = ("sys_oper_log", "sys_dict_type", "sys_dict_data", "sys_logininfor", "sys_job", "sys_job_log",
          "sys_notice", "sys_notice_read", "gen_table", "gen_table_column")


class RuoYiManagementDataChecks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        bundle.MigrationBundleChecks.setUpClass()
        cls.password = settings()["root_password"]

    def test_actual_mysql_preserves_complete_original_framework_columns(self):
        for table in TABLES:
            with self.subTest(table=table):
                expected = [column for column in bundle.MigrationBundleChecks.tables[table].this.expressions
                            if isinstance(column, exp.ColumnDef)]
                result = mysql("SELECT JSON_OBJECT('name',COLUMN_NAME,'type',COLUMN_TYPE,'nullable',IS_NULLABLE,"
                    "'default',COLUMN_DEFAULT,'extra',EXTRA,'collation',COLLATION_NAME) FROM information_schema.columns "
                    "WHERE TABLE_SCHEMA='medical_local' AND TABLE_NAME='" + table + "' ORDER BY ORDINAL_POSITION", self.password)
                actual = [json.loads(line) for line in result.stdout.splitlines()]
                self.assertEqual([column["name"] for column in actual], [column.name for column in expected])
                primary = {column.name for key in bundle.MigrationBundleChecks.tables[table].find_all(exp.PrimaryKey) for column in key.expressions}
                for column, original in zip(actual, expected, strict=True):
                    kind = original.kind.copy()
                    if kind.this in (exp.DataType.Type.INT, exp.DataType.Type.BIGINT):
                        kind.set("expressions", [])
                    self.assertEqual(sqlglot.parse_one(column["type"], into=exp.DataType, dialect="mysql"), kind)
                    constraints = [constraint.kind for constraint in original.constraints]
                    self.assertEqual(column["nullable"] == "NO", original.name in primary or
                        any(isinstance(constraint, exp.NotNullColumnConstraint) and not constraint.args.get("allow_null")
                            for constraint in constraints))
                    defaults = [constraint.this for constraint in constraints if isinstance(constraint, exp.DefaultColumnConstraint)]
                    self.assertLessEqual(len(defaults), 1)
                    value = None if not defaults or isinstance(defaults[0], exp.Null) else defaults[0].this
                    self.assertEqual(column["default"], value)
                    self.assertEqual(column["extra"], "auto_increment" if any(
                        isinstance(constraint, exp.AutoIncrementColumnConstraint) for constraint in constraints) else "")
                    if kind.this in (exp.DataType.Type.VARCHAR, exp.DataType.Type.CHAR):
                        self.assertEqual(column["collation"], "utf8mb4_0900_bin")

    def test_actual_mysql_preserves_primary_unique_and_query_indexes(self):
        for table in TABLES:
            with self.subTest(table=table):
                expected = bundle.framework_index_signature(bundle.MigrationBundleChecks.tables[table].this)
                result = mysql("SELECT JSON_OBJECT('name',INDEX_NAME,'unique',NON_UNIQUE=0,'column',COLUMN_NAME) "
                    "FROM information_schema.statistics WHERE TABLE_SCHEMA='medical_local' AND TABLE_NAME='" + table +
                    "' ORDER BY INDEX_NAME,SEQ_IN_INDEX", self.password)
                indexes = {}
                for row in (json.loads(line) for line in result.stdout.splitlines()):
                    identity = (row["name"], bool(row["unique"]))
                    indexes.setdefault(identity, []).append(row["column"])
                actual = [(name, unique, tuple(columns)) for (name, unique), columns in indexes.items()]
                self.assertCountEqual(actual, expected)


if __name__ == "__main__":
    unittest.main(verbosity=2)

"""Validator self-tests only; synthetic statements are never accepted as runtime evidence."""
import unittest
import sqlglot
from sqlglot import exp
from validate_task_field_target_sql_capture import FORMAT, generated, mandatory, reconstruct_columns, validate_record


class TaskFieldSqlShapeValidatorTests(unittest.TestCase):
    def setUp(self):
        self.columns = reconstruct_columns(sqlglot.parse("""
            CREATE TABLE sample (tenant_id CHAR(36) NOT NULL, id CHAR(36) NOT NULL,
                optional_value TEXT NULL, revision INT NOT NULL DEFAULT 1,
                derived CHAR(36) GENERATED ALWAYS AS (id) STORED);
            CREATE TABLE other_sample (tenant_id CHAR(36) NOT NULL, id CHAR(36) NOT NULL);
            ALTER TABLE sample ADD COLUMN added_by_migration TEXT NULL;
        """, read="mysql"))

    def record(self, sql, count=0, names=None):
        return {"format": FORMAT, "producer": "mapping", "sql": sql,
                "binding_mode": "NAMED" if names is not None else "POSITIONAL",
                "parameter_count": count, "parameter_names": names or [],
                "parameter_types": ["java.lang.String"] * count}

    def test_accepts_actual_shape_and_alter_added_column(self):
        self.assertEqual(("SELECT", None, {"sample"}), validate_record(self.record(
            "SELECT added_by_migration FROM sample WHERE tenant_id=? AND id=?", 2), self.columns))

    def test_nullable_and_default_and_generated_columns_can_be_omitted(self):
        validate_record(self.record("INSERT INTO sample(tenant_id,id) VALUES (?,?)", 2), self.columns)
        self.assertFalse(mandatory(self.columns["sample"]["optional_value"]))
        self.assertTrue(generated(self.columns["sample"]["derived"]))

    def test_rejects_missing_required_column(self):
        with self.assertRaisesRegex(AssertionError, "Omitted required"):
            validate_record(self.record("INSERT INTO sample(tenant_id) VALUES (?)", 1), self.columns)

    def test_rejects_generated_column_write(self):
        for sql, count in [("INSERT INTO sample(tenant_id,id,derived) VALUES (?,?,?)", 3),
                           ("UPDATE sample SET derived=? WHERE tenant_id=?", 2)]:
            with self.subTest(sql=sql), self.assertRaisesRegex(AssertionError, "generated column"):
                validate_record(self.record(sql, count), self.columns)

    def test_rejects_unknown_table_and_column(self):
        for sql in ["SELECT * FROM nonexistent WHERE tenant_id=?", "SELECT absent FROM sample WHERE tenant_id=?"]:
            with self.subTest(sql=sql), self.assertRaisesRegex(AssertionError, "Unknown"):
                validate_record(self.record(sql, 1), self.columns)

    def test_rejects_ambiguous_join_column(self):
        sql = "SELECT id FROM sample s JOIN other_sample o ON s.id=o.id WHERE s.tenant_id=?"
        with self.assertRaisesRegex(AssertionError, "ambiguous"):
            validate_record(self.record(sql, 1), self.columns)

    def test_allows_qualified_join_and_order_alias(self):
        validate_record(self.record("SELECT s.id AS chosen FROM sample s JOIN other_sample o ON s.id=o.id "
                                    "WHERE s.tenant_id=? ORDER BY chosen", 1), self.columns)

    def test_rejects_parameter_count_and_insert_arity(self):
        for sql, count, message in [
            ("SELECT * FROM sample WHERE tenant_id=?", 0, "Parameter arity"),
            ("INSERT INTO sample(tenant_id,id) VALUES (?)", 1, "column/value arity")]:
            with self.subTest(sql=sql), self.assertRaisesRegex(AssertionError, message):
                validate_record(self.record(sql, count), self.columns)

    def test_requires_exact_named_bind_shape(self):
        sql = "SELECT * FROM sample WHERE tenant_id=:tenant AND id=:id"
        validate_record(self.record(sql, 2, ["tenant", "id"]), self.columns)
        with self.assertRaisesRegex(AssertionError, "Named bind shape"):
            validate_record(self.record(sql, 2, ["tenant", "other"]), self.columns)

    def test_rejects_unscoped_select_and_update(self):
        for sql in ["SELECT * FROM sample WHERE id=?", "UPDATE sample SET id=?"]:
            with self.subTest(sql=sql), self.assertRaisesRegex(AssertionError, "Tenant predicate"):
                validate_record(self.record(sql, 1), self.columns)

    def test_rejects_parameter_values_or_unreviewed_sql(self):
        record = self.record("SELECT 1")
        record["parameter_values"] = []
        with self.assertRaisesRegex(AssertionError, "Unexpected capture fields"):
            validate_record(record, self.columns)
        for sql in ["DELETE FROM sample WHERE tenant_id=?", "SELECT 1; SELECT 2"]:
            with self.subTest(sql=sql), self.assertRaises(AssertionError):
                validate_record(self.record(sql, 1), self.columns)

    def test_rejects_opaque_schema_commands(self):
        with self.assertRaisesRegex(AssertionError, "Opaque migration"):
            reconstruct_columns([exp.Command(this="ALTER", expression="TABLE sample unknown")])


if __name__ == "__main__":
    unittest.main()

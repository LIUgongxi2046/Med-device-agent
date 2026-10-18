import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.alter.AlterExpression;
import net.sf.jsqlparser.statement.alter.AlterOperation;
import net.sf.jsqlparser.statement.create.table.ColumnDefinition;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.update.Update;

class InspectAlterMigrations {
    public static void main(String[] args) throws Exception {
        var result = new ArrayList<Object>();
        for (var statement : CCJSqlParserUtil.parseStatements(Files.readString(Path.of(args[0])))) {
            if (statement instanceof CreateTable created) {
                result.add(java.util.Map.of("create", created.toString()));
                continue;
            }
            if (statement instanceof Insert || statement instanceof Update) {
                result.add(java.util.Map.of("data_fill", statement.toString()));
                continue;
            }
            if (!(statement instanceof Alter alter)) throw new IllegalStateException("ALTER_MIGRATION_REQUIRED");
            for (var change : alter.getAlterExpressions()) {
                var entry = new LinkedHashMap<String, Object>();
                entry.put("table", alter.getTable().getName());
                entry.put("operation", change.getOperation().name());
                // 成熟Parser保持原字段与约束节点，交给SQLGlot执行完整约束检查。
                if (change.getColDataTypeList() != null && !change.getColDataTypeList().isEmpty()) {
                    // 字段位置属于ALTER；使用成熟AST保留完整定义与AFTER关系。
                    var projection = new Alter(); projection.setTable(alter.getTable());
                    var actions = new ArrayList<AlterExpression>();
                    for (var column : change.getColDataTypeList()) {
                        var action = new AlterExpression(); action.setOperation(AlterOperation.ADD);
                        action.hasColumn(true); action.addColDataType(column); actions.add(action);
                    }
                    projection.setAlterExpressions(actions);
                    entry.put("definition", projection.toString());
                } else if (change.getIndex() != null || change.getUkColumns() != null || change.getPkColumns() != null || change.getFkColumns() != null) {
                    var constraint = new Alter();
                    constraint.setTable(alter.getTable());
                    constraint.setAlterExpressions(List.of(change));
                    entry.put("definition", constraint.toString());
                } else if (change.getOperation() == AlterOperation.DROP && change.getConstraintName() != null) {
                    // Preserve an explicit constraint-drop AST for replacement-check verification.
                    var constraint = new Alter(); constraint.setTable(alter.getTable());
                    constraint.setAlterExpressions(List.of(change));
                    entry.put("definition", constraint.toString());
                    entry.put("drop_constraint", change.getConstraintName());
                } else if (change.getColumnDropDefaultList() != null && !change.getColumnDropDefaultList().isEmpty()) {
                    entry.put("drop_defaults", change.getColumnDropDefaultList().stream()
                        .map(column -> column.getColumnName()).toList());
                } else throw new IllegalStateException("ALTER_DEFINITION_REQUIRED");
                result.add(entry);
            }
        }
        System.out.println(JsonMapper.builder().build().writeValueAsString(result));
    }
}

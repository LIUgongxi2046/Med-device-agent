import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.create.table.ColumnDefinition;
import net.sf.jsqlparser.statement.create.table.CreateTable;

class InspectAlterMigrations {
    public static void main(String[] args) throws Exception {
        var result = new ArrayList<Object>();
        for (var statement : CCJSqlParserUtil.parseStatements(Files.readString(Path.of(args[0])))) {
            if (statement instanceof CreateTable created) {
                result.add(java.util.Map.of("create", created.toString()));
                continue;
            }
            if (!(statement instanceof Alter alter)) throw new IllegalStateException("ALTER_MIGRATION_REQUIRED");
            for (var change : alter.getAlterExpressions()) {
                var entry = new LinkedHashMap<String, Object>();
                entry.put("table", alter.getTable().getName());
                entry.put("operation", change.getOperation().name());
                // 成熟Parser保持原字段与约束节点，交给SQLGlot执行完整约束检查。
                var definition = new CreateTable(); definition.setTable(alter.getTable());
                if (change.getColDataTypeList() != null && !change.getColDataTypeList().isEmpty()) {
                    var columns = new ArrayList<ColumnDefinition>();
                    for (var column : change.getColDataTypeList()) columns.add(column);
                    definition.setColumnDefinitions(columns);
                    entry.put("definition", definition.toString());
                } else if (change.getIndex() != null || change.getUkColumns() != null || change.getPkColumns() != null || change.getFkColumns() != null) {
                    var constraint = new Alter();
                    constraint.setTable(alter.getTable());
                    constraint.setAlterExpressions(List.of(change));
                    entry.put("definition", constraint.toString());
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

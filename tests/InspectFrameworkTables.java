import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.create.table.CreateTable;

class InspectFrameworkTables {
    public static void main(String[] args) throws Exception {
        var mapper = JsonMapper.builder().build();
        var result = new ArrayList<Object>();
        // SQLParse选择原CREATE语句，JSQLParser负责完整字段和索引语法。
        for (var source : mapper.readValue(System.in, String[].class)) {
            if (!(CCJSqlParserUtil.parse(source) instanceof CreateTable table)) {
                throw new IllegalStateException("FRAMEWORK_TABLE_DEFINITION_REQUIRED");
            }
            var indexes = new ArrayList<Object>();
            if (table.getIndexes() != null) for (var index : table.getIndexes()) {
                var descriptor = new LinkedHashMap<String, Object>();
                String kind = index.getType().toUpperCase(Locale.ROOT);
                descriptor.put("name", "PRIMARY KEY".equals(kind) ? "PRIMARY" : index.getName() == null
                    ? index.getColumnsNames().get(0) : index.getName());
                descriptor.put("unique", List.of("PRIMARY KEY", "UNIQUE", "UNIQUE KEY").contains(kind));
                descriptor.put("columns", index.getColumnsNames());
                indexes.add(descriptor);
            }
            table.setIndexes(null);
            result.add(java.util.Map.of("definition", table.toString(), "indexes", indexes));
        }
        if (result.isEmpty()) throw new IllegalStateException("FRAMEWORK_TABLE_DEFINITIONS_REQUIRED");
        System.out.println(mapper.writeValueAsString(result));
    }
}

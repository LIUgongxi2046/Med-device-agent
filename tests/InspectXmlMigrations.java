import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Executors;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.create.table.ColumnDefinition;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.update.Update;

class InspectXmlMigrations {
    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2 || args.length == 2 && !args[1].equals("LIQUIBASE"))
            throw new IllegalArgumentException("必须提供迁移XML路径和有效解析方式");
        if (args.length == 2) { inspectStructured(Path.of(args[0]).toRealPath()); return; }
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        org.w3c.dom.Document document;
        try (var stream = Files.newInputStream(Path.of(args[0]))) {
            document = factory.newDocumentBuilder().parse(stream);
        }
        var result = new ArrayList<Object>();
        var elements = document.getElementsByTagNameNS("http://www.liquibase.org/xml/ns/dbchangelog", "sql");
        for (int index = 0; index < elements.getLength(); index++) {
            var element = elements.item(index);
            String changeSet = element.getParentNode().getAttributes().getNamedItem("id").getNodeValue();
            for (var statement : parse(element.getTextContent())) {
                var entry = new LinkedHashMap<String,Object>(); entry.put("change_set", changeSet);
                if (statement instanceof CreateTable created) {
                    entry.put("table", created.getTable().getName()); entry.put("create", created.toString());
                } else if (statement instanceof Alter alter) {
                    entry.put("table", alter.getTable().getName()); entry.put("alter", alter.toString());
                    entry.put("changes", projections(alter));
                } else throw new IllegalStateException("迁移SQL必须为原生CREATE TABLE或ALTER TABLE");
                result.add(entry);
            }
        }
        if (result.isEmpty()) throw new IllegalStateException("迁移XML没有SQL定义");
        System.out.println(JsonMapper.builder().build().writeValueAsString(result));
    }

    private static net.sf.jsqlparser.statement.Statements parse(String sql) throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        try {
            return CCJSqlParserUtil.parseStatements(CCJSqlParserUtil.newParser(sql), executor);
        } finally {
            executor.shutdownNow();
        }
    }

    private static void inspectStructured(Path path) throws Exception {
        var result = new ArrayList<Object>();
        try (var resources = new liquibase.resource.DirectoryResourceAccessor(path.getParent());
             var migration = new liquibase.Liquibase(path.getFileName().toString(), resources, new liquibase.database.core.MySQLDatabase())) {
            for (var changeSet : migration.getDatabaseChangeLog().getChangeSets()) {
                for (var operation : changeSet.getChanges()) {
                    if (operation.validate(migration.getDatabase()).hasErrors()) throw new IllegalStateException("迁移字段无效");
                    var generated = liquibase.sqlgenerator.SqlGeneratorFactory.getInstance().generateSql(operation, migration.getDatabase());
                    if (generated.length == 0) throw new IllegalStateException("迁移必须产生完整SQL");
                    if (operation instanceof liquibase.change.core.DropForeignKeyConstraintChange drop) {
                        for (var sql : generated) {
                            var entry = new LinkedHashMap<String,Object>(); entry.put("change_set", changeSet.getId());
                            entry.put("table", drop.getBaseTableName());
                            entry.put("changes", List.of(java.util.Map.of("operation", "DROP", "projection", sql.toSql(), "definition", sql.toSql())));
                            result.add(entry);
                        }
                        continue;
                    }
                    for (var sql : generated) for (var statement : parse(sql.toSql())) {
                        var entry = new LinkedHashMap<String,Object>(); entry.put("change_set", changeSet.getId());
                        if (statement instanceof CreateTable table) {
                            entry.put("table", table.getTable().getName()); entry.put("create", table.toString());
                        } else if (statement instanceof Alter alter) {
                            entry.put("table", alter.getTable().getName());
                            entry.put("changes", projections(alter));
                        } else if (statement instanceof Update update) {
                            entry.put("table", update.getTable().getName()); entry.put("data_fill", update.toString());
                        } else throw new IllegalStateException("迁移SQL必须为CREATE TABLE、ALTER TABLE或明确数据填充");
                        result.add(entry);
                    }
                }
            }
        }
        System.out.println(JsonMapper.builder().build().writeValueAsString(result));
    }

    private static List<Object> projections(Alter alter) {
        var result = new ArrayList<Object>();
        for (var change : alter.getAlterExpressions()) {
            var item = new LinkedHashMap<String,Object>(); item.put("operation", change.getOperation().name());
            item.put("definition", change.toString());
            if (change.getColDataTypeList() != null && !change.getColDataTypeList().isEmpty()) {
                var projection = new CreateTable(); projection.setTable(alter.getTable());
                projection.setColumnDefinitions(new ArrayList<ColumnDefinition>(change.getColDataTypeList()));
                item.put("projection", projection.toString());
            } else {
                var projection = new Alter(); projection.setTable(alter.getTable()); projection.setAlterExpressions(List.of(change));
                item.put("projection", projection.toString());
            }
            result.add(item);
        }
        return result;
    }
}

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.insert.Insert;

class SeedAcceptanceFramework {
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) throw new IllegalArgumentException("ACCEPTANCE_REFERENCE_REQUIRED");
        var selected = new LinkedHashMap<String,List<Insert>>();
        for (String name : List.of("sys_dept", "sys_post", "sys_config", "sys_notice")) selected.put(name, new ArrayList<>());
        for (var statement : CCJSqlParserUtil.parseStatements(Files.readString(Path.of(arguments[0])), parser -> parser.withBackslashEscapeCharacter(true))) {
            if (statement instanceof Insert insert && selected.containsKey(insert.getTable().getName())) selected.get(insert.getTable().getName()).add(insert);
        }
        if (selected.values().stream().anyMatch(List::isEmpty)) throw new IllegalStateException("ACCEPTANCE_REFERENCE_INCOMPLETE");
        try (var connection = DriverManager.getConnection("jdbc:mysql://127.0.0.1:13306/medical_local?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC", "root", Objects.requireNonNull(System.getenv("MEDICAL_ROOT_PASSWORD")))) {
            connection.setAutoCommit(false);
            for (var entry : selected.entrySet()) {
                long count;
                try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT COUNT(*) FROM " + entry.getKey())) { rows.next(); count = rows.getLong(1); }
                if (count != 0) { System.out.println(entry.getKey() + " EXISTING " + count); continue; }
                try (var command = connection.createStatement()) {
                    for (var insert : entry.getValue()) if (command.executeUpdate(insert.toString()) != 1) throw new IllegalStateException("ACCEPTANCE_INSERT_FAILED");
                    switch (entry.getKey()) {
                        case "sys_dept" -> command.executeUpdate("UPDATE sys_dept SET dept_name=CONCAT('验收测试 · ',dept_name),leader='验收测试负责人',phone=NULL,email=NULL,create_time=UTC_TIMESTAMP(),create_by='acceptance'");
                        case "sys_post" -> command.executeUpdate("UPDATE sys_post SET post_name=CONCAT('验收测试 · ',post_name),remark='SIMULATION 本地验收测试数据',create_time=UTC_TIMESTAMP(),create_by='acceptance'");
                        case "sys_config" -> {
                            command.executeUpdate("UPDATE sys_config SET remark=CONCAT('SIMULATION 本地验收 · ',COALESCE(remark,'')),create_time=UTC_TIMESTAMP(),create_by='acceptance'");
                            try (var password = connection.prepareStatement("UPDATE sys_config SET config_value=? WHERE config_key='sys.user.initPassword'")) {
                                password.setString(1, Objects.requireNonNull(System.getenv("MEDICAL_ACCEPTANCE_INITIAL_PASSWORD"))); password.executeUpdate();
                            }
                        }
                        case "sys_notice" -> command.executeUpdate("UPDATE sys_notice SET notice_title=CONCAT('验收测试 · ',notice_title),notice_content=CONVERT('SIMULATION 本地功能验收记录，不用于客户或医疗结论。' USING utf8mb4),remark='本地验收数据',create_time=UTC_TIMESTAMP(),create_by='acceptance'");
                        default -> throw new IllegalStateException("ACCEPTANCE_TABLE_NOT_DECLARED");
                    }
                }
                System.out.println(entry.getKey() + " CREATED " + entry.getValue().size());
            }
            connection.commit();
        }
    }
}

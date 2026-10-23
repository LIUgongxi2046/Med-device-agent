import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.insert.Insert;

class InitializeLocalFramework {
    public static void main(String[] arguments) throws Exception {
        if (arguments.length < 1 || arguments.length > 2) throw new IllegalArgumentException("FRAMEWORK_SOURCE_REQUIRED");
        var selected = new ArrayList<Insert>();
        for (var statement : CCJSqlParserUtil.parseStatements(Files.readString(Path.of(arguments[0])),
                parser -> parser.withBackslashEscapeCharacter(true))) {
            if (!(statement instanceof Insert insert)) continue;
            String table = insert.getTable().getName();
            if (Set.of("sys_menu", "sys_dict_type", "sys_dict_data").contains(table)) selected.add(insert);
            if (table.equals("sys_role")) {
                var id = insert.getValues().getExpressions().get(0);
                if (id instanceof StringValue text && text.getValue().equals("1")
                        || id instanceof LongValue number && number.getValue() == 1L) selected.add(insert);
            }
        }
        if (selected.size() < 100) throw new IllegalStateException("FRAMEWORK_REFERENCE_DATA_INCOMPLETE");
        if (arguments.length == 1) {
            System.out.println("FRAMEWORK_REFERENCE_STATEMENTS " + selected.size());
            return;
        }
        if (!arguments[1].equals("--apply")) throw new IllegalArgumentException("FRAMEWORK_ACTION_INVALID");
        try (var connection = DriverManager.getConnection(
                "jdbc:mysql://127.0.0.1:13306/medical_local?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC",
                "root", Objects.requireNonNull(System.getenv("MEDICAL_ROOT_PASSWORD")))) {
            connection.setAutoCommit(false);
            long owner;
            try (var query = connection.prepareStatement("SELECT a.ruoyi_user_id FROM platform_local_account a JOIN platform_local_bootstrap b ON b.created_by=a.user_id WHERE b.coordination_key='LOCAL' AND a.username=? AND a.platform_role='PLATFORM_ADMIN' AND a.status='ACTIVE' FOR UPDATE")) {
                query.setString(1, Objects.requireNonNull(System.getenv("MEDICAL_OWNER_USERNAME")));
                try (var rows = query.executeQuery()) {
                    if (!rows.next()) throw new IllegalStateException("FRAMEWORK_OWNER_REQUIRED");
                    owner = rows.getLong(1);
                    if (rows.next()) throw new IllegalStateException("FRAMEWORK_OWNER_NOT_UNIQUE");
                }
            }
            for (String table : List.of("sys_menu", "sys_role", "sys_dict_type", "sys_dict_data")) {
                try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    rows.next();
                    if (rows.getLong(1) != 0) throw new IllegalStateException("FRAMEWORK_REFERENCE_ALREADY_PRESENT " + table);
                }
            }
            try (var command = connection.createStatement()) {
                for (var insert : selected) {
                    if (command.executeUpdate(insert.toString()) != 1) throw new IllegalStateException("FRAMEWORK_REFERENCE_INSERT_FAILED");
                }
            }
            try (var command = connection.prepareStatement("INSERT INTO sys_user_role (user_id,role_id) VALUES (?,1)")) {
                command.setLong(1, owner);
                if (command.executeUpdate() != 1) throw new IllegalStateException("FRAMEWORK_OWNER_ROLE_FAILED");
            }
            long expectedMenus = selected.stream().filter(insert -> insert.getTable().getName().equals("sys_menu")).count();
            try (var command = connection.createStatement()) {
                if (command.executeUpdate("INSERT INTO sys_role_menu (role_id,menu_id) SELECT 1,menu_id FROM sys_menu") != expectedMenus) {
                    throw new IllegalStateException("FRAMEWORK_ROLE_MENU_INCOMPLETE");
                }
            }
            connection.commit();
            System.out.println("FRAMEWORK_REFERENCE_INITIALIZED " + selected.size() + " OWNER " + owner);
        }
    }
}

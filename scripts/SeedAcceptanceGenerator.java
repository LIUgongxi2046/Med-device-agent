import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.Objects;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.create.table.CreateTable;

class SeedAcceptanceGenerator {
    public static void main(String[] args) throws Exception {
        var statements = CCJSqlParserUtil.parseStatements(Files.readString(Path.of(args[0])), parser -> parser.withBackslashEscapeCharacter(true));
        try (var connection = DriverManager.getConnection("jdbc:mysql://127.0.0.1:13306/framework_dev_acceptance?useSSL=false&allowPublicKeyRetrieval=true", "acceptance_generator", Objects.requireNonNull(System.getenv("MEDICAL_GENERATOR_PASSWORD")))) {
            for (var statement : statements) {
                if (statement instanceof CreateTable create && List.of("gen_table", "gen_table_column", "sys_dept").contains(create.getTable().getName())) {
                    create.setIfNotExists(true);
                    try (var command = connection.createStatement()) { command.execute(create.toString()); }
                }
            }
            try (var command = connection.prepareStatement("INSERT INTO sys_dept(dept_id,parent_id,ancestors,dept_name,order_num,status,del_flag,create_by,create_time) SELECT 100,0,'0','验收测试 · 独立开发组织',1,'0','0','acceptance',UTC_TIMESTAMP() WHERE NOT EXISTS(SELECT 1 FROM sys_dept WHERE dept_id=100)")) { command.executeUpdate(); }
            System.out.println("ACCEPTANCE_GENERATOR_DATABASE_READY");
        }
    }
}

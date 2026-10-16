package com.ruoyi.medical.persistence;

import com.ruoyi.medical.persistence.mapper.RetrievalMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class RetrievalSchemaTest {
    @Test
    void publishedRetrievalSchemaAndMapperStatementsRemainLoadable() throws Exception {
        var schema = new RetrievalSchema(new PermissionSchema());
        assertEquals(64, schema.manifestSha256().length());
        var configuration = new MedicalPersistenceConfiguration()
                .medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
        for (var method : RetrievalMapper.class.getDeclaredMethods()) {
            String name = RetrievalMapper.class.getName() + "." + method.getName();
            var statement = configuration.getMappedStatement(name);
            String sql = statement.getBoundSql(Map.of()).getSql();
            assertNotNull(net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(sql));
            if (method.getName().equals("candidates")) {
                assertTrue(sql.contains("?"));
                assertTrue(statement.getBoundSql(Map.of("environment", "LOCAL", "region", "CN"))
                        .getParameterMappings().stream().map(org.apache.ibatis.mapping.ParameterMapping::getProperty)
                        .collect(java.util.stream.Collectors.toSet()).containsAll(
                                java.util.Set.of("environment", "region", "queryText", "projectId", "userId", "identityMode")));
            }
        }
        for (String name : java.util.List.of("candidate", "query", "hit")) {
            var mapping = configuration.getResultMap(RetrievalMapper.class.getName() + "." + name);
            Class<?>[] parameters = mapping.getConstructorResultMappings().stream()
                    .map(org.apache.ibatis.mapping.ResultMapping::getJavaType).toArray(Class<?>[]::new);
            Class<?> type = switch (name) {
                case "candidate" -> RetrievalProtocol.Candidate.class;
                case "query" -> RetrievalProtocol.QueryRow.class;
                case "hit" -> RetrievalProtocol.Hit.class;
                default -> throw new IllegalStateException(name);
            };
            assertNotNull(type.getDeclaredConstructor(parameters));
        }
    }

    @Test
    void everyRetrievalStatementRequiresTheCurrentSubject() {
        for (var method : RetrievalMapper.class.getDeclaredMethods()) {
            assertThrows(TenantAccessException.class, () -> TenantScope.requireSubjectParameter(
                    RetrievalMapper.class.getName() + "." + method.getName(), Map.of()));
        }
    }
}

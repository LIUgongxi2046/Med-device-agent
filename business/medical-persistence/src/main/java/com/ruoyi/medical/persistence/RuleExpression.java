package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.expression.spel.SpelCompilerMode;
import org.springframework.expression.spel.SpelNode;
import org.springframework.expression.spel.SpelParserConfiguration;
import org.springframework.expression.spel.ast.*;
import org.springframework.expression.spel.standard.SpelExpression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;

@Component
public final class RuleExpression {
    static final String ENGINE = "spring-spel-6.2.19-boolean-v1";
    private static final Set<Class<?>> OPERATORS = Set.of(OpAnd.class, OpOr.class, OperatorNot.class,
            OpEQ.class, OpNE.class, OpLT.class, OpLE.class, OpGT.class, OpGE.class);
    private final SpelExpressionParser parser;
    private final JsonMapper values = JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS,
            DeserializationFeature.USE_BIG_INTEGER_FOR_INTS).build();

    public RuleExpression() {
        if (!"6.2.19".equals(SpelExpressionParser.class.getPackage().getImplementationVersion()))
            throw new IllegalStateException("RULE_ENGINE_VERSION_UNSUPPORTED");
        parser = new SpelExpressionParser(new SpelParserConfiguration(SpelCompilerMode.OFF,
                RuleExpression.class.getClassLoader(), false, false, 0, 10000, 512));
    }

    Compiled compile(TemplateApplicabilityProtocol.Expression input) {
        if (!ENGINE.equals(input.engine_revision()) || !"1.0.0".equals(input.schema_version())) unsupported();
        ProjectSchema.text(input.expression(), 10000);
        var declaredInputs = new HashSet<>(input.input_keys());
        if (declaredInputs.size() != input.input_keys().size()) invalid();
        var declaredConstants = new HashSet<String>();
        var constantValues = new HashMap<String,com.fasterxml.jackson.databind.JsonNode>();
        for (var constant : input.constants()) {
            if (!declaredConstants.add(constant.key())) invalid();
            constantValues.put(constant.key(), constant.value());
        }
        var expression = (SpelExpression) parser.parseExpression(input.expression());
        var inputs = new HashSet<String>(); var constants = new HashSet<String>();
        var nodes = new ArrayDeque<Visit>(); nodes.add(new Visit(expression.getAST(), 1)); int visited = 0;
        // 原Spring解析AST；完整节点检查只限制本业务允许的能力。
        while (!nodes.isEmpty()) {
            var visit = nodes.removeFirst(); var node = visit.node();
            if (++visited > 512 || visit.depth() > 64) throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
            if (node instanceof CompoundExpression) {
                if (node.getChildCount() != 2 || !(node.getChild(0) instanceof VariableReference)
                        || !(node.getChild(1) instanceof Indexer index) || index.getChildCount() != 1
                        || !(index.getChild(0) instanceof StringLiteral key)) invalid();
                String variable = node.getChild(0).toStringAST();
                String name = (String) ((StringLiteral) node.getChild(1).getChild(0)).getLiteralValue().getValue();
                ProjectSchema.text(name, 128);
                if (variable.equals("#attributes")) inputs.add(name);
                else if (variable.equals("#constants")) constants.add(name);
                else invalid();
                continue;
            }
            if (!OPERATORS.contains(node.getClass()) && !(node instanceof BooleanLiteral)
                    && !(node instanceof StringLiteral) && !(node instanceof NullLiteral)) invalid();
            // 核查完整AST中的每个布尔运算，即使它位于比较操作数内部。
            if (node instanceof OpAnd || node instanceof OpOr || node instanceof OperatorNot)
                for (int index = 0; index < node.getChildCount(); index++) requireBoolean(node.getChild(index), constantValues);
            for (int index = 0; index < node.getChildCount(); index++) nodes.addLast(new Visit(node.getChild(index), visit.depth() + 1));
        }
        if (!inputs.equals(declaredInputs) || !constants.equals(declaredConstants)) invalid();
        requireBoolean(expression.getAST(), constantValues);
        return new Compiled(input, expression);
    }

    private static void requireBoolean(SpelNode node, java.util.Map<String,com.fasterxml.jackson.databind.JsonNode> constants) {
        if (!OPERATORS.contains(node.getClass()) && !(node instanceof BooleanLiteral) && !(node instanceof CompoundExpression)) invalid();
        if (node instanceof CompoundExpression && node.getChildCount() == 2
                && node.getChild(0) instanceof VariableReference variable && variable.toStringAST().equals("#constants")
                && node.getChild(1) instanceof Indexer index && index.getChildCount() == 1 && index.getChild(0) instanceof StringLiteral key) {
            var value = constants.get((String) key.getLiteralValue().getValue());
            if (value == null || !value.isBoolean()) invalid();
        }
    }

    TemplateApplicabilityProtocol.Result evaluate(Compiled compiled, List<ProjectProtocol.Attribute> attributes) {
        var available = new HashMap<String,ProjectProtocol.Attribute>();
        for (var attribute : attributes) if (available.putIfAbsent(attribute.key(), attribute) != null) invalid();
        var unknown = new ArrayList<String>(); var actual = new HashMap<String,Object>();
        for (String key : compiled.input().input_keys()) {
            var attribute = available.get(key);
            if (attribute == null || attribute.status().equals("UNKNOWN")) unknown.add(key);
            else {
                if (!attribute.status().equals("KNOWN") || attribute.value() == null || attribute.value().isNull()) invalid();
                actual.put(key, values.convertValue(attribute.value(), Object.class));
            }
        }
        if (!unknown.isEmpty()) return new TemplateApplicabilityProtocol.Result("UNKNOWN", unknown);
        var constants = new HashMap<String,Object>();
        for (var constant : compiled.input().constants()) constants.put(constant.key(), values.convertValue(constant.value(), Object.class));
        var context = SimpleEvaluationContext.forReadOnlyDataBinding().build();
        context.setVariable("attributes", java.util.Collections.unmodifiableMap(actual));
        context.setVariable("constants", java.util.Collections.unmodifiableMap(constants));
        Object result = compiled.expression().getValue(context);
        if (!(result instanceof Boolean)) invalid();
        return new TemplateApplicabilityProtocol.Result(Boolean.TRUE.equals(result) ? "APPLICABLE" : "NOT_APPLICABLE", List.of());
    }

    record Compiled(TemplateApplicabilityProtocol.Expression input, SpelExpression expression) { }
    private record Visit(SpelNode node, int depth) { }
    private static void invalid() { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
    private static void unsupported() { throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED); }
}

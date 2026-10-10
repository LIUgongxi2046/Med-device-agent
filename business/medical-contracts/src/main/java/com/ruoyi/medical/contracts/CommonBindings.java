package com.ruoyi.medical.contracts;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import static com.ruoyi.medical.contracts.TypedSchemaBindings.definition;
import com.ruoyi.medical.contracts.TypedSchemaBindings.Definition;
import com.ruoyi.medical.contracts.TypedSchemaBindings.ScalarWire;

public final class CommonBindings {
    private CommonBindings() { }
    public static final Definition<String> ID = definition("interfaces", "Id", String.class, ScalarWire.UUID);
    public static final Definition<String> REVISION = definition("interfaces", "Revision", String.class, ScalarWire.REVISION);
    public static final Definition<String> HASH = definition("interfaces", "Hash", String.class);
    public static final Definition<String> NULLABLE_ID = definition("interfaces", "NullableId", String.class, ScalarWire.NULLABLE_UUID);
    public static final Definition<String> NULLABLE_HASH = definition("interfaces", "NullableHash", String.class);
    public static final Definition<String> NULLABLE_TEXT = definition("interfaces", "NullableText", String.class);
    public static final Definition<List<String>> IDS = definition("interfaces", "Ids", new TypeReference<List<String>>() { }, ScalarWire.UUIDS);
    public static final Definition<List<String>> TEXTS = definition("interfaces", "Texts", new TypeReference<List<String>>() { }, ScalarWire.NONE);
    public static final Definition<String> TIMESTAMP = definition("interfaces", "Timestamp", String.class, ScalarWire.TIMESTAMP);
    public static final Definition<CommonProtocol.Coverage> COVERAGE = definition("interfaces", "Coverage", CommonProtocol.Coverage.class);
    public static final Definition<CommonProtocol.ArtifactRef> ARTIFACT_REF = definition("interfaces", "ArtifactRef", CommonProtocol.ArtifactRef.class);
    public static final Definition<CommonProtocol.Selection> SELECTION = definition("interfaces", "Selection", CommonProtocol.Selection.class);
    public static final Definition<CommonProtocol.TextSelection> TEXT_SELECTION = definition("interfaces", "TextSelection", CommonProtocol.TextSelection.class);
    public static final Definition<CommonProtocol.ImageSelection> IMAGE_SELECTION = definition("interfaces", "ImageSelection", CommonProtocol.ImageSelection.class);
    public static final Definition<CommonProtocol.TableSelection> TABLE_SELECTION = definition("interfaces", "TableSelection", CommonProtocol.TableSelection.class);
    public static final Definition<CommonProtocol.SourceScope> SOURCE_SCOPE = definition("interfaces", "SourceScope", CommonProtocol.SourceScope.class);
    public static final Definition<CommonProtocol.EvidenceDecision> EVIDENCE_DECISION = definition("interfaces", "EvidenceDecision", CommonProtocol.EvidenceDecision.class);
    public static List<Definition<?>> definitions() { return List.of(ID, REVISION, HASH, NULLABLE_ID, NULLABLE_HASH, NULLABLE_TEXT, IDS, TEXTS, TIMESTAMP, COVERAGE, ARTIFACT_REF, SELECTION, TEXT_SELECTION, IMAGE_SELECTION, TABLE_SELECTION, SOURCE_SCOPE, EVIDENCE_DECISION); }
}

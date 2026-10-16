package com.ruoyi.medical.persistence;

import java.util.List;

public final class SourceExactProtocol {
    private SourceExactProtocol() { }
    public record Field(String field_kind,String raw_value,String alias_value,String alias_reason,String chunk_id,
            String quote_hash,String locator_sha256,int start_codepoint,int end_codepoint) { }
    public record PublicationInput(String schema_version,String project_id,String parse_version,String index_version,
            String approval_revision,String binding_revision,String rights_revision,String expected_revision,List<Field> fields,String reason) {
        public PublicationInput { fields=List.copyOf(fields); }
    }
    public record SearchInput(String schema_version,String project_id,String field_kind,String query,String purpose) { }
}

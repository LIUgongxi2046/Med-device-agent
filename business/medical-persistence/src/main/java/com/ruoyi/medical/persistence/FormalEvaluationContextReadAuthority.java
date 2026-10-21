package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import java.io.IOException;
import java.util.*;
import static com.ruoyi.medical.persistence.FormalEvaluationContextProtocol.*;

/** Historical content-read checks only. No current-head, approval-validity or executable proof is returned. */
public final class FormalEvaluationContextReadAuthority {
    private final FormalAgentCandidateAuthority candidates;
    private final AgentExecutionPolicyAuthority policies;
    private final AgentPublicationDependencies provenance;
    private final PermissionSchema json;
    public FormalEvaluationContextReadAuthority(FormalAgentCandidateAuthority candidates, AgentExecutionPolicyAuthority policies,
            AgentPublicationDependencies provenance, PermissionSchema json) {
        this.candidates=candidates; this.policies=policies; this.provenance=provenance; this.json=json;
    }
    void require(EnterpriseAccess.Admission admission, Definition definition) throws IOException {
        FormalEvaluationExecutionAdmission.current(admission);
        var candidate=candidates.require(admission,definition.candidate(),definition.projectId(),"AGENT_PUBLICATION");
        if(!candidate.executionPolicy().equals(definition.executionPolicy()))bad();
        var policy=policies.requireHistoricalByVersion(admission,definition.executionPolicy().version_id(),
                definition.executionPolicy().content_sha256(),definition.projectId(),candidate.classification(),"AGENT_PUBLICATION");
        var modelNodes=new TreeMap<String,AgentExecutionPolicyProtocol.NodeBinding>();
        for(var node:policy.definition().bindings())if(node.modelVersion()!=null&&modelNodes.put(node.nodeKind(),node)!=null)bad();
        if(modelNodes.size()!=definition.nodeBindings().size())bad();
        var roots=new TreeMap<String,PermissionProtocol.Basis>();
        for(var node:definition.nodeBindings()) {
            var expected=modelNodes.remove(node.nodeKind());if(expected==null||!expected.roleVersion().equals(node.role()))bad();
            FormalEvaluationContextDependencies.member(candidate.manifestDefinition(),"role_versions",node.role());
            FormalEvaluationContextDependencies.member(candidate.manifestDefinition(),"prompt_revisions",node.prompt());
            FormalEvaluationContextDependencies.member(candidate.manifestDefinition(),"tool_versions",node.contextAssembler());
            FormalEvaluationContextDependencies.schemaMember(candidate.manifestDefinition(),node.contextAssembler().version_id(),"INPUT",node.inputSchema());
            FormalEvaluationContextDependencies.schemaMember(candidate.manifestDefinition(),node.contextAssembler().version_id(),"OUTPUT",node.outputSchema());
            for(var ref:List.of(node.role(),node.prompt(),node.contextAssembler())) {
                var prior=roots.putIfAbsent(ref.version_id(),ref);if(prior!=null&&!prior.equals(ref))bad();
            }
        }
        if(!modelNodes.isEmpty())bad();
        // Generic version/provenance readers validate current privacy/read grants and source rights, not mutable capability heads.
        var refs=List.copyOf(roots.values());
        String current=json.canonical(provenance.require(admission,definition.projectId(),refs,"AGENT_PUBLICATION").authorizations());
        candidate.requireUnchanged();policy.requireUnchanged();
        if(!current.equals(json.canonical(provenance.require(admission,definition.projectId(),refs,"AGENT_PUBLICATION").authorizations())))
            throw new TenantAccessException(ErrorCode.SCOPE_STALE);
    }
    private static void bad() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}

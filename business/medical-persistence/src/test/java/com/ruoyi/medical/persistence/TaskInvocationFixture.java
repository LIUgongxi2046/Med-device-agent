package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import java.math.BigInteger;
import java.util.List;
import javax.sql.DataSource;
import static org.mockito.Mockito.*;

/** Already-authenticated/context evidence doubles only. Not a live database, source or mTLS claim. */
final class TaskInvocationFixture {
    static String id(int n){return "00000000-0000-4000-8000-"+String.format("%012d",n);}
    final String hash="a".repeat(64),tenant=id(1),actor=id(2),project=id(5),run=id(8),principal=id(9),step=id(10),contextId=id(12),modelId=id(16),connection=id(6);
    final FormalInvocationSchema schema;final TaskInvocationOriginals originals=mock(TaskInvocationOriginals.class);final TaskInvocationContext contexts=mock(TaskInvocationContext.class);
    final TaskInvocationOriginals.Stored stored=mock(TaskInvocationOriginals.Stored.class);
    final TaskInvocationContext.Registered context=mock(TaskInvocationContext.Registered.class);
    final EnterpriseAccess.Admission admission=mock(EnterpriseAccess.Admission.class);
    final RunTokenAuthority.Admission worker=mock(RunTokenAuthority.Admission.class);
    final TaskInvocationProtocol.Request request;final RunTokenAuthority.ActionRequest action;
    final TaskInvocationRecords records;final TaskInvocationRecords.Binding binding;
    final JsonNode body,price,dependencies;final ModelConfigurationProtocol.Fixed model;
    TaskInvocationFixture()throws Exception {
        schema=new FormalInvocationSchema();
        var node=new TaskInvocationProtocol.Node(tenant,project,id(40),run,principal,step,id(41),id(42),"1","spiffe://test/gateway",id(43),hash,hash,id(44),hash,"INTERNAL");
        var params=schema.parse("{\"max_output_tokens\":4,\"temperature\":null,\"reasoning_effort\":\"none\",\"stop\":[],\"purpose\":\"TASK\"}");
        request=new TaskInvocationProtocol.Request("1.0.0","TASK","N02",node,modelId,"DOCUMENT_BODY",
                new CommonProtocol.ArtifactRef(id(45),hash,"urn:medical:task-invocation-context:1.0.0#/$defs/ExactInput",new CommonProtocol.Coverage(hash,hash,true,null)),
                basis(46),basis(47),basis(48),basis(49),List.of(),basis(50),basis(51),"10",params);
        price=schema.parse("{\"schema_version\":\"1.0.0\",\"kind\":\"TOKEN_ONLY_PER_MILLION\",\"provider\":\"deepseek\",\"model_id\":\"deepseek-chat\",\"model_version\":\"fixed\",\"currency\":\"USD\",\"uncached_input_per_million\":\"0\",\"cache_read_per_million\":\"0\",\"cache_write_per_million\":\"0\",\"output_per_million\":\"0\",\"rounding\":\"CEILING_8_DECIMAL_PLACES\"}");
        dependencies=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().set("price",price);
        var definition=new ModelConfigurationProtocol.Definition("deepseek","https://provider.test","region","deepseek-chat","fixed",null,id(52),id(53),
                new ModelConfigurationProtocol.Limits("100","20","1","10","100",60,false,List.of("none"),0),basis(4),"USD",new ModelConfigurationProtocol.DataPolicy(List.of("INTERNAL"),List.of("region"),false,"0",basis(54)),"urn:request",hash,"v1");
        model=new ModelConfigurationProtocol.Fixed("1.0.0",tenant,project,id(55),modelId,null,definition,"test",id(56),"1",actor,"2026-10-20T00:00:00Z");
        body=schema.parse("{\"model\":\"deepseek-chat\",\"messages\":[{\"role\":\"user\",\"content\":\"fixed question\"}],\"max_tokens\":4,\"stream\":true}");
        when(admission.actorId()).thenReturn(actor);when(admission.formalWorker()).thenReturn(true);
        when(admission.context()).thenReturn(new com.ruoyi.medical.identity.BusinessSessionProtocol.Context(tenant,"0","1","1"));
        var proof=mock(FormalWorkerAccess.Proof.class);when(admission.formalProof()).thenReturn(proof);
        when(proof.identity()).thenReturn(new FormalWorkerAccess.Identity(tenant,project,run,principal,actor,"TASK",step,TaskInvocationService.actionId(contextId),"1","spiffe://test/gateway","{}","{}",List.of("model.send"),null));
        when(stored.actorId()).thenReturn(actor);when(stored.contextId()).thenReturn(contextId);when(stored.connectionVerificationId()).thenReturn(connection);
        when(stored.request()).thenReturn(request);when(stored.dependencies()).thenReturn(dependencies);when(stored.price()).thenReturn(schema.price(id(4),price));
        when(context.contextId()).thenReturn(contextId);when(context.request()).thenReturn(request);when(context.dependencies()).thenReturn(dependencies);
        when(context.model()).thenReturn(model);when(context.providerBody()).thenReturn(body);when(context.price()).thenReturn(schema.price(id(4),price));
        when(context.sendAuthorizations()).thenReturn(schema.parse("{\"phase\":\"MODEL_CALL\"}"));when(context.authorizationSha256()).thenReturn(hash);when(context.credentialReferenceId()).thenReturn(id(52));when(context.credentialRevision()).thenReturn(id(53));
        when(originals.requireStored(tenant,contextId)).thenReturn(stored);when(contexts.requireCurrent(admission,contextId)).thenReturn(context);
        records=new TaskInvocationRecords(mock(DataSource.class),originals,schema);binding=records.bind(admission,context,TaskInvocationService.actionId(contextId));
        action=new RunTokenAuthority.ActionRequest(run,step,binding.action(),BigInteger.ONE,hash,binding.requestHash(),"MODEL_CALL",modelId,new PermissionProtocol.Scope(tenant,project,"CONFIGURATION",id(55)),"model.send","DOCUMENT_BODY");
        when(worker.tenantId()).thenReturn(tenant);when(worker.projectId()).thenReturn(project);when(worker.initiatorId()).thenReturn(actor);when(worker.principalId()).thenReturn(principal);
        when(worker.runId()).thenReturn(run);when(worker.stepId()).thenReturn(step);when(worker.actionId()).thenReturn(action.actionId());when(worker.leaseGeneration()).thenReturn(BigInteger.ONE);when(worker.serviceUri()).thenReturn("spiffe://test/gateway");
        when(worker.delegatedPermissions()).thenReturn(List.of(new RunTokenAuthoritySchema.Permission(action.scope(),action.action(),action.purpose())));
    }
    PermissionProtocol.Basis basis(int n){return new PermissionProtocol.Basis(id(n),hash);}
}

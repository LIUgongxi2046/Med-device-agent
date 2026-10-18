package com.ruoyi.medical.persistence;

import java.util.List;

final class EvaluationFixture {
    static String id(int n){return String.format("00000000-0000-4000-8000-%012d",n);}
    static PermissionProtocol.Basis ref(int n){return new PermissionProtocol.Basis(id(n),"a".repeat(64));}
    static EvaluationProtocol.Sample sample(int n,String kind,String split){return new EvaluationProtocol.Sample(id(n),id(n+100),split,"PTR",kind,ref(n+200),ref(n+300),List.of());}
    static EvaluationProtocol.Create create(EvaluationSchema schema,List<EvaluationProtocol.Sample> samples,List<String> dimensions,int repeats)throws Exception {
        var normalized=new java.util.ArrayList<EvaluationProtocol.Sample>();
        for(var sample:samples) {
            var expectations=dimensions.stream().map(d->new EvaluationProtocol.Expectation(d,
                java.util.stream.IntStream.range(0,10).mapToObj(i->"required-"+i).toList(),d.equals("EVAL-11")?List.of("legal-0","legal-1"):List.of())).toList();
            var provisional=new EvaluationProtocol.Sample(sample.sample_id(),sample.source_family(),sample.split(),sample.document_type(),sample.evidence_class(),sample.input_ref(),sample.expected_ref(),expectations);
            normalized.add(new EvaluationProtocol.Sample(sample.sample_id(),sample.source_family(),sample.split(),sample.document_type(),sample.evidence_class(),sample.input_ref(),
                new PermissionProtocol.Basis(sample.expected_ref().version_id(),schema.hash(schema.expectedItems(provisional))),expectations));
        }
        return new EvaluationProtocol.Create("1.0.0",id(1),ref(10),new PermissionProtocol.Basis(id(11),schema.hash(normalized)),normalized,List.of(ref(20),ref(21)),
            ref(12),dimensions,new EvaluationProtocol.Variance(repeats,"ALL_REQUIRED_RUNS",null),ref(13),"PRODUCTION","Fixed pre-release assertion review");
    }
    static EvaluationProtocol.Definition definition(EvaluationSchema schema,List<EvaluationProtocol.Sample> samples,List<String> dimensions,int repeats)throws Exception{
        var r=create(schema,samples,dimensions,repeats);var json=new PermissionSchema();
        var cs=r.candidates().stream().map(c->new EvaluationProtocol.Candidate(c.version_id(),c.content_sha256(),id(30),id(31),json.object(),json.object(),"1.0.0","b".repeat(64))).toList();
        return new EvaluationProtocol.Definition("1.0.0",id(2),id(3),r,cs,r.samples().stream().map(EvaluationProtocol.Sample::sample_id).sorted().toList(),id(4),"1",id(5),"2026-10-16T00:00:00Z");
    }
    static EvaluationProtocol.Definition withApplicableCount(EvaluationSchema schema,EvaluationProtocol.Definition definition,int count)throws Exception {
        var request=definition.request();var samples=new java.util.ArrayList<EvaluationProtocol.Sample>();
        for(var sample:request.samples()) {
            var expectations=sample.expectations().stream().map(e->new EvaluationProtocol.Expectation(e.dimension(),java.util.stream.IntStream.range(0,count).mapToObj(i->"required-"+i).toList(),e.control_item_ids())).toList();
            var provisional=new EvaluationProtocol.Sample(sample.sample_id(),sample.source_family(),sample.split(),sample.document_type(),sample.evidence_class(),sample.input_ref(),sample.expected_ref(),expectations);
            samples.add(new EvaluationProtocol.Sample(sample.sample_id(),sample.source_family(),sample.split(),sample.document_type(),sample.evidence_class(),sample.input_ref(),new PermissionProtocol.Basis(sample.expected_ref().version_id(),schema.hash(schema.expectedItems(provisional))),expectations));
        }
        var changed=new EvaluationProtocol.Create(request.schema_version(),request.project_id(),request.dataset(),new PermissionProtocol.Basis(request.sample_manifest_artifact().version_id(),schema.hash(samples)),samples,request.candidates(),request.rule_revision(),request.required_dimensions(),request.variance_policy(),request.authorized_use_evidence(),request.environment(),request.reason());
        return new EvaluationProtocol.Definition(definition.schema_version(),definition.tenant_id(),definition.evaluation_id(),changed,definition.candidate_snapshots(),definition.approved_expectation_samples(),definition.authority_grant_id(),definition.authority_revision(),definition.created_by(),definition.created_at());
    }
    static EvaluationProtocol.Append append(EvaluationProtocol.Definition d,String hash,EvaluationProtocol.Cell cell,String revision,String supersedes,String numerator,String denominator){
        var sample=d.request().samples().stream().filter(s->s.sample_id().equals(cell.sample_id())).findFirst().orElseThrow();
        return new EvaluationProtocol.Append("1.0.0",revision,hash,cell,sample.input_ref(),ref(50),List.of(ref(51)),numerator,denominator,"0","0","2","2","SCORED",supersedes,"Independent expert observation");
    }
    static EvaluationProtocol.Score score(int id,EvaluationProtocol.Definition d,String hash,EvaluationProtocol.Append a){return new EvaluationProtocol.Score("1.0.0",EvaluationFixture.id(id),d.evaluation_id(),hash,d.request().rule_revision(),a,EvaluationFixture.id(40),EvaluationFixture.id(41),"1","2026-10-16T00:01:00Z");}
}

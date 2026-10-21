package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import java.util.*;
import static com.ruoyi.medical.persistence.FormalEvaluationExecutionProtocol.*;

/** Deterministic coverage checking only. Passing this function is not an original-evidence proof. */
final class FormalEvaluationCoverage {
 private FormalEvaluationCoverage(){}
 record Key(String sample,int repeat,String node){}
 static Key key(Node n){return new Key(n.cell().sampleId(),n.cell().repeatIndex(),n.nodeKind());}
 static void exact(List<Node> expected,List<FormalEvaluationExecutionProtocol.Record> records){
  if(expected.isEmpty()||expected.size()>10000||records.size()!=expected.size())bad();
  var byKey=new HashMap<Key,Node>();for(var node:expected)if(byKey.put(key(node),node)!=null)bad();
  var seen=new HashSet<Key>();var stepIds=new HashSet<String>();var invocations=new HashSet<String>();
  var runs=new HashMap<String,String>();var cellRuns=new HashMap<String,String>();
  for(var record:records){var node=record.node();var key=key(node);if(!node.equals(byKey.get(key))||!seen.add(key)||!stepIds.add(record.stepId()))bad();
   String cell=node.cell().sampleId()+":"+node.cell().repeatIndex();var previous=runs.putIfAbsent(record.runId(),cell);if(previous!=null&&!previous.equals(cell))bad();
   previous=cellRuns.putIfAbsent(cell,record.runId());if(previous!=null&&!previous.equals(record.runId()))bad();
   if(node.model()!=null&&record.invocations().isEmpty()||node.model()==null&&!record.invocations().isEmpty())bad();
   var actions=new HashSet<String>();for(var invocation:record.invocations())if(!invocations.add(invocation.invocationId())||!actions.add(invocation.actionId()))bad();
   if(new HashSet<>(record.checkIds()).size()!=record.checkIds().size()||new HashSet<>(record.actualArtifacts()).size()!=record.actualArtifacts().size())bad();
  }
 }
 static List<Artifact> artifacts(List<FormalEvaluationExecutionProtocol.Record> records){
  var found=new TreeMap<String,Artifact>();for(var record:records){
   var all=new ArrayList<Artifact>(List.of(record.inputManifest(),record.stepEnvelope(),record.stepResult()));if(record.sessionExport()!=null)all.add(record.sessionExport());all.addAll(record.actualArtifacts());
   for(var invocation:record.invocations()){all.add(invocation.modelResult());all.add(invocation.providerCapture());}
   for(var value:all){var previous=found.putIfAbsent(value.artifactId(),value);if(previous!=null&&!previous.equals(value))bad();}
  }return List.copyOf(found.values());
 }
 private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}

package com.ruoyi.medical.persistence;

import com.ruoyi.medical.workflow.WorkflowProtocol.Reference;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.util.function.ThrowingSupplier;

/** End-to-end deterministic host output production. Transaction proofs never cross
 * physical I/O. Original intents always recover by GET, including unknown PUT outcomes. */
public final class TaskCandidateAssemblyArtifactProduction {
    private final TaskRunActivityPreparation runs;private final TaskCandidateAssemblyAuthority inputs;private final TaskCandidateAssemblyProducerAuthority producers;private final TaskCandidateAssemblyObjectLedger objects;private final RuntimeObjectTransport transport;private final TaskCandidateAssemblyCrossFileAuthority crossFiles;private final TaskCandidateAssemblyCheckService checks;
    public TaskCandidateAssemblyArtifactProduction(TaskRunActivityPreparation runs,TaskCandidateAssemblyAuthority inputs,TaskCandidateAssemblyProducerAuthority producers,TaskCandidateAssemblyObjectLedger objects,RuntimeObjectTransport transport,TaskCandidateAssemblyCrossFileAuthority crossFiles,TaskCandidateAssemblyCheckService checks){this.runs=runs;this.inputs=inputs;this.producers=producers;this.objects=objects;this.transport=transport;this.crossFiles=crossFiles;this.checks=checks;}
    @FunctionalInterface public interface Completion<T>{T apply(TaskRunActivityPreparation.Current current,TaskCandidateAssemblyAuthority.Verified input,List<TaskCandidateAssemblyObjectLedger.Registered> artifacts)throws Exception;}
    private record Physical(TaskCandidateAssemblyObjectLedger.Attempt attempt,RuntimeObjectTransport.VerifiedTransfer transfer){}
    private static final List<String> ORDER=List.of("CROSS_FILE_CHECK_REPORT","DOCUMENT_BODY","ASSEMBLY_COMPANION","ASSEMBLY_MANIFEST");
    public <T>T produce(HttpServletRequest http,Reference reference,Completion<T> completion)throws Exception{return execute(http,reference,completion,false);}
    /** Original physical GETs only. A missing saved aggregate check is never invented. */
    public <T>T recover(HttpServletRequest http,Reference reference,Completion<T> completion)throws Exception{return execute(http,reference,completion,true);}
    private <T>T execute(HttpServletRequest http,Reference reference,Completion<T> completion,boolean recovery)throws Exception {
        var crossReads=crossFiles.read(http,reference);var physical=new ArrayList<Physical>();
        if(!recovery)runs.withCurrent(http,reference,c->{var input=inputs.require(c,crossReads);var missing=new LinkedHashMap<String,Integer>();for(String kind:ORDER)if(!objects.hasOriginal(input,kind)){
            // A UUID entry adds at most 39 UTF-8 bytes. This is a capacity upper
            // bound only, never a predicted check identity or artifact digest.
            missing.put(kind,canonical(input,kind).length+(kind.equals("ASSEMBLY_MANIFEST")?39:0));
        }if(!missing.isEmpty())producers.require(c,input).requireFamily(missing);return null;});
        for(String kind:ORDER){
            var attempt=runs.withCurrent(http,reference,c->{var input=fresh(c,crossReads,physical);return recovery||objects.hasOriginal(input,kind)?objects.recoverOriginal(input,kind):objects.prepare(input,producers.require(c,input),kind);});
            var transfer=transport.transferVerified(attempt,()->ThrowingSupplier.of(()->runs.withCurrent(http,reference,c->{var input=fresh(c,crossReads,physical);if(attempt.priorAttempt())objects.requireOriginalTransfer(input,attempt);else objects.requireTransfer(input,producers.require(c,input),attempt);return null;})).get());
            runs.withCurrent(http,reference,c->{var input=fresh(c,crossReads,physical);var registered=recovery?objects.readOriginal(input,attempt,transfer):attempt.priorAttempt()?objects.registerRecovered(input,attempt,transfer):objects.register(input,producers.require(c,input),attempt,transfer);if(kind.equals("CROSS_FILE_CHECK_REPORT")){if(recovery)checks.requireSaved(input,registered);else checks.save(input,registered);}return null;});
            physical.add(new Physical(attempt,transfer));
        }
        return runs.withCurrent(http,reference,c->{var input=fresh(c,crossReads,physical);var registered=new ArrayList<TaskCandidateAssemblyObjectLedger.Registered>();for(var item:physical)registered.add(objects.readOriginal(input,item.attempt(),item.transfer()));return completion.apply(c,input,List.copyOf(registered));});
    }
    private TaskCandidateAssemblyAuthority.Verified fresh(TaskRunActivityPreparation.Current c,TaskCandidateAssemblyCrossFileAuthority.Verified crossReads,List<Physical> physical)throws Exception{
        var base=inputs.require(c,crossReads);if(physical.isEmpty())return base;var report=physical.get(0);var registered=objects.readOriginal(base,report.attempt(),report.transfer());return base.withCommittedCheck(checks.requireSaved(base,registered));
    }
    private static byte[] canonical(TaskCandidateAssemblyAuthority.Verified input,String kind)throws Exception{return ContentReadAuthorization.canonicalBytes(switch(kind){case "ASSEMBLY_MANIFEST"->new TaskSourceVerifierSchema().tree(input.manifest());case "DOCUMENT_BODY"->input.body();case "ASSEMBLY_COMPANION"->input.companion();case "CROSS_FILE_CHECK_REPORT"->input.crossFileReport();default->throw new IllegalArgumentException("N06_KIND_REQUIRED");});}
}

package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Bounded trusted deployment adapter. No shell, provider body, credential or token in argv,
 * stdout or errors. An ambiguous pipe never implies provider completion or another dispatch. */
public final class TaskInvocationLocalBridge {
    public record Configuration(Path executable,String executableSha256,Path entrypoint,String entrypointSha256,
            Path packageRoot,Path packageManifest,String packageManifestSha256,Path privateConfiguration,Duration timeout) {
        public Configuration {
            for(Path path:List.of(executable,entrypoint,packageRoot,packageManifest,privateConfiguration))if(!path.isAbsolute()||!path.normalize().equals(path))throw new IllegalArgumentException("TASK_ADAPTER_ABSOLUTE_PATH_REQUIRED");
            for(String hash:List.of(executableSha256,entrypointSha256,packageManifestSha256))WireValues.hash(hash);
            if(List.of(executable.toString(),entrypoint.toString(),packageRoot.toString(),privateConfiguration.toString()).stream().mapToInt(v->v.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).sum()>8100)throw new IllegalArgumentException("TASK_ADAPTER_ARGV_BOUND");
            if(timeout.isNegative()||timeout.isZero()||timeout.compareTo(Duration.ofMinutes(5))>0)throw new IllegalArgumentException("TASK_ADAPTER_TIMEOUT_REQUIRED");
        }
        List<String> command(){return List.of(executable.toString(),"-I",entrypoint.toString(),"--config",privateConfiguration.toString(),"--package-root",packageRoot.toString());}
    }
    private static final int STDIN_MAX=32768,STDOUT_MAX=8192,STDERR_MAX=4096;
    private final Configuration configuration;private final Runner runner;
    private final JsonMapper json=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public TaskInvocationLocalBridge(Configuration configuration){this(configuration,TaskInvocationLocalBridge::runProcess);}
    TaskInvocationLocalBridge(Configuration configuration,Runner runner){this.configuration=Objects.requireNonNull(configuration);this.runner=Objects.requireNonNull(runner);}
    /** Deployment readiness only, never model/send authority. */
    public void requireReady()throws Exception {
        Configuration c=configuration;requireFile(c.executable,c.executableSha256,128*1024*1024,true);requireFile(c.entrypoint,c.entrypointSha256,1024*1024,false);
        byte[] manifest=requireFile(c.packageManifest,c.packageManifestSha256,1024*1024,false);requireDirectory(c.packageRoot);
        var document=json.readTree(manifest);fields(document,Set.of("schema_version","files"));if(!"1.0.0".equals(document.path("schema_version").textValue())||!document.path("files").isArray()||document.path("files").isEmpty()||document.path("files").size()>4096)invalid();
        var declared=new TreeSet<String>();long total=0;for(var file:document.required("files")){
            fields(file,Set.of("path","sha256"));String relative=file.required("path").textValue(),hash=file.required("sha256").textValue();WireValues.hash(hash);
            if(relative==null||relative.isBlank()||relative.contains("\\")||Path.of(relative).isAbsolute()||!Path.of(relative).normalize().toString().equals(relative)||relative.startsWith("../")||!declared.add(relative))invalid();
            var target=c.packageRoot.resolve(relative);if(!target.startsWith(c.packageRoot))invalid();for(var parent=target.getParent();parent!=null&&parent.startsWith(c.packageRoot);parent=parent.getParent())requireDirectory(parent);
            total+=requireFile(target,hash,16*1024*1024,false).length;if(total>64L*1024*1024)invalid();
        }
        var actual=new TreeSet<String>();try(var paths=Files.walk(c.packageRoot)){
            int visited=0;var iterator=paths.iterator();while(iterator.hasNext()){if(++visited>8192)invalid();var path=iterator.next();if(path.equals(c.packageRoot))continue;if(Files.isSymbolicLink(path))invalid();if(Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS)){requireDirectory(path);continue;}actual.add(c.packageRoot.relativize(path).toString());}
        }
        if(!declared.equals(actual)||declared.stream().noneMatch(p->p.startsWith("medical_model_gateway/"))||declared.stream().noneMatch(p->p.startsWith("medical_contracts/")))invalid();
        // Python owns private configuration descriptor validation and secret loading.
        if(!Files.isRegularFile(c.privateConfiguration,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(c.privateConfiguration))invalid();
    }
    public Outcome execute(TaskInvocationService.DispatchCall command)throws Exception {
        Objects.requireNonNull(command);requireReady();return execute(command.consume(),"DISPATCH",command.invocationId(),null);
    }
    public Outcome execute(TaskPlanDraftInvocationService.DispatchCall command)throws Exception {Objects.requireNonNull(command);requireReady();return execute(command.consume(),"DISPATCH",command.invocationId(),null);}
    public Outcome execute(TaskSectionWriterInvocationService.DispatchCall command)throws Exception {Objects.requireNonNull(command);requireReady();return execute(command.consume(),"DISPATCH",command.invocationId(),null);}
    public Outcome execute(TaskSourceVerifierInvocationService.DispatchCall command)throws Exception {Objects.requireNonNull(command);requireReady();return execute(command.consume(),"DISPATCH",command.invocationId(),null);}
    public Outcome execute(FormalEvaluationInvocationService.DispatchCall command)throws Exception {Objects.requireNonNull(command);requireReady();return execute(command.consume(),"DISPATCH",command.invocationId(),null);}
    public Outcome execute(DocumentReviewInvocationService.DispatchCall command)throws Exception {Objects.requireNonNull(command);requireReady();return execute(command.consume(),"DISPATCH",command.invocationId(),null);}
    public Outcome recover(FormalEvaluationInvocationService.RecoveryCall command)throws Exception {Objects.requireNonNull(command);requireReady();return execute(command.consume(),"RECOVER",command.invocationId(),command.attemptId());}
    /** Read-only provider recovery. Only a private original-authorized one-shot command is accepted. */
    public Outcome recover(TaskInvocationRecovery.RecoveryCall command)throws Exception {
        Objects.requireNonNull(command);requireReady();return execute(command.consume(),"RECOVER",command.invocationId(),command.attemptId());
    }
    private Outcome execute(byte[] input,String mode,String invocation,String originalAttempt)throws Exception {
        if(input.length==0||input.length>STDIN_MAX)invalid();byte[] output;
        try{output=runner.run(configuration,input);}catch(Exception failure){if(failure instanceof InterruptedException)Thread.currentThread().interrupt();throw new Unknown();}finally{Arrays.fill(input,(byte)0);}
        if(output==null||output.length==0||output.length>STDOUT_MAX)throw new Unknown();
        try{var response=json.readTree(output);fields(response,Set.of("schema_version","mode","invocation_id","dispatch_attempt_id","recorded"));
            if(!"1.0.0".equals(response.path("schema_version").textValue())||!mode.equals(response.path("mode").textValue())||!invocation.equals(response.path("invocation_id").textValue())||!response.path("recorded").isBoolean())invalid();
            boolean recorded=response.required("recorded").booleanValue();var attempt=response.required("dispatch_attempt_id");
            if(recorded){if(!attempt.isTextual())invalid();WireValues.uuid(attempt.textValue());}else if(!attempt.isNull())invalid();
            if(originalAttempt!=null&&(!recorded||!originalAttempt.equals(attempt.textValue())))invalid();
            return new Outcome(invocation,recorded?attempt.textValue():null,recorded);
        }catch(Exception malformed){throw new Unknown();}
    }
    /** Factual adapter acknowledgement only. Backend must independently verify original076. */
    public record Outcome(String invocationId,String attemptId,boolean recorded){}
    public static final class Unknown extends Exception {private Unknown(){super("TASK_ADAPTER_OUTCOME_UNKNOWN");}}
    @FunctionalInterface interface Runner {byte[] run(Configuration c,byte[] input)throws Exception;}
    private static byte[] runProcess(Configuration c,byte[] input)throws Exception {
        var builder=new ProcessBuilder(c.command());builder.directory(c.packageRoot.toFile());builder.environment().clear();builder.environment().put("LANG","C.UTF-8");builder.environment().put("PYTHONDONTWRITEBYTECODE","1");
        var process=builder.start();var threads=Executors.newFixedThreadPool(3,r->{var t=new Thread(r,"task-model-adapter-pipe");t.setDaemon(true);return t;});
        try{
            var stdout=threads.submit(()->bounded(process.getInputStream(),STDOUT_MAX));var stderr=threads.submit(()->bounded(process.getErrorStream(),STDERR_MAX));
            var stdin=threads.submit(()->{try(var out=process.getOutputStream()){out.write(input);}return true;});
            if(!process.waitFor(c.timeout.toMillis(),TimeUnit.MILLISECONDS))throw new IOException("TASK_ADAPTER_TIMEOUT");stdin.get(1,TimeUnit.SECONDS);byte[] errors=stderr.get(1,TimeUnit.SECONDS),result=stdout.get(1,TimeUnit.SECONDS);
            if(process.exitValue()!=0||errors.length!=0)throw new IOException("TASK_ADAPTER_FAILED");return result;
        }finally{Arrays.fill(input,(byte)0);if(process.isAlive()){process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();}try{process.getOutputStream().close();process.getInputStream().close();process.getErrorStream().close();}finally{threads.shutdownNow();}}
    }
    private static byte[] bounded(InputStream stream,int max)throws IOException{try(stream){var bytes=stream.readNBytes(max+1);if(bytes.length>max)throw new IOException("TASK_ADAPTER_PIPE_BOUND");return bytes;}}
    private static byte[] requireFile(Path path,String hash,int maximum,boolean executable)throws Exception {
        var before=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);if(!before.isRegularFile()||before.isSymbolicLink()||before.size()<1||before.size()>maximum||(executable&&!Files.isExecutable(path)))invalid();
        var permissions=Files.getPosixFilePermissions(path,LinkOption.NOFOLLOW_LINKS);if(permissions.contains(PosixFilePermission.GROUP_WRITE)||permissions.contains(PosixFilePermission.OTHERS_WRITE))invalid();
        byte[] bytes;try(var channel=Files.newByteChannel(path,Set.of(StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS));var input=java.nio.channels.Channels.newInputStream(channel)){bytes=input.readNBytes(maximum+1);}
        var after=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);if(bytes.length!=before.size()||bytes.length>maximum||!Objects.equals(before.fileKey(),after.fileKey())||!before.lastModifiedTime().equals(after.lastModifiedTime())||before.size()!=after.size()||!WireValues.sha256(bytes).equals(hash))invalid();return bytes;
    }
    private static void requireDirectory(Path path)throws IOException{if(!Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(path))invalid();var p=Files.getPosixFilePermissions(path,LinkOption.NOFOLLOW_LINKS);if(p.contains(PosixFilePermission.GROUP_WRITE)||p.contains(PosixFilePermission.OTHERS_WRITE))invalid();}
    private static void fields(JsonNode n,Set<String> expected){var keys=new HashSet<String>();n.fieldNames().forEachRemaining(keys::add);if(!n.isObject()||!keys.equals(expected))invalid();}
    private static void invalid(){throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);}
}

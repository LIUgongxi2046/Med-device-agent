package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import java.io.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Executes only a server-configured, hash-pinned tokenizer wrapper. Never accepts a client path or argv.
 * The deployment must confine this executable's OS/network authority; this class is not an OS sandbox. */
public final class FormalTokenizerRunner {
    private final Configuration config;
    private final JsonMapper json=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public record Configuration(boolean enabled, Path executable, String executableSha256, String tokenizerVersion,
            int maximumInputBytes, int maximumOutputBytes, Duration timeout) {
        public Configuration {
            if(enabled) {
                Objects.requireNonNull(executable);WireValues.hash(executableSha256);WireValues.uuid(tokenizerVersion);
                if(!executable.isAbsolute()||maximumInputBytes<1||maximumOutputBytes<128||timeout==null||timeout.isNegative()||timeout.isZero()||timeout.compareTo(Duration.ofSeconds(60))>0)
                    throw new IllegalArgumentException("Invalid bounded tokenizer configuration");
            }
        }
    }
    public static final class Result {
        private final String version,artifactHash,inputHash,outputHash;private final BigInteger count;
        private Result(String version,String artifactHash,String inputHash,String outputHash,BigInteger count){this.version=version;this.artifactHash=artifactHash;this.inputHash=inputHash;this.outputHash=outputHash;this.count=count;}
        public String tokenizerVersion(){return version;}public String executableSha256(){return artifactHash;}public String inputSha256(){return inputHash;}public String outputSha256(){return outputHash;}public BigInteger tokenCount(){return count;}
    }
    public FormalTokenizerRunner(Configuration config){this.config=Objects.requireNonNull(config);}
    public Result count(byte[] exactCanonicalInput,String tokenizerVersion,String expectedExecutableHash,BooleanSupplier cancelled)throws Exception {
        if(!config.enabled())fail(ErrorCode.DEPENDENCY_UNAVAILABLE);
        if(exactCanonicalInput==null||exactCanonicalInput.length==0||exactCanonicalInput.length>config.maximumInputBytes())fail(ErrorCode.CONTEXT_LIMIT);
        if(!config.tokenizerVersion().equals(tokenizerVersion)||!config.executableSha256().equals(expectedExecutableHash))fail(ErrorCode.CONTENT_MISMATCH);
        Objects.requireNonNull(cancelled);verifyExecutable();if(cancelled.getAsBoolean())fail(ErrorCode.LEASE_STALE);
        String inputHash=WireValues.sha256(exactCanonicalInput);var request=json.createObjectNode().put("schema_version","1.0.0").put("tokenizer_version",tokenizerVersion).put("input_sha256",inputHash).put("input_base64",Base64.getEncoder().encodeToString(exactCanonicalInput));
        Path executionDirectory=Files.createTempDirectory("formal-tokenizer-",java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")));
        Path executable=executionDirectory.resolve("tokenizer");
        byte[] executableBytes=Files.readAllBytes(config.executable());
        if(!expectedExecutableHash.equals(WireValues.sha256(executableBytes))){Files.deleteIfExists(executionDirectory);fail(ErrorCode.CONTENT_MISMATCH);}
        Files.write(executable,executableBytes,StandardOpenOption.CREATE_NEW);Files.setPosixFilePermissions(executable,java.nio.file.attribute.PosixFilePermissions.fromString("r-x------"));
        var builder=new ProcessBuilder(executable.toString());builder.directory(executionDirectory.toFile());builder.environment().clear();builder.redirectErrorStream(false);
        Process process;
        try {process=builder.start();}catch(Exception failure){Files.deleteIfExists(executable);Files.deleteIfExists(executionDirectory);throw failure;}var executor=Executors.newFixedThreadPool(3,r->{var t=new Thread(r,"formal-tokenizer-io");t.setDaemon(true);return t;});
        try {
            Future<byte[]> output=executor.submit(()->bounded(process.getInputStream(),config.maximumOutputBytes()));
            Future<byte[]> errors=executor.submit(()->bounded(process.getErrorStream(),Math.min(4096,config.maximumOutputBytes())));
            Future<?> input=executor.submit(()->{try(var stream=process.getOutputStream()){stream.write(json.writeValueAsBytes(request));}return null;});
            long deadline=System.nanoTime()+config.timeout().toNanos();
            while(!process.waitFor(20,TimeUnit.MILLISECONDS)) {
                if(cancelled.getAsBoolean())fail(ErrorCode.LEASE_STALE);
                if(System.nanoTime()>=deadline)fail(ErrorCode.CONTEXT_LIMIT);
                if(output.isDone())output.get();if(errors.isDone())errors.get();if(input.isDone())input.get();
            }
            if(cancelled.getAsBoolean())fail(ErrorCode.LEASE_STALE);
            if(System.nanoTime()>=deadline)fail(ErrorCode.CONTEXT_LIMIT);
            if(process.exitValue()!=0)fail(ErrorCode.DEPENDENCY_UNAVAILABLE);
            long remaining=Math.max(1,deadline-System.nanoTime());input.get(remaining,TimeUnit.NANOSECONDS);
            byte[] bytes=output.get(Math.max(1,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);errors.get(Math.max(1,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);
            JsonNode result=json.readTree(bytes);Set<String> fields=new HashSet<>();if(result!=null)result.fieldNames().forEachRemaining(fields::add);
            if(result==null||!result.isObject()||!fields.equals(Set.of("schema_version","tokenizer_version","input_sha256","token_count","complete"))
                    ||!"1.0.0".equals(result.path("schema_version").textValue())||!tokenizerVersion.equals(result.path("tokenizer_version").textValue())
                    ||!inputHash.equals(result.path("input_sha256").textValue())||!result.path("complete").isBoolean()||!result.path("complete").booleanValue()
                    ||!result.path("token_count").isTextual())fail(ErrorCode.CONTENT_MISMATCH);
            BigInteger count=WireValues.revision(result.required("token_count").textValue());verifyExecutable();
            return new Result(tokenizerVersion,expectedExecutableHash,inputHash,WireValues.sha256(bytes),count);
        } finally {
            process.toHandle().descendants().forEach(handle->{if(handle.isAlive())handle.destroyForcibly();});
            if(process.isAlive())process.destroyForcibly();executor.shutdownNow();
            Files.deleteIfExists(executable);Files.deleteIfExists(executionDirectory);
        }
    }
    /** Internal read-only discovery of the actual configured executable identity; never exposes its path or executes it. */
    PermissionProtocol.Basis configuredSourceBasis() throws Exception {
        if (!config.enabled()) fail(ErrorCode.DEPENDENCY_UNAVAILABLE);
        verifyExecutable();
        return new PermissionProtocol.Basis(config.tokenizerVersion(), config.executableSha256());
    }
    private void verifyExecutable()throws Exception {
        Path path=config.executable();if(Files.isSymbolicLink(path)||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||!Files.isExecutable(path))fail(ErrorCode.DEPENDENCY_UNAVAILABLE);
        var permissions=Files.getPosixFilePermissions(path,LinkOption.NOFOLLOW_LINKS);
        if(permissions.contains(PosixFilePermission.GROUP_WRITE)||permissions.contains(PosixFilePermission.OTHERS_WRITE))fail(ErrorCode.ACCESS_DENIED);
        if(Files.size(path)>64L*1024*1024)fail(ErrorCode.CONTEXT_LIMIT);
        if(!config.executableSha256().equals(WireValues.sha256(Files.readAllBytes(path))))fail(ErrorCode.CONTENT_MISMATCH);
    }
    private static byte[] bounded(InputStream input,int maximum)throws IOException {try(input;var out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=input.read(b))!=-1){if((long)out.size()+n>maximum)fail(ErrorCode.CONTEXT_LIMIT);out.write(b,0,n);}return out.toByteArray();}}
    private static void fail(ErrorCode code){throw new TenantAccessException(code);}
}

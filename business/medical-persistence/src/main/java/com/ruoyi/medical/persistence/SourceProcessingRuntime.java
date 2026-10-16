package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.commons.codec.digest.DigestUtils;

final class SourceProcessingRuntime {
    static void verify(SourceProcessingPolicy policy) throws Exception {
        try(var input=Files.newInputStream(policy.pythonExecutable().toRealPath())) {
            if(!policy.pythonSha256().equals(DigestUtils.sha256Hex(input))) mismatch();
        }
        var expected=new HashSet<String>();
        for(var file:policy.sourceFiles()) {
            Path path=policy.projectRoot().resolve(file.path());
            if(!path.equals(path.toRealPath()) || !Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)) mismatch();
            try(var input=Files.newInputStream(path)) { if(!file.sha256().equals(DigestUtils.sha256Hex(input))) mismatch(); }
            expected.add(file.path());
        }
        var actual=new HashSet<String>();
        for(String directory:List.of("document/src/medical_document","contracts/python/src/medical_contracts"))
            try(var paths=Files.walk(policy.projectRoot().resolve(directory))) {
                paths.filter(path -> path.toString().endsWith(".py")).forEach(path -> actual.add(policy.projectRoot().relativize(path).toString()));
            }
        if(!expected.equals(actual)) mismatch();
        for(var binding:policy.bindings())
            if(!binding.scannerReleaseSha256().equals(DigestUtils.sha256Hex(SourceProcessingResult.privateBytes(binding.scannerReleasePath(),262144)))) mismatch();
    }

    static int scan(SourceProcessingPolicy policy,Path command,Path result,Path work,S3ObjectReader.Admission admission) throws Exception {
        verify(policy); admission.requireCurrent();
        Path log=work.resolve("scanner.log");
        Files.createFile(log,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        var process=new ProcessBuilder(policy.pythonExecutable().toString(),"-m","medical_document.scan_command",command.toString(),result.toString());
        process.directory(work.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
        process.environment().clear();
        process.environment().put("PATH","/usr/bin:/bin"); process.environment().put("LANG","en_US.UTF-8");
        process.environment().put("LC_ALL","en_US.UTF-8"); process.environment().put("PYTHONNOUSERSITE","1");
        process.environment().put("PYTHONPYCACHEPREFIX",work.resolve("python-cache").toString());
        process.environment().put("OMP_NUM_THREADS","1"); process.environment().put("MKL_NUM_THREADS","1");
        process.environment().put("OPENBLAS_NUM_THREADS","1"); process.environment().put("TMPDIR",work.toString());
        process.environment().put("HF_HUB_OFFLINE","1"); process.environment().put("TRANSFORMERS_OFFLINE","1");
        process.environment().put("HF_HOME",work.resolve("model-cache").toString());
        process.environment().put("PYTHONPATH",policy.projectRoot().resolve("document/src")+java.io.File.pathSeparator
            +policy.projectRoot().resolve("contracts/python/src"));
        var running=process.start();
        try {
            while(!running.waitFor(policy.heartbeat().toMillis(),TimeUnit.MILLISECONDS)) admission.requireCurrent();
            admission.requireCurrent(); verify(policy); return running.exitValue();
        } finally {
            if(running.isAlive()) {
                running.destroyForcibly();
                if(!running.waitFor(10,TimeUnit.SECONDS)) throw new IllegalStateException("上传完成扫描进程尚未终止");
            }
        }
    }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}

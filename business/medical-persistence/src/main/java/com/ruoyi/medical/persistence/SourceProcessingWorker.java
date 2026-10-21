package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.SourceProcessingMapper;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.util.function.ThrowingSupplier;

final class SourceProcessingWorker {
    private final String owner=UUID.randomUUID().toString();
    private final SourceProcessingAccess access;
    private final SourceProcessingMapper mapper;
    private final PermissionMapper permissions;
    private final PermissionSchema json;
    private final SourceProcessingPolicy policy;
    private final SourceProcessingResult results;
    private final SourceUploadTransport transport;
    private final Path workspace;

    SourceProcessingWorker(SourceProcessingAccess access, SourceProcessingMapper mapper, PermissionMapper permissions,
            PermissionSchema json, SourceProcessingPolicy policy, SourceUploadTransport transport,
            SourceUploadTransportPolicy storage) throws Exception {
        this.access=access; this.mapper=mapper; this.permissions=permissions; this.json=json; this.policy=policy;
        this.transport=transport; results=new SourceProcessingResult();
        Path root=SourceProcessingResult.privateDirectory(storage.storageDirectory().toRealPath());
        workspace=Files.createDirectories(root.resolve("source-processing"),
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        SourceProcessingResult.privateDirectory(workspace); verifyRuntime();
    }

    List<Job> pending() {
        var jobs=new java.util.ArrayList<Job>();
        for(String tenant:policy.tenantIds()) {
            for(String attempt:access.pending(tenant)) {
                jobs.add(new Job(tenant,attempt));
                if(jobs.size()==100) return List.copyOf(jobs);
            }
        }
        return List.copyOf(jobs);
    }

    void run(Job job) { ThrowingSupplier.of(() -> { execute(job.tenant(),job.attempt()); return null; }).get(); }
    record Job(String tenant,String attempt) { }

    void execute(String tenant,String attempt) throws Exception {
        var row=claim(tenant,attempt); if(row==null) return;
        var guard=new Guard(tenant,attempt,row);
        Path work=Files.createTempDirectory(workspace,"parse-",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        try {
            guard.requireCurrent(); verifyRuntime();
            SourceProcessingResult.Verified parsed; ObjectRef reference;
            if(row.writeIntent()!=null) {
                // 原写入身份已经持久保存，恢复只查询原对象，租约更新不产生另一写入。
                JsonNode command=results.read(row.commandRef()),receipt=results.read(row.resultRef());
                verifyCommand(row,command);
                var intent=intent(results.read(row.writeIntent()));
                reference=transport.writeSourceJob(tenant,row.createdBy(),intent,null,work,true,policy.maximumBundleBytes(),guard);
                byte[] bytes=read(guard,reference,policy.maximumBundleBytes());
                parsed=results.verify(command,receipt,bytes);
            } else {
                String format=format(row.mediaType());
                Path original=work.resolve("original"+extension(format));
                save(original,read(guard,new ObjectRef(row.objectKey(),row.objectVersion(),row.originalSha256(),
                    row.sizeBytes().toString(),row.mediaType()),policy.maximumSourceBytes()));
                Path output=Files.createDirectory(work.resolve("output"),PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
                JsonNode command=command(guard.row,format,original,output);
                Path commandPath=work.resolve("command.json"),resultPath=work.resolve("result.json");
                save(commandPath,results.canonical(command).getBytes(StandardCharsets.UTF_8));
                int exit=run(commandPath,resultPath,work,guard);
                if(exit!=0) {
                    fail(guard,"PROCESSOR_EXIT",exit,false);
                    throw new IllegalStateException("来源处理进程返回失败，退出状态："+exit);
                }
                guard.requireCurrent(); verifyRuntime(); parsed=results.local(command,resultPath,policy.maximumBundleBytes());
                verifyCommand(guard.row,command);
                var intent=new S3ObjectWriter.WriteIntent(UUID.randomUUID().toString(),"tenants/"+tenant+"/source-parsed/"
                    +row.sourceVersion()+"/"+row.parseVersion()+"/parsed.json",parsed.receipt().required("bundle_sha256").textValue(),
                    parsed.receipt().required("bundle_size_bytes").textValue(),"application/json");
                guard.row=access.execute(tenant,attempt,admission -> {
                    guard.require(admission); var values=admission.values();
                    values.put("commandRef",results.canonical(command)); values.put("resultRef",results.canonical(parsed.receipt()));
                    values.put("writeIntent",results.canonical(json.tree(intent))); changed(mapper.saveWriteIntent(values));
                    return single(mapper.current(values));
                });
                reference=transport.writeSourceJob(tenant,row.createdBy(),intent,Path.of(parsed.receipt().required("bundle_path").textValue()),
                    output,false,policy.maximumBundleBytes(),guard);
            }
            guard.requireCurrent();
            if(!reference.sha256().equals(parsed.receipt().required("bundle_sha256").textValue())
                    || !reference.sizeBytes().equals(parsed.receipt().required("bundle_size_bytes").textValue())) mismatch();
            complete(guard,parsed,reference);
        } finally { removeWork(work); }
    }

    private SourceProcessingRow claim(String tenant,String attempt) {
        return access.execute(tenant,attempt,admission -> {
            var row=admission.row(); var values=admission.values();
            if(!List.of("QUEUED","RUNNING").contains(row.status())
                    || !List.of("QUEUED","PROCESSING").contains(row.parseStatus())) stale();
            if(row.leaseExpiresAt()!=null && row.leaseExpiresAt().isAfter(admission.now())) stale();
            if(row.leaseGeneration().compareTo(BigInteger.valueOf(2))>=0
                    || row.executionDeadline()!=null && !row.executionDeadline().isAfter(admission.now())) {
                values.put("expired",true); values.put("failureManifest",failure("PROCESSING_LIMIT",null));
                changed(mapper.failAttempt(values)); changed(mapper.failParse(values)); changed(mapper.advanceGovernance(values));
                event(admission,"source.processing.failed",json.tree(Map.of("code","PROCESSING_LIMIT"))); return null;
            }
            LocalDateTime deadline=row.executionDeadline()==null?admission.now().plus(policy.timeout()):row.executionDeadline();
            values.put("workerId",owner); values.put("deadline",deadline);
            values.put("leaseUntil",minimum(admission.now().plus(policy.lease()),deadline));
            changed(mapper.claim(values));
            if("QUEUED".equals(row.parseStatus())) changed(mapper.startParse(values));
            var claimed=single(mapper.current(values));
            if(!owner.equals(claimed.leaseOwner()) || !row.leaseGeneration().add(BigInteger.ONE).equals(claimed.leaseGeneration())
                    || !row.revision().add(BigInteger.ONE).equals(claimed.revision()) || !"PROCESSING".equals(claimed.parseStatus())) mismatch();
            event(admission,"source.processing.started",json.tree(Map.of("worker_id",owner,"lease_generation",claimed.leaseGeneration().toString())));
            return claimed;
        });
    }

    private JsonNode command(SourceProcessingRow row,String format,Path original,Path output) throws Exception {
        var binding=policy.binding(row.configRevision()); var command=json.object();
        command.put("schema_version","1.0.0"); command.put("attempt_id",row.attemptId()); command.put("run_id",row.runId());
        command.put("source_version",row.sourceVersion()); command.put("parse_version",row.parseVersion());
        command.put("config_revision",row.configRevision()); command.put("config_sha256",binding.configSha256());
        command.put("worker_id",owner); command.put("lease_generation",row.leaseGeneration().toString());
        if("HTML".equals(format)){if(!binding.htmlEnabled())mismatch();command.put("html_parser_revision","HTML-STATIC-TEXT-1");}
        command.put("format",format); command.put("original_sha256",row.originalSha256()); command.put("size_bytes",row.sizeBytes().toString());
        command.put("source_path",original.toString()); command.put("catalogue_root",policy.projectRoot().toRealPath().toString());
        command.put("scanner_release_path",binding.scannerReleasePath().toRealPath().toString());
        command.put("scanner_release_sha256",binding.scannerReleaseSha256()); command.put("output_directory",output.toString());
        if(binding.complexReleasePath()!=null) {
            command.put("complex_release_path",binding.complexReleasePath().toRealPath().toString());
            command.put("complex_release_sha256",binding.complexReleaseSha256());
        }
        return command;
    }

    private void verifyCommand(SourceProcessingRow row,JsonNode command) {
        var binding=policy.binding(row.configRevision());
        if(!format(row.mediaType()).equals(command.required("format").textValue()))mismatch();
        if("HTML".equals(command.required("format").textValue())) {if(!binding.htmlEnabled() || !"HTML-STATIC-TEXT-1".equals(command.path("html_parser_revision").textValue()))mismatch();}
        else if(command.has("html_parser_revision"))mismatch();
        if(!java.util.Objects.equals(binding.complexReleaseSha256(),command.path("complex_release_sha256").textValue())) mismatch();
        for(var entry:Map.of("attempt_id",row.attemptId(),"run_id",row.runId(),"source_version",row.sourceVersion(),
                "parse_version",row.parseVersion(),"config_revision",row.configRevision(),"config_sha256",binding.configSha256(),
                "original_sha256",row.originalSha256(),"size_bytes",row.sizeBytes().toString(),
                "scanner_release_sha256",binding.scannerReleaseSha256()).entrySet())
            if(!entry.getValue().equals(command.required(entry.getKey()).textValue())) mismatch();
    }

    private int run(Path command,Path result,Path work,Guard guard) throws Exception {
        Path log=work.resolve("processor.log"); Files.createFile(log,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        var process=new ProcessBuilder(policy.pythonExecutable().toString(),"-m","medical_document.processing_command",command.toString(),result.toString());
        process.directory(work.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
        process.environment().clear();
        process.environment().put("PATH","/usr/bin:/bin"); process.environment().put("LANG","en_US.UTF-8");
        process.environment().put("LC_ALL","en_US.UTF-8"); process.environment().put("PYTHONNOUSERSITE","1");
        process.environment().put("PYTHONPYCACHEPREFIX",work.resolve("python-cache").toString());
        process.environment().put("OMP_NUM_THREADS","1"); process.environment().put("MKL_NUM_THREADS","1");
        process.environment().put("OPENBLAS_NUM_THREADS","1");
        process.environment().put("TMPDIR",work.toString()); process.environment().put("HF_HUB_OFFLINE","1");
        process.environment().put("TRANSFORMERS_OFFLINE","1"); process.environment().put("HF_HOME",work.resolve("model-cache").toString());
        process.environment().put("PYTHONPATH",policy.projectRoot().resolve("document/src")+java.io.File.pathSeparator
            +policy.projectRoot().resolve("contracts/python/src"));
        var running=process.start();
        try {
            while(!running.waitFor(policy.heartbeat().toMillis(),TimeUnit.MILLISECONDS)) guard.requireCurrent();
            guard.requireCurrent(); return running.exitValue();
        } finally {
            if(running.isAlive()) {
                running.destroyForcibly();
                if(!running.waitFor(10,TimeUnit.SECONDS)) throw new IllegalStateException("来源处理进程尚未终止");
            }
        }
    }

    private byte[] read(Guard guard,ObjectRef reference,int maximum) throws Exception {
        if(!reference.objectKey().startsWith("tenants/"+guard.tenant+"/")) throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        return transport.readSourceJob(guard.tenant,guard.row.createdBy(),maximum,guard,(reader,admission) -> {
            try(var verified=reader.readVerified(reference,admission)) {
                byte[] bytes=new byte[new BigInteger(reference.sizeBytes()).intValueExact()];
                for(int start=0;start<bytes.length;) {
                    int length=Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,bytes.length-start);
                    System.arraycopy(verified.chunk(start,length),0,bytes,start,length); start+=length;
                }
                admission.requireCurrent(); return bytes;
            }
        });
    }

    private void complete(Guard guard,SourceProcessingResult.Verified result,ObjectRef object) {
        access.execute(guard.tenant,guard.attempt,admission -> {
            guard.require(admission); verifyRuntime(); var values=admission.values(); var bundle=result.bundle();
            if(mapper.nodeCount(values).signum()!=0 || mapper.chunkCount(values).signum()!=0) mismatch();
            values.put("bundleSha256",object.sha256()); values.put("schemaManifest",results.manifest()); changed(mapper.saveVersion(values));
            values.put("upstreamId",guard.row.sourceVersion()); values.put("relationKind","PARSE_INPUT"); changed(mapper.saveLineage(values));
            values.put("upstreamId",guard.row.configRevision()); values.put("relationKind","PARSE_CONFIGURATION"); changed(mapper.saveLineage(values));
            for(int ordinal=0;ordinal<bundle.required("nodes").size();ordinal++) {
                var node=bundle.required("nodes").get(ordinal); var chunk=bundle.required("chunks").get(ordinal);
                values.put("nodeId",node.required("node_id").textValue()); values.put("ordinal",ordinal);
                values.put("nodeKind",node.required("node_kind").textValue()); values.put("locator",results.canonical(node.required("locator")));
                var content=json.object(); content.set("object_ref",json.tree(object)); content.put("node_id",node.required("node_id").textValue());
                content.put("ordinal",ordinal); content.put("quote_hash",node.required("quote_hash").textValue()); content.set("heading_path",node.required("heading_path"));
                content.set("structure",nodeStructure(bundle,node));
                if(bundle.required("processor_manifest").has("complex_engine_release_sha256"))
                    content.set("complex_engine_release_sha256",bundle.required("processor_manifest").required("complex_engine_release_sha256"));
                values.put("contentRef",results.canonical(content)); values.put("nodeHash",DigestUtils.sha256Hex(results.canonical(node)));
                values.put("chunkId",chunk.required("chunk_id").textValue()); values.put("quoteHash",chunk.required("quote_hash").textValue());
                values.put("rawText",chunk.required("raw_text").textValue()); values.put("searchText",chunk.required("search_text").textValue());
                var range=json.object(); range.put("char_start",0); range.put("char_end",chunk.required("raw_text").textValue().codePointCount(0,chunk.required("raw_text").textValue().length()));
                values.put("range",results.canonical(range)); changed(mapper.saveNode(values)); changed(mapper.saveChunk(values)); changed(mapper.saveChunkNode(values));
            }
            values.put("artifactKey",object.objectKey()); values.put("artifactVersion",object.objectVersion()); values.put("artifactSizeBytes",object.sizeBytes());
            values.put("parseStatus",bundle.required("status").textValue()); values.put("processorManifest",results.canonical(bundle.required("processor_manifest")));
            var failures=json.object(); failures.put("schema_version","1.0.0"); failures.set("issues",bundle.required("issues"));
            values.put("failureManifest",results.canonical(failures)); changed(mapper.completeParse(values));
            values.put("fetchLimit",bundle.required("nodes").size()+1); verifyStored(values,result,object);
            // 完整解析与存在缺项分别保存，人工核准继续由来源治理决定。
            values.put("attemptStatus",bundle.required("issues").isEmpty()?"SUCCEEDED":"FAILED");
            var receipt=result.receipt().deepCopy(); ((ObjectNode)receipt).set("object_ref",json.tree(object));
            ((ObjectNode)receipt).put("confirmed_by_worker",owner); ((ObjectNode)receipt).put("confirmed_lease_generation",guard.row.leaseGeneration().toString());
            values.put("resultRef",results.canonical(receipt)); changed(mapper.completeAttempt(values)); changed(mapper.advanceGovernance(values));
            event(admission,"source.processing.completed",receipt);
            if(bundle.required("issues").isEmpty() && "PARSE_AND_INDEX".equals(results.read(guard.row.requestManifest()).required("stage").textValue())) {
                var request=json.object(); request.put("schema_version","1.0.0"); request.put("source_version",guard.row.sourceVersion());
                request.put("parse_version",guard.row.parseVersion()); request.put("content_sha256",object.sha256());
                request.put("config_revision",admission.binding().indexConfigRevision());
                request.put("parse_config_revision",guard.row.configRevision()); request.put("project_id",guard.row.projectId());
                request.put("requested_by",guard.row.createdBy());
                changed(permissions.saveEvent(guard.row.createdBy(),UUID.randomUUID().toString(),guard.row.parseVersion(),"1",
                    "source.index.requested",results.canonical(request)));
            }
            var after=single(mapper.current(values));
            if(!values.get("attemptStatus").equals(after.status()) || !values.get("parseStatus").equals(after.parseStatus())
                    || after.leaseOwner()!=null || after.leaseExpiresAt()!=null) mismatch();
            return null;
        });
    }

    private void verifyStored(Map<String,Object> values,SourceProcessingResult.Verified result,ObjectRef object) throws Exception {
        var nodes=result.bundle().required("nodes"); var chunks=result.bundle().required("chunks");
        if(!BigInteger.valueOf(nodes.size()).equals(mapper.nodeCount(values)) || !BigInteger.valueOf(chunks.size()).equals(mapper.chunkCount(values))) mismatch();
        var materialized=single(mapper.materialized(values));
        if(!object.sha256().equals(materialized.get("contentSha256")) || !object.sha256().equals(materialized.get("registeredSha256"))
                || !object.objectKey().equals(materialized.get("artifactKey")) || !object.objectVersion().equals(materialized.get("artifactVersion"))
                || !object.sizeBytes().equals(materialized.get("artifactSizeBytes").toString())
                || !result.bundle().required("status").textValue().equals(materialized.get("parseStatus"))) mismatch();
        var stored=mapper.storedNodes(values); if(stored.size()!=nodes.size()) mismatch();
        for(int ordinal=0;ordinal<nodes.size();ordinal++) {
            var node=nodes.get(ordinal); var chunk=chunks.get(ordinal); var current=stored.get(ordinal);
            if(!node.required("node_id").textValue().equals(current.get("nodeId"))
                    || !chunk.required("chunk_id").textValue().equals(current.get("chunkId"))
                    || !Integer.toString(ordinal).equals(current.get("ordinal").toString())
                    || !Integer.toString(ordinal).equals(current.get("chunkOrdinal").toString())
                    || !node.required("node_kind").textValue().equals(current.get("nodeKind"))
                    || !node.required("raw_text").textValue().equals(current.get("rawText"))
                    || !chunk.required("search_text").textValue().equals(current.get("searchText"))
                    || !node.required("quote_hash").textValue().equals(current.get("quoteHash"))
                    || !node.required("locator").equals(results.read((String)current.get("locator")))
                    || !node.required("locator").equals(results.read((String)current.get("chunkLocator")))
                    || !DigestUtils.sha256Hex(results.canonical(node)).equals(current.get("nodeHash"))) mismatch();
            var content=results.read((String)current.get("contentRef"));
            if(!json.tree(object).equals(content.required("object_ref")) || !node.required("heading_path").equals(content.required("heading_path"))
                    || !nodeStructure(result.bundle(),node).equals(content.required("structure"))) mismatch();
        }
    }

    private void fail(Guard guard,String code,Integer exit,boolean expired) {
        access.execute(guard.tenant,guard.attempt,admission -> {
            guard.require(admission); var values=admission.values(); values.put("expired",expired);
            values.put("failureManifest",failure(code,exit)); changed(mapper.failAttempt(values)); changed(mapper.failParse(values));
            changed(mapper.advanceGovernance(values)); event(admission,"source.processing.failed",results.read((String)values.get("failureManifest"))); return null;
        });
    }
    private String failure(String code,Integer exit) throws Exception {
        var manifest=json.object(); manifest.put("schema_version","1.0.0"); var issue=manifest.putArray("issues").addObject();
        issue.put("code",code); if(exit!=null) issue.put("exit_code",exit); issue.putNull("locator"); return results.canonical(manifest);
    }

    private void event(SourceProcessingAccess.Admission admission,String type,JsonNode result) throws Exception {
        var event=json.object(); event.put("schema_version","1.0.0"); event.put("attempt_id",admission.row().attemptId());
        event.put("source_version",admission.row().sourceVersion()); event.put("parse_version",admission.row().parseVersion());
        event.put("run_id",admission.row().runId()); event.set("result",result);
        String revision=admission.row().revision().add(BigInteger.ONE).toString();
        changed(permissions.saveAudit(admission.row().createdBy(),admission.row().projectId(),UUID.randomUUID().toString(),type,
            results.canonical(event),UUID.randomUUID().toString(),results.manifest(),admission.row().classification()));
        changed(permissions.saveEvent(admission.row().createdBy(),UUID.randomUUID().toString(),admission.row().attemptId(),revision,type,results.canonical(event)));
    }

    private void verifyRuntime() throws Exception {
        SourceProcessingRuntime.verify(policy);
    }

    private final class Guard implements S3ObjectReader.Admission {
        private final String tenant,attempt; private SourceProcessingRow row;
        Guard(String tenant,String attempt,SourceProcessingRow row) { this.tenant=tenant; this.attempt=attempt; this.row=row; }
        @Override public void requireCurrent() {
            row=access.execute(tenant,attempt,admission -> {
                require(admission); var values=admission.values();
                values.put("leaseUntil",minimum(admission.now().plus(policy.lease()),row.executionDeadline()));
                changed(mapper.renew(values)); return single(mapper.current(values));
            });
        }
        void require(SourceProcessingAccess.Admission admission) {
            if(!"RUNNING".equals(admission.row().status()) || !owner.equals(admission.row().leaseOwner())
                    || !row.equals(admission.row()) || admission.row().leaseExpiresAt()==null
                    || !admission.row().leaseExpiresAt().isAfter(admission.now())
                    || !admission.row().executionDeadline().isAfter(admission.now())) stale();
        }
    }

    private static S3ObjectWriter.WriteIntent intent(JsonNode value) {
        return new S3ObjectWriter.WriteIntent(value.required("write_id").textValue(),value.required("object_key").textValue(),
            value.required("sha256").textValue(),value.required("size_bytes").textValue(),value.required("media_type").textValue());
    }
    private JsonNode nodeStructure(JsonNode bundle,JsonNode node) {
        var structures=json.object().putArray("items");
        var processor=bundle.required("processor_manifest");
        for(String name:List.of("complex_structure","table_structure","embedded_image_provenance")) {
            var entries=processor.path(name);
            if(entries.isArray()) for(var entry:entries) {
                if(node.required("node_id").equals(entry.path("node_id")) || node.required("locator").equals(entry.path("locator")))
                    structures.add(entry.deepCopy());
            }
        }
        return structures;
    }
    private static String format(String media) {
        return switch(media) {
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "DOCX";
            case "application/pdf" -> "PDF";
            case "text/html" -> "HTML";
            case "image/png", "image/jpeg", "image/tiff" -> "IMAGE";
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "XLSX";
            case "text/csv" -> "CSV";
            case "text/plain" -> "TXT";
            case "text/markdown" -> "MARKDOWN";
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "PPTX";
            default -> throw new TenantAccessException(ErrorCode.FILE_TYPE_UNSUPPORTED);
        };
    }
    private static String extension(String format) { return "MARKDOWN".equals(format)?".md":"."+format.toLowerCase(java.util.Locale.ROOT); }
    private static LocalDateTime minimum(LocalDateTime first,LocalDateTime second) { return first.isBefore(second)?first:second; }
    private static void save(Path path,byte[] bytes) throws Exception {
        Files.createFile(path,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Files.write(path,bytes,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
    }
    private static void removeWork(Path directory) throws Exception {
        Files.walkFileTree(directory,new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attributes) throws java.io.IOException { Files.delete(path); return FileVisitResult.CONTINUE; }
            @Override public FileVisitResult postVisitDirectory(Path path,java.io.IOException error) throws java.io.IOException {
                if(error!=null) throw error; Files.delete(path); return FileVisitResult.CONTINUE;
            }
        });
    }
    private static <T> T single(List<T> rows) { if(rows.size()!=1) mismatch(); return rows.get(0); }
    private static void changed(int count) { if(count!=1) stale(); }
    private static void stale() { throw new TenantAccessException(ErrorCode.LEASE_STALE); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}

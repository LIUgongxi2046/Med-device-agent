package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.function.ThrowingSupplier;

/** Reads real original and parsed bytes before minting a mapping-bound receipt. No worker impersonation. */
@Component
public final class TaskFieldSourceReader {
    private final EnterpriseAccess access;
    private final TaskFieldSourceAuthority authority;
    private final TaskFieldSourceReceiptStore receipts;
    private final SourceUploadTransport transport;
    private final TaskFieldTargetPolicy policy;
    private final SourceExactSchema exact;
    private final SourceProcessingResult processing;
    private final PermissionSchema json;

    public TaskFieldSourceReader(EnterpriseAccess access,TaskFieldSourceAuthority authority,TaskFieldSourceReceiptStore receipts,
            SourceUploadTransport transport,TaskFieldTargetPolicy policy,SourceExactSchema exact,PermissionSchema json) throws Exception {
        this.access=access;this.authority=authority;this.receipts=receipts;this.transport=transport;this.policy=policy;this.exact=exact;this.json=json;
        processing=new SourceProcessingResult();
    }

    public Read read(ApplicationSessionIdentity.Verified subject,String context,TaskFieldSourceAuthority.Request request) throws Exception {
        Objects.requireNonNull(subject); Objects.requireNonNull(request);
        if(TransactionSynchronizationManager.isActualTransactionActive()) fail(ErrorCode.ACCESS_DENIED);
        var initial=access.execute(subject,a -> ThrowingSupplier.of(() -> authority.require(a,context,request)).get());
        var existing=access.execute(subject,a -> ThrowingSupplier.of(() -> {
            var current=authority.require(a,context,request); authority.requireSame(initial,current); return receipts.find(a,current,null);
        }).get());
        if(existing!=null) return result(subject,context,initial,existing);
        S3ObjectReader.Admission current=() -> access.execute(subject,a -> ThrowingSupplier.of(() -> {
            authority.requireSame(initial,authority.require(a,context,request)); return null;
        }).get());
        var physical=initial.processing();
        var original=transport.readDocument(subject,initial.task().tenantId(),policy.maximumOriginalBytes(),current,(reader,lease) -> {
            var guard=guard(current,lease);
            try(var bytes=reader.readVerified(physical.original(),guard)) {
                // Hash every delivered physical byte; PDF/DOCX bytes are never decoded as substring text.
                return consume(bytes,policy.maximumOriginalBytes(),false,guard);
            }
        });
        var parsed=transport.readDocument(subject,initial.task().tenantId(),policy.maximumParsedBytes(),current,(reader,lease) -> {
            var guard=guard(current,lease);
            try(var bytes=reader.readVerified(physical.parsed(),guard)) {
                return consume(bytes,policy.maximumParsedBytes(),true,guard);
            }
        });
        verifyParsed(physical,initial.field(),parsed.bytes()); current.requireCurrent();
        var delivered=new Bytes(initial,original.sha256(),original.length(),parsed.sha256(),parsed.length());
        var stored=access.execute(subject,a -> ThrowingSupplier.of(() -> {
            var fresh=authority.require(a,context,request); authority.requireSame(initial,fresh);
            var value=receipts.save(a,fresh,delivered); authority.requireSame(fresh,authority.require(a,context,request)); return value;
        }).get());
        return result(subject,context,initial,stored);
    }

    /** Used by target registration inside its own current authenticated transaction. */
    void requireCurrent(EnterpriseAccess.Admission admission,String context,Read read) throws Exception {
        Objects.requireNonNull(read);
        var current=authority.require(admission,context,read.request);
        var stored=receipts.find(admission,current,read.receiptId());
        if(stored==null || !stored.sha256().equals(read.receiptSha256()) || !stored.manifest().equals(read.manifest)) fail(ErrorCode.CONTENT_MISMATCH);
    }

    private Read result(ApplicationSessionIdentity.Verified subject,String context,TaskFieldSourceAuthority.Proof proof,
            TaskFieldSourceReceiptStore.Stored receipt) {
        Runnable recheck=() -> access.execute(subject,a -> ThrowingSupplier.of(() -> {
            var current=authority.require(a,context,proof.request()); var actual=receipts.find(a,current,receipt.id());
            if(actual==null || !actual.sha256().equals(receipt.sha256()) || !actual.manifest().equals(receipt.manifest())) fail(ErrorCode.CONTENT_MISMATCH);
            return null;
        }).get());
        return new Read(proof.request(),proof.field(),receipt,recheck);
    }

    static S3ObjectReader.TimedAdmission guard(S3ObjectReader.Admission current,S3ObjectReader.TimedAdmission lease) {
        Objects.requireNonNull(current);Objects.requireNonNull(lease);
        return new S3ObjectReader.TimedAdmission() {
            @Override public void requireCurrent(){current.requireCurrent();lease.requireCurrent();}
            @Override public Duration remainingTime(){requireCurrent();return lease.remainingTime();}
        };
    }

    private static Delivered consume(S3ObjectReader.VerifiedObject object,int maximum,boolean keep,S3ObjectReader.Admission guard) throws Exception {
        int size=WireValues.revision(object.reference().sizeBytes()).intValueExact();
        if(size<1 || size>maximum) fail(ErrorCode.CONTEXT_LIMIT);
        var digest=java.security.MessageDigest.getInstance("SHA-256"); byte[] all=keep?new byte[size]:null;
        for(int start=0;start<size;) {
            guard.requireCurrent(); int length=Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,size-start); byte[] segment=object.chunk(start,length);
            digest.update(segment); if(keep)System.arraycopy(segment,0,all,start,length);start+=length;guard.requireCurrent();
        }
        String hash=java.util.HexFormat.of().formatHex(digest.digest());
        if(!object.fullSha256().equals(hash) || !object.reference().sha256().equals(hash)) fail(ErrorCode.CONTENT_MISMATCH);
        return new Delivered(hash,size,all);
    }

    /** The processing receipt attests parsing provenance, not original-file UTF-8 text. */
    void verifyParsed(TaskFieldSourceAuthority.Processing expected,SourceExactProtocol.Field field,byte[] bytes) throws Exception {
        if(bytes==null || !WireValues.sha256(bytes).equals(expected.parsed().sha256())
                || !Integer.toString(bytes.length).equals(expected.parsed().sizeBytes())) fail(ErrorCode.CONTENT_MISMATCH);
        var receipt=(ObjectNode)expected.result();
        if(!json.tree(expected.parsed()).equals(receipt.required("object_ref"))) fail(ErrorCode.CONTENT_MISMATCH);
        receipt.remove(List.of("object_ref","confirmed_by_worker","confirmed_lease_generation"));
        var verified=processing.verify(expected.command(),receipt,bytes); var bundle=verified.bundle();
        if(!"COMPLETE".equals(bundle.required("status").textValue()) || !bundle.required("issues").isEmpty()
                || !expected.attempt().equals(bundle.required("attempt_id").textValue())
                || !expected.original().sha256().equals(bundle.required("original_sha256").textValue())
                || !expected.processorManifest().equals(bundle.required("processor_manifest"))) fail(ErrorCode.CONTENT_MISMATCH);
        verifyLiteral(exact,json,field,bundle);
    }

    static void verifyLiteral(SourceExactSchema exact,PermissionSchema json,SourceExactProtocol.Field field,JsonNode bundle) throws Exception {
        JsonNode selected=null;
        for(var chunk:bundle.required("chunks")) if(field.chunk_id().equals(chunk.required("chunk_id").textValue())) {
            if(selected!=null) fail(ErrorCode.CONTENT_MISMATCH); selected=chunk;
        }
        if(selected==null) fail(ErrorCode.CONTENT_MISMATCH);
        exact.verifyField(field,selected.required("chunk_id").textValue(),selected.required("raw_text").textValue(),
                selected.required("quote_hash").textValue(),json.canonical(selected.required("locator")));
    }

    private record Delivered(String sha256,int length,byte[] bytes) { }

    /** Only read() can create the proof accepted by the persistence boundary. */
    static final class Bytes {
        private final TaskFieldSourceAuthority.Proof authority;
        private final String originalSha256,parsedSha256; private final int originalLength,parsedLength;
        private Bytes(TaskFieldSourceAuthority.Proof authority,String originalHash,int originalLength,String parsedHash,int parsedLength) {
            this.authority=authority;this.originalSha256=originalHash;this.originalLength=originalLength;
            this.parsedSha256=parsedHash;this.parsedLength=parsedLength;
        }
        void require(TaskFieldSourceAuthority.Proof current) {
            if(!authority.authoritySha256().equals(current.authoritySha256()) || !authority.authority().equals(current.authority())
                    || !originalSha256.equals(current.processing().original().sha256()) || !parsedSha256.equals(current.processing().parsed().sha256())
                    || !Integer.toString(originalLength).equals(current.processing().original().sizeBytes())
                    || !Integer.toString(parsedLength).equals(current.processing().parsed().sizeBytes())) fail(ErrorCode.CONTENT_MISMATCH);
        }
    }

    public static final class Read {
        private final TaskFieldSourceAuthority.Request request; private final SourceExactProtocol.Field field;
        private final String id,sha256; private final JsonNode manifest; private final Runnable current;
        private Read(TaskFieldSourceAuthority.Request request,SourceExactProtocol.Field field,TaskFieldSourceReceiptStore.Stored receipt,Runnable current) {
            this.request=request;this.field=field;id=receipt.id();sha256=receipt.sha256();manifest=receipt.manifest();this.current=current;
        }
        public String receiptId(){return id;} public String receiptSha256(){return sha256;}
        public JsonNode manifest(){return manifest.deepCopy();} public SourceExactProtocol.Field field(){return field;}
        public String rawValue(){return field.raw_value();}
        public TaskFieldSourceAuthority.Request request(){return request;}
        public void requireCurrent(){current.run();}
    }
    private static void fail(ErrorCode code){throw new TenantAccessException(code);}
}

package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.WireValues;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Pure fixtures only: no service, secrets, registered source or real approvals. */
class TaskFieldSourceProvenanceTest {
    PermissionSchema json;
    SourceExactSchema exact;
    TaskFieldSourceReader reader;
    ObjectNode command,bundle,result,locator;
    ObjectRef original,parsed;
    byte[] bytes;
    SourceExactProtocol.Field field;
    final String raw="prefix😀 AB 007 suffix";
    static String id(int n){return String.format("00000000-0000-4000-8000-%012d",n);}
    static String hash(String value){return org.apache.commons.codec.digest.DigestUtils.sha256Hex(value.getBytes(StandardCharsets.UTF_8));}
    static TaskFieldTargetPolicy policy(){return new TaskFieldTargetPolicy(100,100,1048576,1048576,1048576,"LOCAL","LOCAL");}

    @BeforeEach void fixture() throws Exception {
        json=new PermissionSchema();exact=new SourceExactSchema(json);
        reader=new TaskFieldSourceReader(null,null,null,null,policy(),exact,json);
        // Original binary is deliberately not UTF-8 and does not contain the literal extracted field.
        byte[] originalBytes=new byte[]{'%', 'P', 'D', 'F', '-', '1', '.', '7', (byte)0xff, (byte)0xfe};
        original=new ObjectRef("tenants/"+id(1)+"/source-uploads/"+id(11)+"/original","original-v1",WireValues.sha256(originalBytes),Integer.toString(originalBytes.length),"application/pdf");
        command=json.object().put("schema_version","1.0.0").put("attempt_id",id(2)).put("run_id",id(3)).put("source_version",id(4))
            .put("parse_version",id(5)).put("config_revision",id(6)).put("config_sha256",hash("config")).put("worker_id",id(7))
            .put("lease_generation","1").put("format","PDF").put("original_sha256",original.sha256()).put("size_bytes",original.sizeBytes())
            .put("source_path","/synthetic/original.pdf").put("catalogue_root","/synthetic/catalogue")
            .put("scanner_release_path","/synthetic/scanner").put("scanner_release_sha256",hash("scanner")).put("output_directory","/synthetic/output");
        bundle=json.object();result=json.object();
        for(String name:List.of("schema_version","attempt_id","run_id","source_version","parse_version","config_revision","config_sha256","worker_id","lease_generation","original_sha256")) {
            bundle.set(name,command.required(name));result.set(name,command.required(name));
        }
        bundle.put("command_sha256",hash(json.canonical(command))).put("status","COMPLETE").put("format","PDF");
        result.put("command_sha256",hash(json.canonical(command))).put("status","COMPLETE").put("bundle_path","/synthetic/output/parsed.json")
            .put("node_count",1).put("chunk_count",1).put("issue_count",0);
        var processor=bundle.putObject("processor_manifest").put("config_revision",id(6)).put("config_sha256",hash("config"))
            .put("scanner_release_sha256",hash("scanner")).put("parameters_json","{}").put("parameters_sha256",hash("{}"))
            .put("network_enabled",false).put("data_kind","REGISTERED").put("chunking_revision","SOURCE-NODE-CHUNKS-1")
            .put("locator_schema_sha256",SchemaCatalogue.fromClasspath().schemaDigest("dataset"));
        locator=json.object().put("kind","PDF").put("page_index",0).put("page_basis","ORIGINAL").putNull("printed_label");
        locator.putObject("region").put("kind","WHOLE_PAGE");
        var node=bundle.putArray("nodes").addObject().put("node_id",id(8)).put("ordinal",0).put("node_kind","TEXT")
            .put("raw_text",raw).put("quote_hash",hash(raw));node.putArray("heading_path");node.set("locator",locator);
        var chunk=bundle.putArray("chunks").addObject().put("chunk_id",id(9)).put("node_id",id(8)).put("ordinal",0)
            .put("raw_text",raw).put("search_text",raw).put("quote_hash",hash(raw));chunk.set("locator",locator);
        bundle.putArray("issues");var scan=bundle.putObject("scan").put("original_sha256",original.sha256()).put("size_bytes",original.sizeBytes())
            .put("release_sha256",hash("scanner")).put("returncode",0);
        var artifacts=scan.putArray("artifacts");for(String name:List.of("scan.txt","signature-0.txt","signature-1.txt","signature-2.txt"))
            artifacts.addObject().put("name",name).put("sha256",hash("clean")).put("size_bytes","5").put("content_base64","Y2xlYW4=");
        field=new SourceExactProtocol.Field("MODEL","AB 007","AB007","Explicit approved alias",id(9),hash(raw),hash(json.canonical(locator)),8,14);
        refresh();
    }
    void refresh() throws Exception {
        bytes=json.canonical(bundle).getBytes(StandardCharsets.UTF_8);
        parsed=new ObjectRef("tenants/"+id(1)+"/source-parsed/"+id(4)+"/"+id(5)+"/parsed.json","parsed-v1",WireValues.sha256(bytes),Integer.toString(bytes.length),"application/json");
        result.put("bundle_sha256",parsed.sha256()).put("bundle_size_bytes",parsed.sizeBytes());result.set("object_ref",json.tree(parsed));
        result.put("confirmed_by_worker",id(7)).put("confirmed_lease_generation","1");
    }
    TaskFieldSourceAuthority.Processing processing(){return new TaskFieldSourceAuthority.Processing(id(10),id(2),command,result,original,parsed,bundle.required("processor_manifest"));}
    void verify() throws Exception {reader.verifyParsed(processing(),field,bytes);}
    @Test void binaryPdfUsesVerifiedParserProvenanceNotUtf8Substring() throws Exception {verify();assertEquals("AB 007",field.raw_value());assertNotEquals(field.alias_value(),field.raw_value());}
    @Test void allFiveLiteralKindsUseTheSameActualSubstringWithoutInferringMapping() throws Exception {
        for(String kind:List.of("MODEL","PTR_NUMBER","DOCUMENT_NUMBER","STANDARD_NUMBER","STANDARD_YEAR")) {
            field=new SourceExactProtocol.Field(kind,field.raw_value(),field.alias_value(),field.alias_reason(),field.chunk_id(),field.quote_hash(),field.locator_sha256(),field.start_codepoint(),field.end_codepoint());verify();
        }
    }
    @Test void aliasCannotReplaceOriginalLiteral() {
        field=new SourceExactProtocol.Field("MODEL","AB007",null,null,id(9),hash(raw),field.locator_sha256(),8,14);
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,this::verify).code());
    }
    @Test void utf16OffsetsCannotReplaceUnicodeCodepointRange() {
        field=new SourceExactProtocol.Field("MODEL","AB 007",null,null,id(9),hash(raw),field.locator_sha256(),9,15);
        assertThrows(TenantAccessException.class,this::verify);
    }
    @Test void changedLocatorCannotBorrowAnExistingQuote() throws Exception {
        ((ObjectNode)bundle.required("chunks").get(0).required("locator")).put("page_index",2);refresh();
        assertThrows(TenantAccessException.class,this::verify);
    }
    @Test void rehashedChangedTextCannotBorrowSourceExact() throws Exception {
        for(var item:List.of((ObjectNode)bundle.required("nodes").get(0),(ObjectNode)bundle.required("chunks").get(0))) {
            item.put("raw_text",raw.replace("007","008")).put("quote_hash",hash(raw.replace("007","008")));
            if(item.has("search_text"))item.put("search_text",raw.replace("007","008"));
        }
        refresh();assertThrows(TenantAccessException.class,this::verify);
    }
    @Test void changedOriginalObjectCannotBorrowParserBundle() {
        original=new ObjectRef(original.objectKey(),original.objectVersion(),hash("another-original"),original.sizeBytes(),original.mediaType());
        assertThrows(TenantAccessException.class,this::verify);
    }
    @Test void alteredCommandEvenWithSameSourceCannotBorrowProcessingReceipt() {
        command.put("config_sha256",hash("different-config"));assertThrows(TenantAccessException.class,this::verify);
    }
    @Test void duplicateChunksAndMissingChunksFailClosed() throws Exception {
        var chunks=(com.fasterxml.jackson.databind.node.ArrayNode)bundle.required("chunks");chunks.add(chunks.get(0).deepCopy());refresh();
        assertThrows(TenantAccessException.class,this::verify);chunks.removeAll();refresh();assertThrows(TenantAccessException.class,this::verify);
    }
    @Test void canonicalEquivalentButDifferentBytesAreNotAccepted() {
        bytes=(new String(bytes,StandardCharsets.UTF_8)+"\n").getBytes(StandardCharsets.UTF_8);assertThrows(TenantAccessException.class,this::verify);
    }
    @Test void guardRechecksDataAuthorityEvenWhenLeaseIsStillLive() {
        var allowed=new AtomicBoolean(true);var calls=new AtomicInteger();
        S3ObjectReader.Admission current=()->{calls.incrementAndGet();if(!allowed.get())throw new TenantAccessException(ErrorCode.ACCESS_DENIED);};
        var lease=new S3ObjectReader.TimedAdmission(){public void requireCurrent(){}public Duration remainingTime(){return Duration.ofSeconds(5);}};
        var guard=TaskFieldSourceReader.guard(current,lease);guard.requireCurrent();assertEquals(Duration.ofSeconds(5),guard.remainingTime());
        allowed.set(false);assertThrows(TenantAccessException.class,guard::requireCurrent);assertThrows(TenantAccessException.class,guard::remainingTime);assertEquals(4,calls.get());
    }
    @Test void privateProofsCannotBeMintedThroughAnExternalConstructor() {
        for(Class<?> type:List.of(TaskFieldSourceAuthority.Proof.class,TaskFieldSourceReader.Bytes.class,TaskFieldSourceReader.Read.class,TaskFieldSourceReceiptStore.Stored.class))
            for(var constructor:type.getDeclaredConstructors())assertTrue(Modifier.isPrivate(constructor.getModifiers()),type.getName());
    }
    @Test void runtimeWorkerCannotImpersonateAuthenticatedPreparation() throws Exception {
        var authority=new TaskFieldSourceAuthority(null,null,null,null,null,exact,new SourceExactPolicy(100,100),new TaskFieldTargetSchema(),policy(),json,mock(DataSource.class));
        var admission=mock(EnterpriseAccess.Admission.class);when(admission.formalWorker()).thenReturn(true);
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->authority.require(admission,"1",new TaskFieldSourceAuthority.Request(id(1),id(2),hash("mapping"),id(3)))).code());
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->authority.require(admission,"1",new TaskFieldSourceAuthority.Request(id(1),id(2),hash("mapping"),id(3)),"plan.write")).code());
    }
    @Test void mutableProcessingJsonCannotChangeAnAlreadyCapturedProof() {
        var saved=processing();command.put("format","TXT");result.put("bundle_sha256",hash("changed"));
        assertEquals("PDF",saved.command().required("format").textValue());
        ((ObjectNode)saved.result()).put("bundle_sha256",hash("again"));assertEquals(parsed.sha256(),saved.result().required("bundle_sha256").textValue());
    }
}

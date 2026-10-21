package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import org.springframework.util.function.ThrowingSupplier;
import static com.ruoyi.medical.persistence.TaskRegisteredToolAuthority.*;

/** AT-03/04 bytes come only from actual governed GET proofs. AT-08 writes bounded
 * waiting work. None of these adapters invokes a model, widens a source scope, or resumes work. */
public final class TaskRegisteredToolBridge {
    private ApprovedWebAcquisitionService web;
    @org.springframework.beans.factory.annotation.Autowired void approvedWeb(ApprovedWebAcquisitionService web){this.web=web;}
    private final TaskProfessionalHostAccess hosts;private final TaskProfessionalSupervisor supervisors;
    private final TaskInvocationService invocations;private final TaskRegisteredToolAuthority authority;
    private final TaskRegisteredToolReceipts receipts;private final TaskProfessionalPredecessorEvidence predecessors;
    private final TaskInvocationPackages packages;private final TaskRegisteredToolWaitingService waiting;
    private final PermissionSchema json;private final SchemaCatalogue catalogue;private final TaskRegisteredToolWaitingGate waitingGate;
    public TaskRegisteredToolBridge(TaskProfessionalHostAccess hosts,TaskProfessionalSupervisor supervisors,
            TaskInvocationService invocations,TaskRegisteredToolAuthority authority,TaskRegisteredToolReceipts receipts,
            TaskProfessionalPredecessorEvidence predecessors,TaskInvocationPackages packages,
            TaskRegisteredToolWaitingService waiting,PermissionSchema json)throws Exception {
        this(hosts,supervisors,invocations,authority,receipts,predecessors,packages,waiting,json,null);
    }
    public TaskRegisteredToolBridge(TaskProfessionalHostAccess hosts,TaskProfessionalSupervisor supervisors,
            TaskInvocationService invocations,TaskRegisteredToolAuthority authority,TaskRegisteredToolReceipts receipts,
            TaskProfessionalPredecessorEvidence predecessors,TaskInvocationPackages packages,
            TaskRegisteredToolWaitingService waiting,PermissionSchema json,TaskRegisteredToolWaitingGate waitingGate)throws Exception {
        this.waitingGate=waitingGate;
        this.hosts=hosts;this.supervisors=supervisors;this.invocations=invocations;this.authority=authority;
        this.receipts=receipts;this.predecessors=predecessors;this.packages=packages;this.waiting=waiting;this.json=json;
        catalogue=SchemaCatalogue.fromClasspath();
    }
    public JsonNode forward(TaskProfessionalHostAccess.Handle host,String session,String tool,JsonNode request)throws Exception {
        validateRequest(tool,request,catalogue);if("AT-08".equals(tool)&&waitingGate==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        var original=hosts.execute(host,a->ThrowingSupplier.of(()->receipts.recover(current(host,a,session,tool,request))).get());
        if(original!=null)return original;
        var selection=hosts.execute(host,a->ThrowingSupplier.of(()->{
            var checked=current(host,a,session,tool,request);receipts.start(checked);
            return new Selection(checked.session().input().projectId(),checked.content(),checked.predecessor());
        }).get());
        if("AT-09".equals(tool)) {
            if(web==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
            var work=hosts.execute(host,a->ThrowingSupplier.of(()->web.startTool(current(host,a,session,tool,request))).get());
            ApprovedWebGateway.Current guard=()->hosts.execute(host,a->ThrowingSupplier.of(()->{same(work,web.startTool(current(host,a,session,tool,request)));return null;}).get());
            try {
                var storedBytes=hosts.execute(host,a->ThrowingSupplier.of(()->web.originalTool(current(host,a,session,tool,request),work)).get());
                final var originalBytes=storedBytes==null?web.gateway().execute(work.request(),false,guard):storedBytes;
                var saved=hosts.execute(host,a->ThrowingSupplier.of(()->web.finishTool(current(host,a,session,tool,request),work,originalBytes)).get());
                if(!"CAPTURED_PENDING_REVIEW".equals(text(saved,"state")))throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
                return hosts.execute(host,a->ThrowingSupplier.of(()->{
                    var checked=current(host,a,session,tool,request);
                    var payload=json.object().put("kind","RECEIPT").put("resource_id",work.id()).put("resource_kind","WEB_ACQUISITION").put("resource_state","CAPTURED_PENDING_REVIEW");payload.putArray("check_ids");
                    var physical=json.object().put("kind","WEB_ACQUISITION");physical.set("witness",web.toolWitness(checked));
                    return receipts.save(checked,new Output(checked.session().input().inputId(),work.revision(),payload,physical));
                }).get());
            } catch(Exception failure) {
                try{hosts.execute(host,a->ThrowingSupplier.of(()->{web.unknownTool(current(host,a,session,tool,request),work);return null;}).get());}catch(Exception stale){failure.addSuppressed(stale);}throw failure;
            }
        }
        if("AT-08".equals(tool))return hosts.execute(host,a->ThrowingSupplier.of(()->{
            var checked=current(host,a,session,tool,request);var witness=waiting.create(checked);
            var payload=json.object().put("kind","RECEIPT").put("resource_id",text(witness,"waiting_item_id"))
                    .put("resource_kind","WAITING_ITEM").put("resource_state","WAITING_INPUT");payload.putArray("check_ids");
            var physical=json.object().put("kind","WAITING_ITEM");physical.set("witness",witness);
            return receipts.save(checked,new Output(checked.session().input().inputId(),null,payload,physical));
        }).get());
        if("AT-03".equals(tool)) {
            // Selection was proved BEFORE any object I/O. Reading all fixed predecessors never
            // creates permission for a caller-selected sibling or result artifact.
            var read=predecessors.readWorker(hosts,host);
            return hosts.execute(host,a->ThrowingSupplier.of(()->{
                var checked=current(host,a,session,tool,request);same(selection.predecessor(),checked.predecessor());
                read.requireCurrent(a);var matches=new ArrayList<JsonNode>();
                for(var entry:read.document(a))if(entry.required("predecessor").equals(selection.predecessor()))matches.add(entry);
                var bundle=one(matches).required("evidence_bundle");String content=new String(ContentReadAuthorization.canonicalBytes(bundle),StandardCharsets.UTF_8);
                same(selection.content().content_sha256(),WireValues.sha256(content.getBytes(StandardCharsets.UTF_8)));
                String reference=catalogue.schemaRef("artifacts","EvidenceBundle");catalogue.validate("artifacts",bundle,"EvidenceBundle");
                var payload=content("ARTIFACT","EVIDENCE_BUNDLE",reference,"application/json",content,checked);
                var physical=json.object().put("kind","PREDECESSOR_READ");physical.set("pins",read.canonicalPins());
                return receipts.save(checked,new Output(selection.content().version_id(),selection.content().version_id(),payload,physical));
            }).get());
        }
        var read=packages.readWorker(hosts,host,selection.project(),selection.content());
        return hosts.execute(host,a->ThrowingSupplier.of(()->{
            var checked=current(host,a,session,tool,request);same(selection.content(),checked.content());read.requireCurrent(a);
            same(selection.content(),read.artifact());var pins=read.canonicalPins();String media=text(pins.required("object_ref"),"mediaType");
            if(!Set.of("application/json","text/plain","text/markdown").contains(media))deny();
            String content=utf8(read.bytes());if(content.isEmpty())bad();String reference="application/json".equals(media)?read.schemaRef():null;
            if(reference!=null)catalogue.bindPublished(reference,read.schemaSha256(),read.bytes());
            var payload=content("SKILL","SKILL_PACKAGE",reference,media,content,checked);
            var physical=json.object().put("kind","SKILL_READ");physical.set("pins",pins);
            return receipts.save(checked,new Output(read.artifact().version_id(),text(request.required("arguments"),"skill_version"),payload,physical));
        }).get());
    }
    private TaskRegisteredToolAuthority.Checked current(TaskProfessionalHostAccess.Handle host,EnterpriseAccess.Admission a,String id,String tool,JsonNode request)throws Exception {
        var session=hosts.session(host,a,id);var supervision=supervisors.requireAdmitted(session);
        if("AT-08".equals(tool)) {
            if(waitingGate==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
            var actual=invocations.waitingToolCall(session,supervision,text(request,"call_id"),request.required("arguments"));
            var checked=authority.require(session,tool,request,new TaskRegisteredToolAuthority.CallProof(){public void requireUnchanged()throws Exception{actual.requireUnchanged();}public JsonNode canonicalPins()throws Exception{return actual.canonicalPins();}});
            waitingGate.require(checked);return checked;
        }
        var actual=invocations.toolCall(session,supervision,text(request,"call_id"),text(request,"name"),request.required("arguments"));return authority.require(session,tool,request,actual);
    }
    private JsonNode content(String kind,String contentKind,String reference,String media,String content,TaskRegisteredToolAuthority.Checked checked)throws Exception {
        var value=json.object().put("kind",kind).put("content_kind",contentKind).put("content_schema_ref",reference)
                .put("content_sha256",WireValues.sha256(content.getBytes(StandardCharsets.UTF_8))).put("media_type",media)
                .put("content",content).put("read_receipt_id",TaskRegisteredToolReceipts.receiptId(checked.session().sessionId(),text(checked.request(),"call_id")));
        return value;
    }
    static final class Output {
        final String artifact,revision;final JsonNode payload,physical;
        private Output(String artifact,String revision,JsonNode payload,JsonNode physical){this.artifact=artifact;this.revision=revision;this.payload=payload.deepCopy();this.physical=physical.deepCopy();}
    }
    private record Selection(String project,PermissionProtocol.Basis content,JsonNode predecessor){}
    private static String utf8(byte[] bytes)throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }
}

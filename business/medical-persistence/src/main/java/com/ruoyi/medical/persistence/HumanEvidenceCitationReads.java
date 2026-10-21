package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.util.*;
import org.springframework.util.function.ThrowingSupplier;

/** Human citation access/location checks. Source approval never decides substantive support. */
public final class HumanEvidenceCitationReads {
 private final EnterpriseAccess access; private final DocumentReadService documents; private final TaskFieldSourceAuthority processing;
 private final TaskResearchSourceReader literals; private final SourceUploadTransport transport;
 public HumanEvidenceCitationReads(EnterpriseAccess access,DocumentReadService documents,TaskFieldSourceAuthority processing,TaskResearchSourceReader literals,SourceUploadTransport transport){this.access=access;this.documents=documents;this.processing=processing;this.literals=literals;this.transport=transport;}
 record Citation(DatasetProtocol.EvidenceRef reference,TaskFieldSourceAuthority.Processing physical,boolean located,String literalSha256,@com.fasterxml.jackson.annotation.JsonIgnore String literal){
  Citation(DatasetProtocol.EvidenceRef reference,TaskFieldSourceAuthority.Processing physical,boolean located,String literalSha256){this(reference,physical,located,literalSha256,null);}
 }
 Map<String,Citation> read(ApplicationSessionIdentity.Verified subject,DocumentReadService.FixedRead body)throws Exception{
  if(transport==null)fail(ErrorCode.OBJECT_UNAVAILABLE);var out=new LinkedHashMap<String,Citation>();long bytes=0;
  for(var link:body.body().evidenceLinks()){
   if(out.size()>=512)fail(ErrorCode.CONTEXT_LIMIT);
   var ref=link.evidence();var physical=current(subject,body,ref);bytes+=Long.parseLong(physical.original().sizeBytes())+Long.parseLong(physical.parsed().sizeBytes());if(bytes>64L*1024*1024)fail(ErrorCode.CONTEXT_LIMIT);
   S3ObjectReader.Admission guard=()->{try{if(!physical.equals(current(subject,body,ref)))fail(ErrorCode.SCOPE_STALE);}catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException(e);}};
   consume(subject,body,physical.original(),guard,false);byte[] parsed=consume(subject,body,physical.parsed(),guard,true);
   var literal=literals.reviewLiteral(physical,ref,parsed);guard.requireCurrent();
   out.put(link.evidenceLinkId(),new Citation(ref,physical,literal.isPresent(),WireValues.sha256(literal.orElse("").getBytes(java.nio.charset.StandardCharsets.UTF_8)),literal.orElse(null)));
  }
  return Collections.unmodifiableMap(out);
 }
 void requireCurrent(EnterpriseAccess.Admission a,DocumentReadService.FixedRead body,Map<String,Citation> pins)throws Exception{
  documents.requireCurrent(a,body);for(var pin:pins.values())if(!pin.physical().equals(physical(body,pin.reference())))fail(ErrorCode.SCOPE_STALE);
 }
 private TaskFieldSourceAuthority.Processing current(ApplicationSessionIdentity.Verified subject,DocumentReadService.FixedRead body,DatasetProtocol.EvidenceRef ref){return access.execute(subject,a->ThrowingSupplier.of(()->{documents.requireCurrent(a,body);return physical(body,ref);}).get());}
 private TaskFieldSourceAuthority.Processing physical(DocumentReadService.FixedRead body,DatasetProtocol.EvidenceRef ref)throws Exception{
  var h=body.header();var source=body.fixedSources().get(ref.sourceVersion());
  if(source==null||source.selection()==null||!h.tenantId().equals(ref.tenantId())||!h.projectId().equals(ref.projectId())||!source.source().sourceId().equals(ref.sourceId())||!source.selection().parseVersion().equals(ref.parseVersion())||!source.selection().approvalRevision().equals(ref.approvalRevision()))fail(ErrorCode.ACCESS_DENIED);
  return processing.processing(h.tenantId(),ref.sourceVersion(),ref.parseVersion(),source.source().originalSha256());
 }
 private byte[] consume(ApplicationSessionIdentity.Verified subject,DocumentReadService.FixedRead body,InterfaceProtocol.ObjectRef ref,S3ObjectReader.Admission guard,boolean keep)throws Exception{
  long size=Long.parseLong(ref.sizeBytes());if(size<1||size>16*1024*1024)fail(ErrorCode.FILE_TOO_LARGE);
  return transport.readDocument(subject,body.header().tenantId(),16*1024*1024,guard,(reader,current)->{try(var object=reader.readVerified(ref,current)){
   byte[] result=keep?new byte[(int)size]:null;var digest=java.security.MessageDigest.getInstance("SHA-256");for(int offset=0;offset<size;){current.requireCurrent();int count=(int)Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,size-offset);byte[] part=object.chunk(offset,count);if(part.length!=count)fail(ErrorCode.CONTENT_MISMATCH);digest.update(part);if(keep)System.arraycopy(part,0,result,offset,count);offset+=count;}
   if(!ref.sha256().equals(java.util.HexFormat.of().formatHex(digest.digest()))||!ref.sha256().equals(object.fullSha256()))fail(ErrorCode.CONTENT_MISMATCH);current.requireCurrent();return result;
  }});
 }
 private static void fail(ErrorCode code){throw new TenantAccessException(code);}
}

package com.ruoyi.medical.persistence;

import static com.ruoyi.medical.persistence.TaskProfessionalPackageAuthoringSchema.*;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** Closed package-only adapter over the existing authenticated, conditional-write transport. */
@Component
@ConditionalOnProperty(prefix="medical.task-professional-package-authoring",name="enabled",havingValue="true")
public final class TaskProfessionalPackageAuthoringTransport {
    private final SourceUploadTransport transport;private final TaskProfessionalPackageAuthoringProfile profile;
    @org.springframework.beans.factory.annotation.Autowired
    public TaskProfessionalPackageAuthoringTransport(SourceUploadTransport transport){this(transport,TaskProfessionalPackageAuthoringProfile.V1);}
    private TaskProfessionalPackageAuthoringTransport(SourceUploadTransport transport,TaskProfessionalPackageAuthoringProfile profile){this.transport=transport;this.profile=profile;}
    TaskProfessionalPackageAuthoringTransport forSchema(TaskProfessionalPackageAuthoringSchema selected){return new TaskProfessionalPackageAuthoringTransport(transport,selected.profile());}
    static final class Verified {
        private final TaskProfessionalPackageAuthoringLedger.Attempt attempt;private final InterfaceProtocol.ObjectRef object;
        private Verified(TaskProfessionalPackageAuthoringLedger.Attempt attempt,InterfaceProtocol.ObjectRef object){this.attempt=attempt;this.object=object;}
        TaskProfessionalPackageAuthoringLedger.Original original(){return attempt.original();}InterfaceProtocol.ObjectRef object(){return object;}
    }
    Verified transfer(ApplicationSessionIdentity.Verified subject,TaskProfessionalPackageAuthoringLedger.Attempt attempt,S3ObjectReader.Admission current)throws Exception {
        Objects.requireNonNull(attempt).claim();var original=attempt.original();validate(original);current.requireCurrent();
        var object=transport.writeDocumentProjection(subject,original.text("tenant_id"),original.write(),original.bytes(),attempt.recovery(),MAXIMUM_BYTES,current);
        verifyReference(original,object);read(subject,original,object,current);current.requireCurrent();return new Verified(attempt,object);
    }
    void readRegistered(ApplicationSessionIdentity.Verified subject,TaskProfessionalPackageAuthoringRegistration.Registered registered,S3ObjectReader.Admission current)throws Exception {
        var o=registered.original();validate(o);read(subject,o,registered.reference(),current);current.requireCurrent();
    }
    private void read(ApplicationSessionIdentity.Verified subject,TaskProfessionalPackageAuthoringLedger.Original original,InterfaceProtocol.ObjectRef reference,S3ObjectReader.Admission current)throws Exception {
        verifyReference(original,reference);transport.readDocument(subject,original.text("tenant_id"),MAXIMUM_BYTES,current,(reader,lease)->{
            S3ObjectReader.Admission guarded=()->{lease.requireCurrent();current.requireCurrent();};
            try(var object=reader.readVerified(reference,guarded)){
                if(!reference.equals(object.reference())||!original.text("payload_sha256").equals(object.fullSha256()))bad();byte[] complete=new byte[original.bytes().length];
                for(int offset=0;offset<complete.length;){guarded.requireCurrent();int count=Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,complete.length-offset);System.arraycopy(object.chunk(offset,count),0,complete,offset,count);offset+=count;guarded.requireCurrent();}
                if(!Arrays.equals(complete,original.bytes()))bad();guarded.requireCurrent();return null;
            }
        });
    }
    static void verifyReference(TaskProfessionalPackageAuthoringLedger.Original original,InterfaceProtocol.ObjectRef object){var intent=original.write();if(object==null||!intent.objectKey().equals(object.objectKey())||!intent.sha256().equals(object.sha256())||!intent.sizeBytes().equals(object.sizeBytes())||!intent.mediaType().equals(object.mediaType())||object.objectVersion()==null||object.objectVersion().isBlank()||"null".equals(object.objectVersion()))bad();}
    private void validate(TaskProfessionalPackageAuthoringLedger.Original original){if(!KINDS.contains(original.text("kind"))||original.bytes().length>MAXIMUM_BYTES||!(profile.objectKey(original.text("tenant_id"),original.text("project_id"),original.text("operation_id"),original.text("artifact_id"))).equals(original.write().objectKey())||!org.apache.commons.codec.digest.DigestUtils.sha256Hex(original.bytes()).equals(original.write().sha256()))bad();}
}

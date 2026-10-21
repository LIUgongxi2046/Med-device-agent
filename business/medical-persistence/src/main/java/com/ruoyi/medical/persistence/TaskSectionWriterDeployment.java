package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import java.util.*;
/** Pinned N04 deployment configuration only, never a launch, isolation or provider proof. */
    public final class TaskSectionWriterDeployment {
        private final JsonNode value;private final String digest;
        public TaskSectionWriterDeployment(JsonNode value,String expectedSha256)throws Exception {
            ProfessionalRuntimeObservations.closed(value,Set.of("schema_version","model_tool_mode","deployment_revision","worker_service_uri","supervisor_endpoint","supervisor_service_uri","supervisor_certificate_sha256","supervisor_trust_revision","runtime_manifest_sha256","host_tool_version","jobs_root","sdk","profile_files","expected_inventory","maximum_object_bytes","token_lifetime_seconds","lease_lifetime_seconds","supervisor_gate_lifetime_seconds","role_name","role_version","profile_artifact"));
            same("1.0.0",text(value,"schema_version"));same("SECTION_WRITER_N04_V1",text(value,"model_tool_mode"));WireValues.uuid(text(value,"deployment_revision"));WireValues.hash(expectedSha256);
            for(String field:List.of("supervisor_certificate_sha256","runtime_manifest_sha256"))WireValues.hash(text(value,field));WireValues.uuid(text(value,"supervisor_trust_revision"));
            for(String field:List.of("worker_service_uri","supervisor_service_uri")){var uri=java.net.URI.create(text(value,field));if(!uri.isAbsolute()||uri.getRawFragment()!=null||uri.getRawQuery()!=null)bad();}
            if(text(value,"worker_service_uri").equals(text(value,"supervisor_service_uri")))deny();
            var endpoint=java.net.URI.create(text(value,"supervisor_endpoint"));if(!"https".equals(endpoint.getScheme())||endpoint.getHost()==null||endpoint.getRawUserInfo()!=null||endpoint.getRawQuery()!=null||endpoint.getRawFragment()!=null||!Set.of("","/").contains(endpoint.getRawPath()))bad();
            String root=text(value,"jobs_root");if(!java.nio.file.Path.of(root).isAbsolute()||!java.nio.file.Path.of(root).normalize().toString().equals(root))bad();
            same("SECTION_WRITER_HOST_V1",text(value,"host_tool_version"));
            bounded(value,"maximum_object_bytes",1,67108864);bounded(value,"token_lifetime_seconds",1,300);bounded(value,"lease_lifetime_seconds",1,86400);bounded(value,"supervisor_gate_lifetime_seconds",1,5);
            if(!"SectionWriter".equals(text(value,"role_name")))bad();
            var role=value.required("role_version");ProfessionalRuntimeObservations.closed(role,Set.of("version_id","content_sha256"));WireValues.uuid(text(role,"version_id"));WireValues.hash(text(role,"content_sha256"));
            var artifact=value.required("profile_artifact");ProfessionalRuntimeObservations.closed(artifact,Set.of("artifact_id","sha256","size_bytes","media_type"));WireValues.uuid(text(artifact,"artifact_id"));WireValues.hash(text(artifact,"sha256"));if(!text(artifact,"size_bytes").matches("[1-9][0-9]{0,7}")||Long.parseLong(text(artifact,"size_bytes"))>67108864||text(artifact,"media_type").isBlank())bad();
            var sdk=value.required("sdk");ProfessionalRuntimeObservations.closed(sdk,Set.of("model","reasoning_effort","max_tokens","initialize_timeout_seconds","request_timeout_seconds","shutdown_timeout_seconds","dsh_bin","sdk_version","runtime_version"));
            if(text(sdk,"model").isBlank()||"SIMULATION".equals(text(sdk,"model"))||"0.0.0.dev0".equals(text(sdk,"sdk_version"))||!text(sdk,"sdk_version").equals(text(sdk,"runtime_version"))||!java.nio.file.Path.of(text(sdk,"dsh_bin")).isAbsolute())bad();
            for(String f:List.of("max_tokens","initialize_timeout_seconds","request_timeout_seconds","shutdown_timeout_seconds"))bounded(sdk,f,1,1000000);
            var profiles=value.required("profile_files");if(!profiles.isArray()||profiles.isEmpty()||profiles.size()>256)bad();var paths=new HashSet<String>();
            for(var file:profiles){ProfessionalRuntimeObservations.closed(file,Set.of("source","relative","sha256"));WireValues.hash(text(file,"sha256"));String relative=TaskProfessionalProfilePath.require(text(file,"relative"));if(!java.nio.file.Path.of(text(file,"source")).isAbsolute()||!paths.add(relative))bad();}
            var inventory=value.required("expected_inventory");ProfessionalRuntimeObservations.closed(inventory,Set.of("plugin_names","tool_schema_hashes","providers","models","model_stream_schema_ref"));
            if(!inventory.required("plugin_names").isArray()||inventory.required("plugin_names").isEmpty()||!inventory.required("tool_schema_hashes").isObject()||!inventory.required("tool_schema_hashes").isEmpty()||!inventory.required("models").isArray()||inventory.required("models").isEmpty())bad();
            var provider=inventory.required("providers");if(!provider.isArray()||provider.size()!=1||!"enterprise-model".equals(text(provider.get(0),"id"))||!"企业模型接口".equals(text(provider.get(0),"name")))bad();
            digest=WireValues.sha256(ContentReadAuthorization.canonicalBytes(value));same(expectedSha256,digest);this.value=value.deepCopy();
        }
        public JsonNode value(){return value.deepCopy();}public String sha256(){return digest;}
        public String revision(){return text(value,"deployment_revision");}public String workerService(){return text(value,"worker_service_uri");}
        public int maximumBytes(){return value.required("maximum_object_bytes").intValue();}
        private static void bounded(JsonNode n,String field,long min,long max){var value=n.required(field);if(!value.isIntegralNumber()||!value.canConvertToLong()||value.longValue()<min||value.longValue()>max)bad();}
    
    private static String text(JsonNode n,String key){if(!n.required(key).isTextual())bad();return n.required(key).textValue();}
    private static void same(Object a,Object b){if(!Objects.equals(a,b))bad();}
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
    private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}

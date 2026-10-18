package com.ruoyi.medical.persistence;
import java.util.List;
/** Safe replay metadata only; no auth_ref, endpoint path, credential value, private approval text or secret path. */
public record ModelEligibilityBinding(String schema_version,String provider,String model_id,String model_version,String model_config_revision,String config_id,String config_content_sha256,String connection_verification_id,String credential_reference_id,String credential_revision,String price_version,String data_policy_version,List<String> allowed_classifications,List<String> allowed_regions,boolean allow_training){public ModelEligibilityBinding{allowed_classifications=List.copyOf(allowed_classifications);allowed_regions=List.copyOf(allowed_regions);}}

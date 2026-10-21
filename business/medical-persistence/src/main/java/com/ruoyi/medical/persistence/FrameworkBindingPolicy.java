package com.ruoyi.medical.persistence;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Missing deployment pins disable consumption, not application startup or the authoring API. */
@ConfigurationProperties(prefix="medical.framework-bindings")
public class FrameworkBindingPolicy {
    private boolean enabled;private String environmentId;private String releaseId;private String sourceCommit;private String runtimeEnvironment="PRODUCTION";
    public boolean isEnabled(){return enabled;}public void setEnabled(boolean value){enabled=value;}
    public String getEnvironmentId(){return environmentId;}public void setEnvironmentId(String value){environmentId=value;}
    public String getReleaseId(){return releaseId;}public void setReleaseId(String value){releaseId=value;}
    public String getSourceCommit(){return sourceCommit;}public void setSourceCommit(String value){sourceCommit=value;}
    public String getRuntimeEnvironment(){return runtimeEnvironment;}public void setRuntimeEnvironment(String value){runtimeEnvironment=value;}
}

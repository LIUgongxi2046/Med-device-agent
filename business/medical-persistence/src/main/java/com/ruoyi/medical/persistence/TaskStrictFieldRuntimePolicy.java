package com.ruoyi.medical.persistence;
import org.springframework.boot.context.properties.ConfigurationProperties;
/** Capacity only, never a capability, source permission or professional approval. */
@ConfigurationProperties("medical.task-strict-field")
public record TaskStrictFieldRuntimePolicy(int maximumEntries,int maximumSections,int maximumContextIds,int maximumInputBytes,
        int maximumResultBytes,int maximumTextCodepoints,long maximumWork,long maximumMilliseconds,int maximumObjectBytes){
    StrictFieldComparisonEngine.Limits limits(){return new StrictFieldComparisonEngine.Limits(maximumEntries,maximumSections,maximumContextIds,maximumInputBytes,maximumResultBytes,maximumTextCodepoints,maximumWork,maximumMilliseconds);}
}

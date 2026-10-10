package com.ruoyi.medical.persistence.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RunReadMapper {
    List<String> head(Map<String,Object> values);
    List<String> steps(Map<String,Object> values);
    List<String> waiting(Map<String,Object> values);
    List<String> invocations(Map<String,Object> values);
    List<String> budget(Map<String,Object> values);
    List<String> cursor(Map<String,Object> values);
    List<String> stream(Map<String,Object> values);
    List<String> eventCursor(Map<String,Object> values);
    List<String> events(Map<String,Object> values);
    List<String> eventAuthority(Map<String,Object> values);
}

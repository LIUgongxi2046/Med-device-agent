package com.ruoyi.medical.persistence.mapper;

import com.ruoyi.medical.persistence.ScenarioCatalogueRow;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ScenarioCatalogueMapper {
    List<ScenarioCatalogueRow> versions(Map<String,Object> values);
}

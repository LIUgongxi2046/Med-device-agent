package com.ruoyi.medical.identity.mapper;

import com.ruoyi.medical.identity.LocalAccountProtocol;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface LocalAccountMapper {
    List<LocalAccountProtocol.AccountItem> selectAccounts(@Param("cursor") String cursor);
}

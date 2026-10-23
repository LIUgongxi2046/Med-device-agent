package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.ErrorCode;
import java.time.*;import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
@Component
@ConditionalOnProperty(prefix="medical.business-audit",name="enabled",havingValue="true")
public final class BusinessAuditCursor{
 record Owner(String tenant,String actor,String session,String context,String auth,String governance,String queryHash){}
 record Position(LocalDateTime createdAt,String auditId){}
 private record Entry(Owner owner,Position position,Instant expires){}
 private final Map<String,Entry> entries=new LinkedHashMap<>();private final Clock clock;private final BusinessAuditPolicy policy;
 @org.springframework.beans.factory.annotation.Autowired
 public BusinessAuditCursor(BusinessAuditPolicy policy){this(policy,Clock.systemUTC());}
 BusinessAuditCursor(BusinessAuditPolicy policy,Clock clock){this.policy=policy;this.clock=clock;}
 synchronized Position resolve(String token,Owner owner){purge();if(token==null)return null;var entry=entries.get(token);if(entry==null)throw new TenantAccessException(ErrorCode.CURSOR_EXPIRED);if(!entry.owner().equals(owner))throw new TenantAccessException(ErrorCode.CURSOR_INVALID);return entry.position();}
 synchronized String issue(Owner owner,Position position){purge();if(entries.size()>=policy.maximumCursorEntries())entries.remove(entries.keySet().iterator().next());String token=UUID.randomUUID().toString();entries.put(token,new Entry(owner,position,clock.instant().plusSeconds(policy.cursorSeconds())));return token;}
 private void purge(){entries.values().removeIf(e->!e.expires().isAfter(clock.instant()));}
}

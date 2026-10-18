package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import java.util.List;
import java.util.HashSet;

/** Pure narrowing operation. Inputs must come from the actual private approved
 * node-policy and authenticated token authorities; this class grants nothing. */
final class RuntimeNodePermissions {
 private RuntimeNodePermissions(){}
 static List<RunTokenAuthoritySchema.Permission> intersect(
   List<RunTokenAuthoritySchema.Permission> principal,
   List<RunTokenAuthoritySchema.Permission> approvedNode,List<String> tokenActions) {
  if(principal==null || approvedNode==null || tokenActions==null || approvedNode.isEmpty() || tokenActions.isEmpty()
   || new HashSet<>(principal).size()!=principal.size() || new HashSet<>(approvedNode).size()!=approvedNode.size()
   || new HashSet<>(tokenActions).size()!=tokenActions.size()
   || principal.stream().anyMatch(java.util.Objects::isNull) || approvedNode.stream().anyMatch(java.util.Objects::isNull)
   || tokenActions.stream().anyMatch(a->a==null || !a.matches("[a-z][a-z0-9_.-]{0,63}"))
   || !principal.containsAll(approvedNode))denied();
  var allowed=approvedNode.stream().filter(p->tokenActions.contains(p.action())).toList();
  if(allowed.isEmpty() || tokenActions.stream().anyMatch(a->allowed.stream().noneMatch(p->a.equals(p.action()))))denied();
  return List.copyOf(allowed);
 }
 private static void denied(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}

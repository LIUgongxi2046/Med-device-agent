package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;

/** Raw slash-component validation. Never normalizes, decodes or rewrites reviewed Unicode. */
final class TaskProfessionalProfilePath {
 private TaskProfessionalProfilePath(){}
 static String require(String value){
  if(value==null||!value.startsWith("profiles/")||value.indexOf('\\')>=0||value.indexOf('%')>=0||value.codePoints().anyMatch(Character::isISOControl))bad();
  for(String part:value.split("/",-1))if(part.isEmpty()||part.equals(".")||part.equals(".."))bad();
  return value;
 }
 private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}

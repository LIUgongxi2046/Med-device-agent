package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.InterfaceProtocol;
final class DocumentComparisonProtocol{
 private DocumentComparisonProtocol(){}
 record Command(String intentSha256,boolean attempted,InterfaceProtocol.ObjectRef reference){}
}

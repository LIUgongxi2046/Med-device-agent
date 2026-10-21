package com.ruoyi.medical.persistence;

import java.net.InetAddress;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Deferred, not executed in this implementation pass. These checks never resolve DNS. */
class ApprovedWebGatewayBoundaryTest {
 @Test void approvedExactDomainOnly(){
  assertEquals("https://www.example.com/report.pdf",ApprovedWebGateway.target("https://www.example.com/report.pdf",List.of("www.example.com")).toASCIIString());
  for(String url:List.of("http://www.example.com/a","https://www.example.com.evil.test/a","https://sub.www.example.com/a","https://www.example.com:8443/a","https://user@www.example.com/a","https://www.example.com/a#fragment","https://127.0.0.1/a","file:///etc/passwd"))
   assertThrows(RuntimeException.class,()->ApprovedWebGateway.target(url,List.of("www.example.com")));
 }
 @Test void rejectNonPublicAddressesWithoutDns()throws Exception{
  for(byte[] address:List.of(new byte[]{127,0,0,1},new byte[]{10,1,2,3},new byte[]{(byte)169,(byte)254,1,1},new byte[]{100,64,0,1},new byte[]{(byte)192,(byte)168,0,1},new byte[]{(byte)198,18,0,1},new byte[16]))
   assertThrows(RuntimeException.class,()->ApprovedWebGateway.publicAddress(InetAddress.getByAddress(address)));
  assertDoesNotThrow(()->ApprovedWebGateway.publicAddress(InetAddress.getByAddress(new byte[]{8,8,8,8})));
 }
 @Test void domainsCannotBecomePathsOrWildcards(){for(String domain:List.of("*.example.com","example.com/path","example.com:443","Example.com","localhost","service.internal","127.0.0.1"))assertThrows(RuntimeException.class,()->ApprovedWebGateway.domain(domain));}
 @Test void at09CannotBeUsedByGenericResearcher(){assertThrows(TenantAccessException.class,()->TaskRegisteredToolAuthority.requireRole("Researcher","AT-09"));assertDoesNotThrow(()->TaskRegisteredToolAuthority.requireRole("NetworkResearcher","AT-09"));}
}

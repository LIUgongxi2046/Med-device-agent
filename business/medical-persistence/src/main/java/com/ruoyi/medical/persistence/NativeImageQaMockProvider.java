package com.ruoyi.medical.persistence;

import java.util.*;
import static com.ruoyi.medical.persistence.NativeImageQaProtocol.*;

/** Deterministic byte transport simulator. No image semantic claims and no external I/O. */
public final class NativeImageQaMockProvider implements NativeImageQaSimulation.Provider {
 private final Map<String,Receipt> receipts=new HashMap<>();private int calls;
 @Override public synchronized Receipt send(Context c)throws Exception{
  validate(c);String hash=hash(c);Receipt previous=receipts.get(c.requestId());
  if(previous!=null){require(previous.contextSha256().equals(hash),"IDEMPOTENCY_CONFLICT");return previous;}
  calls++;Receipt r=new Receipt(VERSION,"SIM",PROVIDER,c.requestId(),hash,c.sessionId(),result(c),bytes(c),units(c),true,false);receipts.put(c.requestId(),r);return r;
 }
 static String result(Context c){return "SYNTHETIC: transported "+c.images().size()+" original image(s), "+bytes(c)+" bytes. No visual understanding, answer support or production evidence asserted.";}
 @Override public synchronized Optional<Receipt> lookup(String requestId){return Optional.ofNullable(receipts.get(requestId));}
 public synchronized int calls(){return calls;}
}

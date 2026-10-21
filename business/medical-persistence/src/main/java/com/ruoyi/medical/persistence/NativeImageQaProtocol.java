package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.WireValues;
import java.io.ByteArrayInputStream;
import java.util.*;
import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;

/** Explicit local mock protocol, NOT an OpenAI/DeepSeek or production provider contract. */
public final class NativeImageQaProtocol {
 public static final String VERSION="1.0.0", PROVIDER="local-synthetic-image-v1", CAPABILITY="NATIVE_IMAGE_TRANSPORT_SIM";
 public static final int MAX_BYTES=4*1024*1024, MAX_PIXELS=16_000_000;
 private static final ObjectMapper JSON=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_TRAILING_TOKENS,DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES).propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
 private NativeImageQaProtocol(){}
 public record Region(int x,int y,int width,int height){}
 public record Image(String sourceVersion,String rightsRevision,String objectRef,String sha256,String mime,int width,int height,Region region,String base64){}
 public record Context(String schemaVersion,String mode,String provider,String capability,String tenantId,String projectId,String sessionId,String requestId,String question,List<Image> images,long maximumUnits){public Context{images=List.copyOf(images);}}
 public record Receipt(String schemaVersion,String mode,String provider,String requestId,String contextSha256,String sessionId,String result,long imageBytes,long chargedUnits,boolean synthetic,boolean formalEvidenceEligible){}
 public record Review(String schemaVersion,String mode,String answerContextSha256,String reviewContextSha256,String verdict,boolean synthetic,boolean formalEvidenceEligible){}
 static JsonNode parse(String raw)throws Exception{return JSON.readTree(raw);}
 public static Context read(String raw)throws Exception{try{Context c=JSON.readValue(raw,Context.class);validate(c);return c;}catch(NullPointerException e){throw new IllegalArgumentException("NULL_CONTEXT_FIELD",e);}}
 public static String write(Context c)throws Exception{validate(c);return JSON.writeValueAsString(c);}
 public static String hash(Context c)throws Exception{validate(c);return WireValues.sha256(ContentReadAuthorization.canonicalBytes(JSON.valueToTree(c)));}
 public static void validate(Context c)throws Exception{
  require(VERSION.equals(c.schemaVersion())&&"SIM".equals(c.mode())&&PROVIDER.equals(c.provider())&&CAPABILITY.equals(c.capability()),"SIM_PROFILE_REQUIRED");
  for(String id:List.of(c.tenantId(),c.projectId(),c.sessionId(),c.requestId()))WireValues.uuid(id);
  require(c.question()!=null&&!c.question().isBlank()&&c.question().length()<=8000,"QUESTION_INVALID");
  require(!c.images().isEmpty()&&c.images().size()<=8&&c.maximumUnits()>0,"CONTEXT_LIMIT");
  long bytes=0;Set<String> refs=new HashSet<>();
  for(Image i:c.images()){
   WireValues.uuid(i.sourceVersion());WireValues.uuid(i.rightsRevision());WireValues.hash(i.sha256());
   require(i.objectRef()!=null&&i.objectRef().matches("fixture/[A-Za-z0-9._/-]{1,200}")&&!i.objectRef().contains("..")&&refs.add(i.objectRef()),"FIXTURE_REFERENCE_REQUIRED");
   require(Set.of("image/png","image/jpeg").contains(i.mime())&&i.base64()!=null&&i.base64().length()<=(MAX_BYTES+2L)/3*4,"IMAGE_LIMIT");
   byte[] raw=Base64.getDecoder().decode(i.base64());bytes+=raw.length;
   require(raw.length>0&&bytes<=MAX_BYTES&&WireValues.sha256(raw).equals(i.sha256()),"IMAGE_HASH_OR_LIMIT");
   try(ImageInputStream stream=ImageIO.createImageInputStream(new ByteArrayInputStream(raw))){
    var readers=ImageIO.getImageReaders(stream);require(readers.hasNext(),"IMAGE_DECODE_FAILED");var reader=readers.next();
    try{reader.setInput(stream,true,true);String format=reader.getFormatName();require(("image/png".equals(i.mime())&&"png".equalsIgnoreCase(format))||("image/jpeg".equals(i.mime())&&"jpeg".equalsIgnoreCase(format)),"MIME_MISMATCH");
     int w=reader.getWidth(0),h=reader.getHeight(0);require(w==i.width()&&h==i.height()&&w>0&&h>0&&(long)w*h<=MAX_PIXELS,"IMAGE_DIMENSIONS");
     var r=i.region();require(r!=null&&r.x()>=0&&r.y()>=0&&r.width()>0&&r.height()>0&&(long)r.x()+r.width()<=w&&(long)r.y()+r.height()<=h,"REGION_INVALID");require(reader.read(0)!=null,"IMAGE_DECODE_FAILED");
    }finally{reader.dispose();}
   }
  }
 }
 static long bytes(Context c){return c.images().stream().mapToLong(i->Base64.getDecoder().decode(i.base64()).length).sum();}
 static long units(Context c){return Math.addExact(1,(bytes(c)+1023)/1024);}
 static void require(boolean b,String code){if(!b)throw new IllegalArgumentException(code);}
}

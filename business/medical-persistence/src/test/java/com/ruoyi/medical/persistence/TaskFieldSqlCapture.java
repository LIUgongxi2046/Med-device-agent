package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.regex.Pattern;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;

/** Test-only capture of SQL actually passed into explicit JDBC boundary doubles.
 * Captures SQL and bind SHAPES only. Never records argument values or opens a database. */
final class TaskFieldSqlCapture {
    private static final String FORMAT="task-field-target-jdbc-shape-v1";
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final Set<String> PARSED=new HashSet<>(),EMITTED=new HashSet<>();
    private static Path output;
    private TaskFieldSqlCapture() { }

    static synchronized void positional(String producer,String sql,Object[] args) {
        var placeholders=parameters(sql);
        if(placeholders.stream().anyMatch(name->!name.isEmpty()) || placeholders.size()!=args.length)
            throw new AssertionError("JDBC positional parameter mismatch: "+producer+" "+sql);
        emit(producer,sql,"POSITIONAL",List.of(),Arrays.stream(args).map(TaskFieldSqlCapture::type).toList());
    }
    static synchronized void named(String producer,String sql,Map<String,?> args) {
        var placeholders=parameters(sql);
        if(placeholders.stream().anyMatch(String::isEmpty) || !new HashSet<>(placeholders).equals(args.keySet()))
            throw new AssertionError("JDBC named parameter mismatch: "+producer+" "+sql);
        emit(producer,sql,"NAMED",placeholders,placeholders.stream().map(name->type(args.get(name))).toList());
    }
    private static List<String> parameters(String sql) {
        try {if(!PARSED.contains(sql)){if(CCJSqlParserUtil.parse(sql)==null)throw new AssertionError("Empty SQL");PARSED.add(sql);}}
        catch(Exception failure){throw new AssertionError("Actual emitted JDBC SQL does not parse: "+sql,failure);}
        var masked=new StringBuilder(sql);char quote=0;
        for(int i=0;i<sql.length();i++) {
            char ch=sql.charAt(i);
            if(quote!=0) {
                masked.setCharAt(i,' ');
                if(ch=='\\' && i+1<sql.length()){masked.setCharAt(++i,' ');continue;}
                if(ch==quote){if(i+1<sql.length()&&sql.charAt(i+1)==quote){masked.setCharAt(++i,' ');}else quote=0;}
            } else if(ch=='\'' || ch=='"' || ch=='`'){quote=ch;masked.setCharAt(i,' ');}
        }
        var matcher=Pattern.compile("\\?|(?<!:):([A-Za-z_][A-Za-z_0-9]*)").matcher(masked);var names=new ArrayList<String>();
        while(matcher.find())names.add(matcher.group(1)==null?"":matcher.group(1));return names;
    }
    private static String type(Object value){return value==null?"null":value.getClass().getName();}
    private static void emit(String producer,String sql,String mode,List<String> names,List<String> types) {
        var record=new LinkedHashMap<String,Object>();record.put("format",FORMAT);record.put("producer",producer);
        record.put("sql",sql);record.put("binding_mode",mode);record.put("parameter_count",types.size());
        record.put("parameter_names",names);record.put("parameter_types",types);
        String requested=System.getProperty("medical.taskFieldSqlEvidence");if(requested==null)return;
        try {
            Path target=Path.of(requested).toAbsolutePath().normalize();
            if(output==null){output=target;var parent=target.getParent();if(parent!=null)Files.createDirectories(parent);Files.writeString(target,"",StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);}
            else if(!output.equals(target))throw new AssertionError("SQL capture output cannot change within one test JVM");
            String encoded=JSON.writeValueAsString(record);
            if(EMITTED.add(encoded))Files.writeString(output,encoded+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        }catch(Exception failure){throw new AssertionError("Cannot write SQL shape evidence",failure);}
    }
}

package com.ruoyi.medical.persistence.erasure;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import static com.ruoyi.medical.persistence.erasure.ExternalErasure.*;

/** Deployment-owned durable private journal. Atomic CAS, fsync-before-send, cross-process lock;
 * never recover from missing state by re-dispatching. Directory must be persistent, non-shared
 * with end users and included in operational backup/retention policy. */
public final class ExternalErasureFileJournal implements Journal {
    private static final ConcurrentHashMap<Path,Object> LOCKS=new ConcurrentHashMap<>();
    private final Path directory;
    private final ObjectMapper json=JsonMapper.builder().findAndAddModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public ExternalErasureFileJournal(Path directory) {
        this.directory=directory.toAbsolutePath().normalize();
        if(!Files.isDirectory(this.directory) || Files.isSymbolicLink(this.directory))
            throw new IllegalArgumentException("PERSISTENT_JOURNAL_DIRECTORY_REQUIRED");
    }
    private <T>T locked(Supplier<T> action) {
        synchronized(LOCKS.computeIfAbsent(directory,p->new Object())) {
            try(var channel=FileChannel.open(directory.resolve(".lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
                var ignored=channel.lock()) { return action.get(); }
            catch(java.io.IOException error){throw new IllegalStateException("JOURNAL_UNAVAILABLE",error);}
        }
    }
    private Path path(String key) {token(key);return directory.resolve(key+".json");}
    private Entry load(String key) {
        try {
            Path path=path(key);if(!Files.exists(path))return null;
            if(Files.isSymbolicLink(path)||Files.size(path)>1_048_576)throw new IllegalStateException("INVALID_JOURNAL");
            Entry entry=json.readValue(Files.readAllBytes(path),Entry.class);
            if(!key.equals(entry.request().key()))throw new IllegalStateException("INVALID_JOURNAL_KEY");return entry;
        } catch(java.io.IOException error){throw new IllegalStateException("JOURNAL_UNREADABLE",error);}
    }
    private void save(String key,Entry value) {
        Path temporary=null;
        try {
            temporary=Files.createTempFile(directory,".pending-",".json");
            byte[] bytes=json.writeValueAsBytes(value);
            try(var out=FileChannel.open(temporary,StandardOpenOption.WRITE)) {
                ByteBuffer buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())out.write(buffer);out.force(true);
            }
            Files.move(temporary,path(key),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            try(var dir=FileChannel.open(directory,StandardOpenOption.READ)){dir.force(true);}
        }catch(java.io.IOException error){throw new IllegalStateException("JOURNAL_WRITE_UNCERTAIN",error);}
        finally{if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(java.io.IOException ignored){}}
    }
    public Entry prepare(Delete request) {return locked(()->{
        Entry prior=load(request.key());if(prior!=null){if(!prior.request().equals(request))throw new IllegalStateException("IDEMPOTENCY_CONFLICT");return prior;}
        Entry value=new Entry(request,Phase.PREPARED,null);save(request.key(),value);return value;
    });}
    public Entry read(String key){return locked(()->load(key));}
    public void transition(String key,Phase expected,Phase next,Receipt receipt){locked(()->{
        Entry prior=Objects.requireNonNull(load(key),"RECOVERY_NOT_FOUND");
        if(prior.phase()!=expected)throw new IllegalStateException("JOURNAL_REVISION_CONFLICT");
        boolean legal=expected==Phase.PREPARED&&next==Phase.DISPATCHING ||
            (expected==Phase.DISPATCHING||expected==Phase.UNKNOWN)&&(next==Phase.UNKNOWN||next==Phase.COMPLETE);
        if(!legal || next==Phase.COMPLETE&&receipt==null)throw new IllegalStateException("INVALID_TRANSITION");
        save(key,new Entry(prior.request(),next,receipt));return null;
    });}
}

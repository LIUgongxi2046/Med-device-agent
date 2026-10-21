package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.WireValues;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.context.SmartLifecycle;

/** Single bounded poller. A failed/ambiguous delivery retains the exact event ID;
 * restart rescans immutable events and the server's durable attempt handles recovery.
 * The cursor is only a scan optimization, never a consumed/accepted checkpoint. */
final class TaskRunRelayLifecycleV2 implements SmartLifecycle {
    private final TaskRunRelayHttpClientV2 client;private final String tenant;private final long pollMillis,shutdownMillis;private final int maximumFailures;
    private final ArrayDeque<String> page=new ArrayDeque<>();private String cursor;private int failures;
    private volatile boolean running;private boolean stopped;private ScheduledExecutorService executor;
    TaskRunRelayLifecycleV2(TaskRunRelayHttpClientV2 client,String tenant,Duration poll,Duration shutdown,int maximumFailures){
        this.client=Objects.requireNonNull(client);WireValues.uuid(tenant);this.tenant=tenant;
        if(poll==null||poll.compareTo(Duration.ofSeconds(1))<0||poll.compareTo(Duration.ofMinutes(5))>0||shutdown==null||shutdown.compareTo(Duration.ofMillis(100))<0||shutdown.compareTo(Duration.ofMinutes(2))>0||maximumFailures<1||maximumFailures>100)throw new IllegalArgumentException("BOUNDED_V2_RELAY_LIFECYCLE_REQUIRED");
        pollMillis=poll.toMillis();shutdownMillis=shutdown.toMillis();this.maximumFailures=maximumFailures;
    }
    @Override public synchronized void start(){
        if(running)return;if(stopped)throw new IllegalStateException("V2_RELAY_ALREADY_STOPPED");
        executor=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"task-run-outbox-v2");t.setDaemon(true);return t;});running=true;
        try{executor.scheduleWithFixedDelay(this::tick,0,pollMillis,TimeUnit.MILLISECONDS);}
        catch(RuntimeException failure){running=false;stopped=true;executor.shutdownNow();throw failure;}
    }
    private void tick(){
        if(!running)return;
        try{
            if(page.isEmpty()){
                var values=client.candidates(tenant,cursor);if(!running)return;
                if(values.isEmpty()){cursor=null;failures=0;return;}page.addAll(values);
            }
            String original=page.getFirst();boolean consumed=client.deliver(tenant,original);
            if(consumed){cursor=original;page.removeFirst();}failures=0;
        }catch(Exception failure){
            if(Thread.currentThread().isInterrupted())return;
            if(++failures>=maximumFailures){synchronized(this){running=false;stopped=true;}executor.shutdown();}
        }
    }
    @Override public void stop(){
        ScheduledExecutorService current;
        synchronized(this){if(stopped)return;stopped=true;running=false;current=executor;}
        if(current==null)return;current.shutdownNow();
        try{current.awaitTermination(shutdownMillis,TimeUnit.MILLISECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
    }
    @Override public void stop(Runnable callback){try{stop();}finally{callback.run();}}
    @Override public boolean isRunning(){return running;}
    @Override public int getPhase(){return Integer.MAX_VALUE-100;}
}

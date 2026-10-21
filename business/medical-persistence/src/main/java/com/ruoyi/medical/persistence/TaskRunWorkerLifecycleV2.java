package com.ruoyi.medical.persistence;

import io.temporal.worker.WorkerFactory;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.springframework.context.SmartLifecycle;

/** Owns only the explicitly configured V2 factory, never the shared Temporal client.
 * Shutdown is bounded; a stopped instance cannot silently restart a new worker. */
public final class TaskRunWorkerLifecycleV2 implements SmartLifecycle {
    private final WorkerFactory factory;
    private final long shutdownMillis;
    private volatile boolean running;
    private boolean stopped;
    public TaskRunWorkerLifecycleV2(WorkerFactory factory,Duration shutdown){
        this.factory=Objects.requireNonNull(factory);
        if(shutdown==null||shutdown.compareTo(Duration.ofMillis(100))<0||shutdown.compareTo(Duration.ofMinutes(2))>0)
            throw new IllegalArgumentException("BOUNDED_V2_WORKER_SHUTDOWN_REQUIRED");
        shutdownMillis=shutdown.toMillis();
    }
    @Override public synchronized void start(){
        if(running)return;
        if(stopped)throw new IllegalStateException("V2_WORKER_ALREADY_STOPPED");
        try{factory.start();running=true;}catch(RuntimeException failure){stopped=true;factory.shutdownNow();throw failure;}
    }
    @Override public synchronized void stop(){
        if(stopped)return;
        stopped=true;running=false;
        factory.shutdown();
        factory.awaitTermination(shutdownMillis,TimeUnit.MILLISECONDS);
        if(!factory.isTerminated())factory.shutdownNow();
    }
    @Override public void stop(Runnable callback){try{stop();}finally{callback.run();}}
    @Override public boolean isRunning(){return running&&!factory.isShutdown();}
    @Override public int getPhase(){return Integer.MAX_VALUE-200;}
}

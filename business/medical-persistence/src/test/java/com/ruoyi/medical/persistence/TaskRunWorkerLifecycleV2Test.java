package com.ruoyi.medical.persistence;

import io.temporal.worker.WorkerFactory;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskRunWorkerLifecycleV2Test {
    @Test void boundedStartStopOwnsOnlyItsFactory(){
        var factory=mock(WorkerFactory.class);var life=new TaskRunWorkerLifecycleV2(factory,Duration.ofMillis(500));
        assertFalse(life.isRunning());life.start();life.start();assertTrue(life.isRunning());
        var callback=mock(Runnable.class);life.stop(callback);life.stop();assertFalse(life.isRunning());
        verify(factory,times(1)).start();verify(factory,times(1)).shutdown();verify(factory).awaitTermination(500,TimeUnit.MILLISECONDS);verify(factory).shutdownNow();verify(callback).run();
        assertThrows(IllegalStateException.class,life::start);
    }
    @Test void gracefulShutdownDoesNotForceAndInvalidBoundsCannotLaunch(){
        var factory=mock(WorkerFactory.class);when(factory.isTerminated()).thenReturn(true);
        var life=new TaskRunWorkerLifecycleV2(factory,Duration.ofSeconds(1));life.start();life.stop();verify(factory,never()).shutdownNow();
        assertThrows(IllegalArgumentException.class,()->new TaskRunWorkerLifecycleV2(factory,Duration.ZERO));
        assertThrows(IllegalArgumentException.class,()->new TaskRunWorkerLifecycleV2(factory,Duration.ofMinutes(3)));
    }
    @Test void failedStartupCannotReportReadinessOrRestart(){
        var factory=mock(WorkerFactory.class);doThrow(new IllegalStateException("unavailable")).when(factory).start();
        var life=new TaskRunWorkerLifecycleV2(factory,Duration.ofSeconds(1));assertThrows(IllegalStateException.class,life::start);
        assertFalse(life.isRunning());verify(factory).shutdownNow();assertThrows(IllegalStateException.class,life::start);
    }
}

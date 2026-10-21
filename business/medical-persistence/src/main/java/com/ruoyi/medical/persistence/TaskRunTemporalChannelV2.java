package com.ruoyi.medical.persistence;

import com.ruoyi.medical.identity.ServiceClientTlsStore;
import io.grpc.*;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.channel.ChannelOption;
import io.grpc.netty.shaded.io.netty.handler.ssl.*;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded fresh TLS channel per RPC. Never reuses a TLS session after trust/key
 * rotation or revocation; no insecure transport, authority override, credentials
 * generation or network connection occurs in this object's constructor. */
final class TaskRunTemporalChannelV2 extends ManagedChannel implements AutoCloseable {
    private final URI origin;private final ServiceClientTlsStore.ContextSource contexts;
    private final int connectMillis,maximumMessageBytes;private final long maximumCallMillis;
    private final List<Runnable> stateObservers=new ArrayList<>();private final int maximumCalls;private int reserved;private final Set<ManagedChannel> channels=new HashSet<>();private boolean shutdown;
    TaskRunTemporalChannelV2(URI origin,ServiceClientTlsStore.ContextSource contexts,Duration connect,Duration maximumCall,int maximumMessageBytes,int maximumCalls){
        Objects.requireNonNull(origin);if(!"https".equals(origin.getScheme())||origin.getHost()==null||origin.getPort()<1||origin.getUserInfo()!=null||origin.getRawQuery()!=null||origin.getRawFragment()!=null||!Set.of("","/").contains(origin.getRawPath()))throw new IllegalArgumentException("EXPLICIT_TEMPORAL_TLS_TARGET_REQUIRED");
        if(connect==null||connect.compareTo(Duration.ofMillis(100))<0||connect.compareTo(Duration.ofSeconds(30))>0||maximumCall==null||maximumCall.compareTo(connect)<0||maximumCall.compareTo(Duration.ofMinutes(3))>0||maximumMessageBytes<4096||maximumMessageBytes>16777216||maximumCalls<1||maximumCalls>1024)throw new IllegalArgumentException("BOUNDED_TEMPORAL_TRANSPORT_REQUIRED");
        this.origin=origin;this.contexts=Objects.requireNonNull(contexts);connectMillis=Math.toIntExact(connect.toMillis());maximumCallMillis=maximumCall.toMillis();this.maximumMessageBytes=maximumMessageBytes;this.maximumCalls=maximumCalls;
    }
    @Override public String authority(){return origin.getRawAuthority();}
    // There is deliberately no persistent READY transport. SDK connect() performs
    // its real bounded health RPC when this channel reports IDLE.
    @Override public synchronized ConnectivityState getState(boolean requestConnection){return shutdown?ConnectivityState.SHUTDOWN:ConnectivityState.IDLE;}
    @Override public synchronized void notifyWhenStateChanged(ConnectivityState source,Runnable callback){Objects.requireNonNull(callback);if(source!=getState(false)){callback.run();return;}if(stateObservers.size()>=maximumCalls)throw new IllegalStateException("TEMPORAL_STATE_OBSERVER_CAPACITY");stateObservers.add(callback);}
    @Override public synchronized void resetConnectBackoff(){List.copyOf(channels).forEach(ManagedChannel::resetConnectBackoff);}
    @Override public void enterIdle(){/* No persistent idle transport to recycle. */}
    private void notifyShutdown(){var callbacks=List.copyOf(stateObservers);stateObservers.clear();for(var callback:callbacks)callback.run();}
    @Override public <ReqT,RespT>ClientCall<ReqT,RespT> newCall(MethodDescriptor<ReqT,RespT> method,CallOptions options){
        return new ClientCall<>(){
            private ClientCall<ReqT,RespT> delegate;private ManagedChannel owned;private final AtomicBoolean finished=new AtomicBoolean();private boolean acquired,cancelled,started;
            @Override public synchronized void start(Listener<RespT> listener,Metadata headers){
                if(started)throw new IllegalStateException("TEMPORAL_CALL_ALREADY_STARTED");started=true;
                if(cancelled){listener.onClose(Status.CANCELLED,new Metadata());return;}
                try{
                    synchronized(TaskRunTemporalChannelV2.this){if(shutdown)throw new IllegalStateException("TEMPORAL_CHANNEL_SHUTDOWN");reap();if(reserved>=maximumCalls)throw new IllegalStateException("TEMPORAL_CHANNEL_CAPACITY");reserved++;acquired=true;}
                    var alpn=new ApplicationProtocolConfig(ApplicationProtocolConfig.Protocol.ALPN,ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,"h2");
                    var ssl=new JdkSslContext(Objects.requireNonNull(contexts.create()),true,null,IdentityCipherSuiteFilter.INSTANCE,alpn,ClientAuth.NONE,new String[]{"TLSv1.3"},false);
                    owned=NettyChannelBuilder.forAddress(origin.getHost(),origin.getPort()).sslContext(ssl).useTransportSecurity()
                        .withOption(ChannelOption.CONNECT_TIMEOUT_MILLIS,connectMillis).maxInboundMessageSize(maximumMessageBytes).disableRetry().build();
                    synchronized(TaskRunTemporalChannelV2.this){channels.add(owned);if(shutdown)throw new IllegalStateException("TEMPORAL_CHANNEL_SHUTDOWN");}
                    long millis=maximumCallMillis;if(options.getDeadline()!=null)millis=Math.min(millis,Math.max(0,options.getDeadline().timeRemaining(TimeUnit.MILLISECONDS)));
                    delegate=owned.newCall(method,options.withDeadlineAfter(millis,TimeUnit.MILLISECONDS));
                    delegate.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(listener){
                        @Override public void onClose(Status status,Metadata trailers){try{super.onClose(status,trailers);}finally{release();}}
                    },headers);
                }catch(Exception failure){release();listener.onClose(Status.UNAVAILABLE.withDescription("PINNED_TEMPORAL_TRANSPORT_UNAVAILABLE"),new Metadata());}
            }
            private void release(){if(finished.compareAndSet(false,true)){if(owned!=null)owned.shutdownNow();synchronized(TaskRunTemporalChannelV2.this){if(acquired&&owned==null)reserved--;reap();TaskRunTemporalChannelV2.this.notifyAll();}}}
            @Override public synchronized void request(int n){if(delegate!=null)delegate.request(n);}
            @Override public synchronized void cancel(String message,Throwable cause){cancelled=true;if(delegate!=null)delegate.cancel(message,cause);else release();}
            @Override public synchronized void halfClose(){if(delegate!=null)delegate.halfClose();}
            @Override public synchronized void sendMessage(ReqT message){if(delegate!=null)delegate.sendMessage(message);}
            @Override public synchronized boolean isReady(){return delegate!=null&&delegate.isReady();}
            @Override public synchronized void setMessageCompression(boolean enabled){if(delegate!=null)delegate.setMessageCompression(enabled);}
        };
    }
    @Override public synchronized ManagedChannel shutdown(){shutdown=true;List.copyOf(channels).forEach(ManagedChannel::shutdown);notifyShutdown();notifyAll();return this;}
    @Override public synchronized ManagedChannel shutdownNow(){shutdown=true;List.copyOf(channels).forEach(ManagedChannel::shutdownNow);notifyShutdown();notifyAll();return this;}
    @Override public synchronized boolean isShutdown(){return shutdown;}
    @Override public synchronized boolean isTerminated(){reap();return shutdown&&reserved==0;}
    private void reap(){var iterator=channels.iterator();while(iterator.hasNext())if(iterator.next().isTerminated()){iterator.remove();reserved--;}}
    @Override public boolean awaitTermination(long timeout,TimeUnit unit)throws InterruptedException{
        long remaining=unit.toNanos(timeout),end=System.nanoTime()+remaining;
        synchronized(this){while(!isTerminated()&&remaining>0){TimeUnit.NANOSECONDS.timedWait(this,Math.min(remaining,TimeUnit.MILLISECONDS.toNanos(50)));remaining=end-System.nanoTime();}return isTerminated();}
    }
    @Override public void close(){shutdownNow();try{awaitTermination(maximumCallMillis,TimeUnit.MILLISECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}}
}

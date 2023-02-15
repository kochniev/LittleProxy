package org.littleshoot.proxy.impl;

import com.google.common.collect.ImmutableList;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import java.nio.channels.spi.SelectorProvider;
import java.util.List;

/**
 * Encapsulates the thread pools used by the proxy. Contains the acceptor thread pool as well as the client-to-proxy and
 * proxy-to-server thread pools.
 */
public class ProxyThreadPools {
    /**
     * These {@link EventLoopGroup}s accept incoming connections to the
     * proxies. A different EventLoopGroup is used for each
     * TransportProtocol, since these have to be configured differently.
     */
    private final NioEventLoopGroup clientToProxyAcceptorPool;

    /**
     * These {@link EventLoopGroup}s read incoming requests to the
     * proxies. A different EventLoopGroup is used for each
     * TransportProtocol, since these have to be configured differently.
     */
    private final NioEventLoopGroup clientToProxyWorkerPool;

    /**
     * This bool variable indicate that client to proxy processing pool initialized
     */
    private final boolean separateProcessingEventLoop;

    /**
     * These {@link EventLoopGroup}s process incoming requests to the
     * proxies.
     */
    private final NioEventLoopGroup clientToProxyProcessingPool;

    /**
     * These {@link EventLoopGroup}s are used for making outgoing
     * connections to servers. A different EventLoopGroup is used for each
     * TransportProtocol, since these have to be configured differently.
     */
    private final NioEventLoopGroup proxyToServerWorkerPool;

    public ProxyThreadPools(SelectorProvider selectorProvider, int incomingAcceptorThreads, int incomingWorkerThreads,
          boolean separateProcessingEventLoop, int incomingWorkerProcessingThreads, int outgoingWorkerThreads, 
        String serverGroupName, int serverGroupId) {
        clientToProxyAcceptorPool = new NioEventLoopGroup(incomingAcceptorThreads,
            new CategorizedThreadFactory(serverGroupName, "ClientToProxyAcceptor", serverGroupId), selectorProvider);

        clientToProxyWorkerPool = new NioEventLoopGroup(incomingWorkerThreads,
            new CategorizedThreadFactory(serverGroupName, "ClientToProxyWorker", serverGroupId), selectorProvider);
        clientToProxyWorkerPool.setIoRatio(90);
        
        this.separateProcessingEventLoop = separateProcessingEventLoop;
        this.clientToProxyProcessingPool = initClientToProxyProcessingPool(
                separateProcessingEventLoop, incomingWorkerProcessingThreads,serverGroupName, serverGroupId, selectorProvider);

        proxyToServerWorkerPool = new NioEventLoopGroup(outgoingWorkerThreads,
            new CategorizedThreadFactory(serverGroupName, "ProxyToServerWorker", serverGroupId), selectorProvider);
        proxyToServerWorkerPool.setIoRatio(90);
    }
    
    private NioEventLoopGroup initClientToProxyProcessingPool(
        boolean separateProcessingEventLoop, int incomingWorkerProcessingThreads,
        String serverGroupName, int serverGroupId, SelectorProvider selectorProvider) {
        if (separateProcessingEventLoop) {
            NioEventLoopGroup eventExecutors = new NioEventLoopGroup(incomingWorkerProcessingThreads,
                new CategorizedThreadFactory(
                    serverGroupName, "ClientToProxyProcessorWorker", serverGroupId), selectorProvider);
            eventExecutors.setIoRatio(90);
            return eventExecutors;
        }
        return null;
    }

    /**
     * Returns all event loops (acceptor and worker thread pools) in this pool.
     */
    public List<EventLoopGroup> getAllEventLoops() {
        return ImmutableList.<EventLoopGroup>of(clientToProxyAcceptorPool, clientToProxyWorkerPool, proxyToServerWorkerPool);
    }

    public NioEventLoopGroup getClientToProxyAcceptorPool() {
        return clientToProxyAcceptorPool;
    }

    public NioEventLoopGroup getClientToProxyProcessingPool() {
        return clientToProxyProcessingPool;
    }

    public NioEventLoopGroup getClientToProxyWorkerPool() {
        return clientToProxyWorkerPool;
    }

    public NioEventLoopGroup getProxyToServerWorkerPool() {
        return proxyToServerWorkerPool;
    }

    public boolean isSeparateProcessingEventLoop() {
        return separateProcessingEventLoop;
    }
}

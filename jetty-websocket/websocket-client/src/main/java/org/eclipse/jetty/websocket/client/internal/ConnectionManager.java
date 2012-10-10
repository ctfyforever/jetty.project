//
//  ========================================================================
//  Copyright (c) 1995-2012 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.websocket.client.internal;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.Collections;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;

import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.util.FutureCallback;
import org.eclipse.jetty.util.component.ContainerLifeCycle;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.Scheduler;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.jetty.websocket.client.internal.io.WebSocketClientSelectorManager;
import org.eclipse.jetty.websocket.core.api.UpgradeResponse;
import org.eclipse.jetty.websocket.core.api.WebSocketPolicy;

/**
 * Internal Connection/Client Manager used to track active clients, their physical vs virtual connection information, and provide some means to create new
 * physical or virtual connections.
 */
public class ConnectionManager extends ContainerLifeCycle
{
    private static final Logger LOG = Log.getLogger(ConnectionManager.class);

    public static InetSocketAddress toSocketAddress(URI uri)
    {
        if (!uri.isAbsolute())
        {
            throw new IllegalArgumentException("Cannot get InetSocketAddress of non-absolute URIs");
        }

        int port = uri.getPort();
        String scheme = uri.getScheme().toLowerCase();
        if ("ws".equals(scheme))
        {
            if (port == (-1))
            {
                port = 80;
            }
        }
        else if ("wss".equals(scheme))
        {
            if (port == (-1))
            {
                port = 443;
            }
        }
        else
        {
            throw new IllegalArgumentException("Only support ws:// and wss:// URIs");
        }

        return new InetSocketAddress(uri.getHost(),port);
    }
    private final Queue<WebSocketClient> clients = new ConcurrentLinkedQueue<>();
    private final WebSocketClientSelectorManager selector;

    public ConnectionManager(ByteBufferPool bufferPool, Executor executor, Scheduler scheduler, SslContextFactory sslContextFactory,
            WebSocketPolicy policy)
    {
        // TODO: configure connect timeout
        selector = new WebSocketClientSelectorManager(bufferPool,executor,scheduler,policy);
        selector.setSslContextFactory(sslContextFactory);
        addBean(selector);
    }

    public void addClient(WebSocketClient client)
    {
        clients.add(client);
    }

    private void closeConnections()
    {
        for (WebSocketClient client : clients)
        {
            if (client.getConnection() != null)
            {
                client.getConnection().close();
            }
        }
    }

    public FutureCallback<UpgradeResponse> connectPhysical(DefaultWebSocketClient client) throws IOException
    {
        SocketChannel channel = SocketChannel.open();
        SocketAddress bindAddress = client.getFactory().getBindAddress();
        if (bindAddress != null)
        {
            channel.bind(bindAddress);
        }

        URI wsUri = client.getWebSocketUri();

        channel.socket().setTcpNoDelay(true); // disable nagle
        channel.configureBlocking(false); // async allways

        InetSocketAddress address = toSocketAddress(wsUri);
        LOG.debug("Connect to {}",address);

        channel.connect(address);
        getSelector().connect(channel,client);

        return client;
    }

    public FutureCallback<UpgradeResponse> connectVirtual(WebSocketClient client)
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected void doStop() throws Exception
    {
        closeConnections();
        clients.clear();
        super.doStop();
    }

    public Collection<WebSocketClient> getClients()
    {
        return Collections.unmodifiableCollection(clients);
    }

    public WebSocketClientSelectorManager getSelector()
    {
        return selector;
    }
}

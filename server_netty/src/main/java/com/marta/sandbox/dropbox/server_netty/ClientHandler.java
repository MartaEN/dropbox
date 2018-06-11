package com.marta.sandbox.dropbox.server_netty;

import com.marta.sandbox.authentication.AuthService;
import com.marta.sandbox.dropbox.server_dispatcher.ServerDispatcher;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.json.simple.JSONObject;


import java.net.InetAddress;
import java.nio.file.Path;
import java.util.logging.Logger;

public class ClientHandler extends ChannelInboundHandlerAdapter {

    private final ServerDispatcher SERVER_DISPATCHER;

    ClientHandler(Path serverDirectory, AuthService authService) {
        this.SERVER_DISPATCHER = new ServerDispatcher(serverDirectory, authService);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        Logger.getGlobal().info("CLIENT CONNECTED: " + InetAddress.getLocalHost().getHostName());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        SERVER_DISPATCHER.processMessageFromClient(new ChannelHandlerContextAdapter(ctx), msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        SERVER_DISPATCHER.setAuth(false);
        ctx.flush();
        ctx.close();
    }

}

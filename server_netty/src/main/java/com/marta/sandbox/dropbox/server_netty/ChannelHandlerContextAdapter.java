package com.marta.sandbox.dropbox.server_netty;

import com.marta.sandbox.dropbox.common.messaging.Commands;
import com.marta.sandbox.dropbox.common.settings.ServerConstants;
import com.marta.sandbox.dropbox.common.session.Sender;
import io.netty.channel.ChannelHandlerContext;
import org.json.simple.JSONObject;

import java.io.*;
import java.util.Arrays;

public class ChannelHandlerContextAdapter implements ServerConstants, Sender {

    private ChannelHandlerContext ctx;

    ChannelHandlerContextAdapter(ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public <T> void send(T message) {
        ctx.writeAndFlush(message);
    }

    @Override
    public void sendFileInChunks(File file) {
            Thread t = new Thread (() -> {
                System.out.println(this + ": sending "+file.getName());
                try (InputStream input = new BufferedInputStream(new FileInputStream(file), MAX_OBJ_SIZE)) {
                    byte [] buffer = new byte [MAX_OBJ_SIZE];
                    long size = file.length();
                    int bytesRead;
                    int sequence = 0;
                    while ((bytesRead = input.read(buffer))!=-1) {
                        JSONObject message = new JSONObject();
                        message.put(Commands.MESSAGE, Commands.FILE);
                        message.put(Commands.FILE, file);
                        message.put(Commands.SEQUENCE, ++sequence);
                        message.put(Commands.BYTES_LEFT, size-=bytesRead);
                        if(bytesRead == buffer.length) message.put (Commands.DATA, buffer);
                        else message.put (Commands.DATA, Arrays.copyOf(buffer,bytesRead));
                        send(message);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            t.start();
    }
}

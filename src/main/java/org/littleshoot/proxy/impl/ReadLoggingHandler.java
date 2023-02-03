package org.littleshoot.proxy.impl;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.internal.logging.InternalLogLevel;
import java.net.SocketAddress;
import org.littleshoot.proxy.RequestTracer;

public class ReadLoggingHandler extends LoggingHandler {

  private RequestTracer requestTracer;

  public ReadLoggingHandler(RequestTracer requestTracer) {
    super(LogLevel.DEBUG);
    this.requestTracer = requestTracer;
  }

  @Override
  public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress,
      ChannelPromise promise) throws Exception {
    this.logger.log(InternalLogLevel.INFO, this.format(ctx, "Connected"));
    ctx.connect(remoteAddress, localAddress, promise);
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    this.logger.log(InternalLogLevel.INFO, 
        this.format(ctx, "Read message. trace id " + requestTracer.getCurrentTrace(ctx.channel())));
    ctx.fireChannelRead(msg);
  }

  @Override
  public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
    this.logger.log(InternalLogLevel.INFO,
        this.format(ctx, "Read Complete. trace id " + requestTracer.getCurrentTrace(ctx.channel())));
    ctx.fireChannelReadComplete();
  }
}

package org.littleshoot.proxy.impl;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.util.AttributeKey;

public class TracingAwareHttpRequestEncoder extends HttpRequestEncoder {

  private ChannelHandlerContext ctx;

  public static final AttributeKey<String> IS_FORWARD_PROXY_TRANSPARENT_ATTRIBUTE = AttributeKey
      .valueOf("isForwardProxyTransparent");

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
    this.ctx = ctx;
  }

  @Override
  protected void sanitizeHeadersBeforeEncode(HttpRequest msg, boolean isAlwaysEmpty) {
    HttpHeaders headers = msg.headers();

    if (isForwardProxyTransparent()) {
      // W3C Trace Context
      headers.remove(TraceUtils.HEADER_TRACEPARENT);

      // B3 Tracing - we don't use these anymore but during the migration we are going to keep both
      headers.remove(TraceUtils.HEADER_X_B3_TRACEID);
      headers.remove(TraceUtils.HEADER_X_B3_SPANID);
      headers.remove(TraceUtils.HEADER_X_B3_SAMPLED);
      headers.remove(TraceUtils.HEADER_X_B3_PARENTSPANID);

      // VGS Request ID
      headers.remove(TraceUtils.HEADER_VGS_REQUEST_ID);
    }
  }

  private boolean isForwardProxyTransparent() {
    String isTransparent = ctx.channel().attr(IS_FORWARD_PROXY_TRANSPARENT_ATTRIBUTE).get();
    return "true".equalsIgnoreCase(isTransparent);
  }
}

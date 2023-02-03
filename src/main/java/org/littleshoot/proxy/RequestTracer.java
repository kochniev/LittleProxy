package org.littleshoot.proxy;

import io.netty.channel.Channel;

public interface RequestTracer {


  /**
   * 
   * Start tracing proxy request for this channel and message
   * @param channel
   * @param msg
   */
  void start(Channel channel, Object msg);

  /**
   * Request is served. Finish tracing.
   * @param channel
   * @param msg
   */
  void finish(Channel channel, Object msg);

  /**
   * Current trace in this channel
   * @param channel
   * @return current trace
   */
  String getCurrentTrace(Channel channel);
}

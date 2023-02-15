package org.littleshoot.proxy.impl;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.AttributeKey;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.log4j.LogManager;
import org.eclipse.jetty.util.ConcurrentHashSet;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.littleshoot.proxy.HttpFilters;
import org.littleshoot.proxy.HttpFiltersAdapter;
import org.littleshoot.proxy.HttpFiltersSourceAdapter;
import org.littleshoot.proxy.HttpProxyServer;
import org.littleshoot.proxy.HttpProxyServerBootstrap;
import org.littleshoot.proxy.RequestTracer;
import org.littleshoot.proxy.test.HttpClientUtil;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SeparateProcessingThreadTest {

  private static final Logger log = LoggerFactory.getLogger(SeparateProcessingThreadTest.class);

  private ClientAndServer mockServer;
  private int mockServerPort;

  private HttpProxyServer proxyServer;

  private LogCollectorAppender appender;

  @Before
  public void setUp() {

    appender = (LogCollectorAppender) LogManager.getRootLogger().getAppender("LogCollectorAppender");
    appender.clear();
    mockServer = new ClientAndServer(0);
    mockServerPort = mockServer.getPort();
  }

  @Test
  public void verifyCorrectChannelContextForRequestTest() throws InterruptedException {

    AttributeKey<String> traceKey = AttributeKey.valueOf("trace");
    Set<String> readThreadTraces = new ConcurrentHashSet<>();
    Set<String> clientToProxyRequestTraces = new ConcurrentHashSet<>();
    Set<String> serverToProxyResponseTraces = new ConcurrentHashSet<>();
    Set<String> successResponseTraces = new ConcurrentHashSet<>();
    this.proxyServer = DefaultHttpProxyServer.bootstrap()
        .withPort(0)
        .withThreadPoolConfiguration(new ThreadPoolConfiguration().withSeparateProcessingEventLoop(true))
        .withAcceptorLoggingEnabled(true)
        .withRequestTracer(new RequestTracer() {
          @Override
          public void start(Channel channel, Object msg) {
            if (msg instanceof FullHttpRequest
                && Thread.currentThread().getName().contains("ClientToProxyWorker")
            ) {
              String[] uriSplit = ((FullHttpRequest) msg).uri().split("/");
              String key = uriSplit[uriSplit.length - 1];
              channel.attr(traceKey).set(key);
              readThreadTraces.add(key);
            }
          }

          @Override
          public void finish(Channel channel, Object msg) {
          }

          @Override
          public String getCurrentTrace(Channel channel) {
            return channel.attr(traceKey).get();
          }
        })
        .withFiltersSource(new HttpFiltersSourceAdapter() {
          @Override
          public HttpFilters filterRequest(HttpRequest originalRequest, ChannelHandlerContext ctx) {

            return new HttpFiltersAdapter(originalRequest, ctx) {
              @Override
              public io.netty.handler.codec.http.HttpResponse clientToProxyRequest(HttpObject httpObject) {
                if (httpObject instanceof FullHttpRequest
                    && Thread.currentThread().getName().contains("ClientToProxyProcessorWorker")
                ) {
                  String[] uriSplit = ((FullHttpRequest) httpObject).uri().split("/");
                  String key = this.ctx.channel().attr(traceKey).get();
                  Assert.assertEquals(uriSplit[uriSplit.length - 1], key);
                  clientToProxyRequestTraces.add(key);
                  log.info(String.format("Client to proxy request increment counter: %s", key));
                }
                return super.clientToProxyRequest(httpObject);
              }

              @Override
              public HttpObject proxyToClientResponse(HttpObject httpObject) {
                String key = ((FullHttpResponse) httpObject).headers().get("key");
                serverToProxyResponseTraces.add(key);
                log.info(String.format("Server to proxy response increment counter: %s", key));
                return super.proxyToClientResponse(httpObject);
              }
            };
          }

          @Override
          public int getMaximumRequestBufferSizeInBytes() {
            return 1000000;
          }

          @Override
          public int getMaximumResponseBufferSizeInBytes() {
            return 1000000;
          }
        })
        .start();

    int requestsAmount = 1000;
    CountDownLatch countDownLatch = new CountDownLatch(requestsAmount);
    Executor executor = Executors.newWorkStealingPool(20);

    for (int i = 0; i < requestsAmount; i++) {
      executor.execute(new Runnable() {
        @Override
        @SneakyThrows
        public void run() {
          String key = RandomStringUtils.randomAlphabetic(10);
          mockServer.when(org.mockserver.model.HttpRequest.request()
                  .withMethod("POST")
                  .withPath("/traceRequest/" + key))
              .respond(HttpResponse.response()
                  .withStatusCode(200)
                  .withHeader("key", key)
                  .withBody("{\"status\":\"OK\"}"));
          Thread.sleep(100);
          org.apache.http.HttpResponse response = HttpClientUtil.performHttpPost(
              "http://localhost:" + mockServerPort + "/traceRequest/" + key, 100, proxyServer);
          if (200 == response.getStatusLine().getStatusCode()) {
            successResponseTraces.add(key);
            log.info(String.format("Success response for trace: %s", key));
            countDownLatch.countDown();
          }
        }
      });
      if (i % 100 == 0) {
        Thread.sleep(500);
      }
    }
    countDownLatch.await(20, TimeUnit.SECONDS);
    Assert.assertEquals(requestsAmount, readThreadTraces.size());
    Assert.assertEquals(requestsAmount, clientToProxyRequestTraces.size());
    Assert.assertEquals(requestsAmount, serverToProxyResponseTraces.size());
    Assert.assertEquals(requestsAmount, successResponseTraces.size());

    Set<String> readMessageLogs = appender.getLogsContains("Read message. trace id");
    Assert.assertEquals(requestsAmount, readMessageLogs.size());
  }


  @Test
  public void readingHappenedSeparateOfProcessingTest() throws Exception {
    AttributeKey<String> traceKey = AttributeKey.valueOf("trace");
    Set<String> readThreadTraces = new ConcurrentHashSet<>();
    Set<String> clientToProxyRequestTraces = new ConcurrentHashSet<>();

    this.proxyServer = DefaultHttpProxyServer.bootstrap()
        .withPort(0)
        .withThreadPoolConfiguration(new ThreadPoolConfiguration().withSeparateProcessingEventLoop(true))
        .withAcceptorLoggingEnabled(true)
        .withRequestTracer(new RequestTracer() {
          @Override
          public void start(Channel channel, Object msg) {
            if (msg instanceof FullHttpRequest && Thread.currentThread().getName().contains("ClientToProxyWorker")) {
              String[] uriSplit = ((FullHttpRequest) msg).uri().split("/");
              String key = uriSplit[uriSplit.length - 1];
              channel.attr(traceKey).set(key);
              readThreadTraces.add(key);
              log.info(String.format("Read request increment counter: %s", key));
            }
          }

          @Override
          public void finish(Channel channel, Object msg) {
          }

          @Override
          public String getCurrentTrace(Channel channel) {
            return channel.attr(traceKey).get();
          }
        })
        .withFiltersSource(new HttpFiltersSourceAdapter() {
          @Override
          public HttpFilters filterRequest(HttpRequest originalRequest, ChannelHandlerContext ctx) {

            return new HttpFiltersAdapter(originalRequest, ctx) {
              @Override
              public io.netty.handler.codec.http.HttpResponse clientToProxyRequest(HttpObject httpObject) {
                if (httpObject instanceof FullHttpRequest) {
                  String[] uriSplit = ((FullHttpRequest) httpObject).uri().split("/");
                  String key = this.ctx.channel().attr(traceKey).get();
                  Assert.assertEquals(uriSplit[uriSplit.length - 1], key);
                  clientToProxyRequestTraces.add(key);
                  log.info(String.format("Client to proxy request increment counter: %s", key));
                  try {
                    // block thread
                    Thread.sleep(10000);
                  } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                  }
                }
                return super.clientToProxyRequest(httpObject);
              }
            };
          }

          @Override
          public int getMaximumRequestBufferSizeInBytes() {
            return 1000000;
          }

          @Override
          public int getMaximumResponseBufferSizeInBytes() {
            return 1000000;
          }
        })
        .start();

    int requestsAmount = 20;
    CountDownLatch countDownLatch = new CountDownLatch(requestsAmount);
    Executor executor = Executors.newWorkStealingPool(20);

    for (int i = 0; i < requestsAmount; i++) {
      executor.execute(new Runnable() {
        @Override
        @SneakyThrows
        public void run() {
          String key = RandomStringUtils.randomAlphabetic(10);
          mockServer.when(org.mockserver.model.HttpRequest.request()
                  .withMethod("POST")
                  .withPath("/traceRequest/" + key))
              .respond(HttpResponse.response()
                  .withStatusCode(200)
                  .withHeader("key", key)
                  .withBody("{\"status\":\"OK\"}"));
          Thread.sleep(100);
          org.apache.http.HttpResponse response = HttpClientUtil.performHttpPost(
              "http://localhost:" + mockServerPort + "/traceRequest/" + key, 100, proxyServer);
          log.info(String.format("Success response for trace: %s", key));
          countDownLatch.countDown();
        }
      });
    }
    countDownLatch.await(2, TimeUnit.SECONDS);
    Assert.assertEquals(requestsAmount, readThreadTraces.size());
    Assert.assertEquals(ServerGroup.DEFAULT_INCOMING_WORKER_THREADS, clientToProxyRequestTraces.size());

    Set<String> readMessageLogs = appender.getLogsContains("Read message. trace id");
    Assert.assertEquals(requestsAmount, readMessageLogs.size());
  }

  @Test
  public void testDefaultAmountOfProcessingThreads() throws InterruptedException {
    int defaultProcessingThreads = 8;
    this.proxyServer = getDefaultBootstrap()
        .withThreadPoolConfiguration(new ThreadPoolConfiguration().withSeparateProcessingEventLoop(true))
        .start();
    sendRequests(20);
    Assert.assertEquals(defaultProcessingThreads, appender.getThreadNames()
        .stream()
        .filter(v -> v.contains("ClientToProxyProcessorWorker"))
        .collect(Collectors.toSet()).size());
  }

  @Test
  public void testCustomAmountOfProcessingThreads() throws InterruptedException {
    int processingThreadsAmount = 12;
    this.proxyServer = getDefaultBootstrap()
        .withThreadPoolConfiguration(new ThreadPoolConfiguration()
            .withSeparateProcessingEventLoop(true)
            .withClientToProxyWorkerProcessingThreads(processingThreadsAmount))
        .start();
    sendRequests(30);
    Assert.assertEquals(processingThreadsAmount, appender.getThreadNames()
        .stream()
        .filter(v -> v.contains("ClientToProxyProcessorWorker"))
        .collect(Collectors.toSet()).size());
  }

  private HttpProxyServerBootstrap getDefaultBootstrap() {
    return DefaultHttpProxyServer.bootstrap()
        .withPort(0)
        .withAcceptorLoggingEnabled(true)
        .withFiltersSource(new HttpFiltersSourceAdapter() {
          @Override
          public HttpFilters filterRequest(HttpRequest originalRequest, ChannelHandlerContext ctx) {
            return new HttpFiltersAdapter(originalRequest, ctx) {
              @Override
              public io.netty.handler.codec.http.HttpResponse clientToProxyRequest(HttpObject httpObject) {
                log.info("clientToProxyRequest");
                return super.clientToProxyRequest(httpObject);
              }
            };
          }

          @Override
          public int getMaximumRequestBufferSizeInBytes() {
            return 1000000;
          }

          @Override
          public int getMaximumResponseBufferSizeInBytes() {
            return 1000000;
          }
        });
  }

  private void sendRequests(int requestsAmount) throws InterruptedException {
    CountDownLatch countDownLatch = new CountDownLatch(requestsAmount);
    Executor executor = Executors.newWorkStealingPool(20);
    mockServer.when(org.mockserver.model.HttpRequest.request()
            .withMethod("POST")
            .withPath("/traceRequest/"))
        .respond(HttpResponse.response()
            .withStatusCode(200)
            .withBody("{\"status\":\"OK\"}"));
    for (int i = 0; i < requestsAmount; i++) {
      executor.execute(() -> {
        HttpClientUtil.performHttpPost(
            "http://localhost:" + mockServerPort + "/traceRequest/", 100, proxyServer);
        countDownLatch.countDown();
      });
    }
    countDownLatch.await(2, TimeUnit.SECONDS);
  }


}

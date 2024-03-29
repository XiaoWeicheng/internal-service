package com.aliware.tianchi.netty;

import com.aliware.tianchi.HashInterface;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.util.CharsetUtil;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.dubbo.common.Constants;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.listener.CallbackListener;
import org.apache.dubbo.rpc.service.CallbackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

@ChannelHandler.Sharable
public class HttpProcessHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpProcessHandler.class);
    private final ApplicationConfig application = new ApplicationConfig();
    private volatile boolean init = false;
    private HashInterface hashInterface;
    private String salt = System.getProperty("salt");

    HttpProcessHandler() {
        this.hashInterface = getServiceStub();
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) throws InterruptedException {
        String content = RandomStringUtils.randomAlphanumeric(16);
        int expected = (content + salt).hashCode();
        if (!init) {
            init();
        }

        AtomicInteger okCount = new AtomicInteger();
        AtomicInteger errCount = new AtomicInteger();

        int count = 10;

        CountDownLatch countDownLatch = new CountDownLatch(count);

        long start = System.currentTimeMillis();

        IntStream.range(0, count).parallel().forEach(value -> {
            hashInterface.hash(content);
            RpcContext.getContext().getCompletableFuture().whenComplete((actual, t) -> {
                if (t == null && Objects.equals(expected, actual)) {
                    okCount.incrementAndGet();
                } else {
                    errCount.incrementAndGet();
                    LOGGER.error("Request result:failure", t);
                }
                countDownLatch.countDown();
            });
        });


        countDownLatch.await(count, TimeUnit.MILLISECONDS);

        long millis = System.currentTimeMillis() - start;

        String contentString = "OK:" + okCount.get() + "\nError:" + errCount.get() + "\nCost:" + millis / 1000+"."
                + millis % 1000;
        System.out.println(contentString);
        FullHttpResponse ok = new DefaultFullHttpResponse(HTTP_1_1, OK,
                Unpooled.copiedBuffer(contentString, CharsetUtil.UTF_8));
        ok.headers().add(HttpHeaderNames.CONTENT_LENGTH, contentString.length());
        ctx.writeAndFlush(ok);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("Channel error", cause);
        ctx.close();
    }

    private List<URL> buildUrls(String interfaceName, Map<String, String> attributes) {
        List<URL> urls = new ArrayList<>();
        // 配置直连的 provider 列表
        urls.add(new URL(Constants.DUBBO_PROTOCOL, "127.0.0.1", 20880, interfaceName, attributes));
//        urls.add(new URL(Constants.DUBBO_PROTOCOL, "127.0.0.1", 20870, interfaceName, attributes));
//        urls.add(new URL(Constants.DUBBO_PROTOCOL, "127.0.0.1", 20890, interfaceName, attributes));
        return urls;
    }

    private HashInterface getServiceStub() {
        application.setName("service-gateway");

        // 直连方式，不使用注册中心
        RegistryConfig registry = new RegistryConfig();
        registry.setAddress("N/A");

        ReferenceConfig<HashInterface> reference = new ReferenceConfig<>();
        reference.setApplication(application);
        reference.setRegistry(registry);
        reference.setInterface(HashInterface.class);
        List<URL> urls = reference.toUrls();
        Map<String, String> attributes = new HashMap<>();
        attributes.put("loadbalance", "user");
        attributes.put("async", "true");
        attributes.put(Constants.HEARTBEAT_KEY, "0");
        attributes.put(Constants.RECONNECT_KEY, "false");
        urls.addAll(buildUrls(HashInterface.class.getName(), attributes));
        return reference.get();
    }

    private synchronized void init() {
        if (init) {
            return;
        }

        init = true;

        initThrash();

        initCallbackListener();
    }

    private void initThrash() {
        List<URL> urls = buildUrls(HashInterface.class.getName(), new HashMap<>());
        for (URL url : urls) {
            RpcContext.getContext().setUrl(url);
            hashInterface.hash("hey");
            CompletableFuture<Integer> result = RpcContext.getContext().getCompletableFuture();
            result.whenComplete((a, t) -> {
                if (t == null) {
                    LOGGER.info("Init hash service successful. address:{} result:{}", url.getAddress(), a, t);
                } else {
                    // LOGGER.error("Init hash service failed. address:{} ", url.getAddress(), t);
                }
            });
        }
    }

    private void initCallbackListener() {
        Set<String> supportedExtensions = ExtensionLoader.getExtensionLoader(CallbackListener.class)
                .getSupportedExtensions();
        if (!supportedExtensions.isEmpty()) {
            Map<String, String> attributes = new HashMap<>();
            attributes.put("addListener.1.callback", "true");
            attributes.put("callbacks", "1000");
            attributes.put("connections", "1");
            attributes.put("dubbo", "2.0.2");
            attributes.put("dynamic", "true");
            attributes.put("generic", "false");
            attributes.put("interface", "org.apache.dubbo.rpc.service.CallbackService");
            attributes.put("methods", "addListener");
            attributes.put(Constants.HEARTBEAT_KEY, "0");
            attributes.put(Constants.RECONNECT_KEY, "false");

            for (String supportedExtension : supportedExtensions) {
                List<URL> urls = buildUrls(CallbackService.class.getName(), attributes);
                for (URL url : urls) {
                    CallbackListener extension = ExtensionLoader.getExtensionLoader(CallbackListener.class)
                            .getExtension(supportedExtension);

                    ReferenceConfig<CallbackService> reference = new ReferenceConfig<>();
                    reference.setApplication(application);
                    reference.setInterface(CallbackService.class);

                    reference.toUrls().add(url);
                    try {
                        reference.get().addListener("env.listener", extension);
                    } catch (Throwable t) {
                        // LOGGER.error("Init callback listener failed. url:{}", url, t);
                    }
                    LOGGER.info("Init callback listener successful. extension:{} address:{}",
                            extension.getClass().getSimpleName(), url.getAddress());
                }
            }
        }
    }
}

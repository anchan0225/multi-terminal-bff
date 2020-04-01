package net.multi.terminal.bff.core;

import com.netflix.hystrix.*;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import lombok.extern.slf4j.Slf4j;
import net.multi.terminal.bff.config.HystrixConfig;
import net.multi.terminal.bff.core.apiname.ApiIdentity;
import net.multi.terminal.bff.core.codec.ApiCodec;
import net.multi.terminal.bff.core.codec.CommonMsg;
import net.multi.terminal.bff.core.serializer.MsgSerializer;
import net.multi.terminal.bff.exception.ApiException;
import net.multi.terminal.bff.model.ApiRsp;
import net.multi.terminal.bff.model.ClientContext;

import static net.multi.terminal.bff.core.util.NettyUtil.buildResponse;
import static net.multi.terminal.bff.core.util.NettyUtil.send;

/**
 * Api断路器和水密舱，用于防止后台系统宕机造成的雪崩现象
 */
@Slf4j
public class ApiHystrixCommand extends HystrixCommand<Void> {
    private final ChannelHandlerContext nettyCtx;
    private final FullHttpRequest httpRequest;
    private final ApiIdentity identity;
    private final ClientContext clientContext;
    private ApiCoreProccessor proccessor;

    public ApiHystrixCommand(ApiIdentity identity, ClientContext clientContext, ApiCoreProccessor proccessor, ChannelHandlerContext nettyCtx, FullHttpRequest httpRequest, HystrixConfig hystrixConfig) {
        super(Setter
                .withGroupKey(HystrixCommandGroupKey.Factory.asKey("API"))
                .andCommandKey(HystrixCommandKey.Factory.asKey(identity.getApiName()))
                .andCommandPropertiesDefaults(HystrixCommandProperties
                        .Setter()
                        .withCircuitBreakerEnabled(hystrixConfig.getCircuitBreakerEnabled())
                        .withCircuitBreakerRequestVolumeThreshold(hystrixConfig.getCircuitBreakerRequestVolumeThreshold())
                        .withCircuitBreakerErrorThresholdPercentage(hystrixConfig.getCircuitBreakerErrorThresholdPercentage())
                        .withCircuitBreakerSleepWindowInMilliseconds(hystrixConfig.getCircuitBreakerSleepWindowInMilliseconds())
                )
        );
        this.nettyCtx = nettyCtx;
        this.httpRequest = httpRequest;
        this.identity = identity;
        this.proccessor = proccessor;
        this.clientContext = clientContext;
    }

    @Override
    protected Void run() throws Exception {
        proccessor.process(identity, nettyCtx, httpRequest);
        return null;
    }


    @Override
    protected Void getFallback() {
        Throwable throwable = getFailedExecutionException();
        if (throwable != null) {
            if (throwable instanceof ApiException) {
                ApiException apiException = (ApiException) throwable;
                send(nettyCtx, throwable.getMessage(), buildResponse(apiException.getHttpResponseStatus(), clientContext.getRspContentType()));
            }else{
                send(nettyCtx, throwable.getMessage(), buildResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR, clientContext.getRspContentType()));
            }
        } else {
            send(nettyCtx, "", buildResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR, clientContext.getRspContentType()));
        }
        return null;
    }
}

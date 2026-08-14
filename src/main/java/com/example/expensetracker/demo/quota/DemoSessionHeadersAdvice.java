package com.example.expensetracker.demo.quota;

import com.example.expensetracker.demo.security.DemoPrincipal;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@ControllerAdvice
public class DemoSessionHeadersAdvice implements ResponseBodyAdvice<Object> {

    public static final String ACTIONS_LIMIT = "Demo-Actions-Limit";
    public static final String ACTIONS_REMAINING = "Demo-Actions-Remaining";
    public static final String SESSION_EXPIRES_AT = "Demo-Session-Expires-At";

    private final ObjectProvider<DemoQuotaService> quotaServiceProvider;

    public DemoSessionHeadersAdvice(ObjectProvider<DemoQuotaService> quotaServiceProvider) {
        this.quotaServiceProvider = quotaServiceProvider;
    }

    @Override
    public boolean supports(
        MethodParameter returnType,
        Class<? extends HttpMessageConverter<?>> converterType
    ) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(
        Object body,
        MethodParameter returnType,
        MediaType selectedContentType,
        Class<? extends HttpMessageConverter<?>> selectedConverterType,
        ServerHttpRequest request,
        ServerHttpResponse response
    ) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication != null && authentication.getPrincipal() instanceof DemoPrincipal demo)
            || !isSuccessful(response)
            || isLogout(request)) {
            return body;
        }

        DemoQuotaService.QuotaSnapshot quota = quotaServiceProvider.getObject().current(demo.sessionId());
        response.getHeaders().set(ACTIONS_LIMIT, Integer.toString(quota.limit()));
        response.getHeaders().set(ACTIONS_REMAINING, Integer.toString(quota.remaining()));
        response.getHeaders().set(SESSION_EXPIRES_AT, quota.expiresAt().toString());
        return body;
    }

    private boolean isSuccessful(ServerHttpResponse response) {
        return !(response instanceof ServletServerHttpResponse servletResponse)
            || servletResponse.getServletResponse().getStatus() < 400;
    }

    private boolean isLogout(ServerHttpRequest request) {
        return request != null
            && HttpMethod.DELETE.equals(request.getMethod())
            && "/api/demo/sessions/current".equals(request.getURI().getPath());
    }
}

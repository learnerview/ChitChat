package com.learnerview.chitchat.common.tenancy;

import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WebSocketTenantHandshakeInterceptorTest {

    private final WebSocketTenantHandshakeInterceptor interceptor = new WebSocketTenantHandshakeInterceptor();

    @Test
    void acceptsTheTenantHeader() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/ws");
        servletRequest.addHeader(TenantHeaderFilter.TENANT_HEADER, "tenant-1");
        Map<String, Object> attributes = new HashMap<>();

        boolean allowed = interceptor.beforeHandshake(new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(new MockHttpServletResponse()), null, attributes);

        assertThat(allowed).isTrue();
        assertThat(attributes.get("tenantId")).isEqualTo("tenant-1");
    }

    @Test
    void fallsBackToTheTenantQueryParameterForBrowserClients() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/ws");
        servletRequest.setParameter("tenantId", "tenant-2");
        Map<String, Object> attributes = new HashMap<>();

        boolean allowed = interceptor.beforeHandshake(new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(new MockHttpServletResponse()), null, attributes);

        assertThat(allowed).isTrue();
        assertThat(attributes.get("tenantId")).isEqualTo("tenant-2");
    }

    @Test
    void rejectsWhenNoTenantIsProvided() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/ws");

        boolean allowed = interceptor.beforeHandshake(new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(new MockHttpServletResponse()), null, new HashMap<>());

        assertThat(allowed).isFalse();
    }
}

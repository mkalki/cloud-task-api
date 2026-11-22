package com.mkalki.cloudtaskapi.requestlimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.unit.DataSize;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RequestSizeLimitFilterTest {

    private static final int LIMIT = 100;

    private final RequestSizeLimitFilter filter = new RequestSizeLimitFilter(new RequestSizeLimitProperties(List.of(
            new RequestSizeLimitProperties.Rule("POST", "/api/v1/jobs", DataSize.ofBytes(LIMIT)),
            new RequestSizeLimitProperties.Rule(null, "/api/v1/tasks/**", DataSize.ofBytes(10))
    )));

    @Test
    void declaredContentLengthAboveTheLimitIsRejectedWithoutReadingTheBody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/jobs");
        request.setContent(new byte[LIMIT + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).isEqualTo("Request body exceeds 100 bytes");
        assertThat(chain.getRequest()).as("controller must not run").isNull();
    }

    @Test
    void chunkedBodyWithoutContentLengthIsCappedWhileReading() throws Exception {
        MockHttpServletRequest request = chunked("POST", "/api/v1/jobs", new byte[LIMIT * 10]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void bodyExactlyAtTheLimitIsPassedOnUnchanged() throws Exception {
        byte[] body = "x".repeat(LIMIT).getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = chunked("POST", "/api/v1/jobs", body);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
        assertThat(chain.getRequest().getInputStream().readAllBytes()).isEqualTo(body);
        assertThat(chain.getRequest().getContentLengthLong()).isEqualTo(LIMIT);
    }

    @Test
    void requestsThatMatchNoRuleAreNotLimited() throws Exception {
        assertPassesThrough(new MockHttpServletRequest("GET", "/api/v1/jobs"), LIMIT * 10);
        assertPassesThrough(new MockHttpServletRequest("POST", "/api/v1/jobs/abc/cancel"), LIMIT * 10);
        assertPassesThrough(new MockHttpServletRequest("POST", "/api/v1/auth/login"), LIMIT * 10);
    }

    @Test
    void eachPathUsesItsOwnLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/tasks/7");
        request.setContent(new byte[11]);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).isEqualTo("Request body exceeds 10 bytes");
    }

    @Test
    void defaultConfigurationLimitsJobSubmissionOnly() {
        RequestSizeLimitProperties defaults = new RequestSizeLimitProperties(null);

        assertThat(defaults.rules()).containsExactly(
                new RequestSizeLimitProperties.Rule("POST", "/api/v1/jobs", DataSize.ofKilobytes(72)));
    }

    private void assertPassesThrough(MockHttpServletRequest request, int bodySize) throws Exception {
        request.setContent(new byte[bodySize]);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    /** A request that, like a chunked upload, does not declare its length. */
    private static MockHttpServletRequest chunked(String method, String uri, byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri) {
            @Override
            public int getContentLength() {
                return -1;
            }

            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setContent(body);
        return request;
    }
}

package io.github.leobej.infrastructure.web;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

// The limits run with a clock the test moves by hand, so a window passes in no time. The suites
// that boot the app switch the filter off: they all log in from 127.0.0.1.
class RateLimitFilterTest {

    private final AtomicLong now = new AtomicLong();
    private final RateLimiter limiter = new RateLimiter(now::get);
    private final RateLimitFilter filter = new RateLimitFilter(limiter, JsonMapper.builder().build());

    @Test
    void loginAllowsAHandfulOfAttemptsAMinuteFromOneAddress() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) {
            assertThat(post("/api/v1/auth/login", "203.0.113.7").getStatus()).isEqualTo(200);
        }

        MockHttpServletResponse refused = post("/api/v1/auth/login", "203.0.113.7");
        assertThat(refused.getStatus()).isEqualTo(429);
        assertThat(Long.parseLong(refused.getHeader("Retry-After"))).isBetween(1L, 60L);
        assertThat(JsonPath.<String>read(refused.getContentAsString(), "$.error.code")).isEqualTo("RATE_LIMITED");
    }

    @Test
    void eachAddressHasItsOwnBudget() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) {
            post("/api/v1/auth/login", "203.0.113.7");
        }
        assertThat(post("/api/v1/auth/login", "203.0.113.7").getStatus()).isEqualTo(429);
        assertThat(post("/api/v1/auth/login", "198.51.100.1").getStatus()).isEqualTo(200);
    }

    @Test
    void theBudgetRefillsOverTheWindow() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) {
            post("/api/v1/auth/login", "203.0.113.7");
        }
        assertThat(post("/api/v1/auth/login", "203.0.113.7").getStatus()).isEqualTo(429);

        // Ten a minute refills one every six seconds.
        now.addAndGet(Duration.ofSeconds(6).toNanos());
        assertThat(post("/api/v1/auth/login", "203.0.113.7").getStatus()).isEqualTo(200);
        assertThat(post("/api/v1/auth/login", "203.0.113.7").getStatus()).isEqualTo(429);
    }

    @Test
    void theEmailSendingEndpointsAreTheTightest() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(post("/api/v1/auth/forgot-password", "203.0.113.7").getStatus()).isEqualTo(200);
        }
        assertThat(post("/api/v1/auth/forgot-password", "203.0.113.7").getStatus()).isEqualTo(429);
        // A separate budget per endpoint: being shut out of one does not lock you out of login.
        assertThat(post("/api/v1/auth/login", "203.0.113.7").getStatus()).isEqualTo(200);
    }

    @Test
    void everythingElseIsLeftAlone() throws Exception {
        for (int attempt = 0; attempt < 50; attempt++) {
            assertThat(post("/api/v1/workspaces", "203.0.113.7").getStatus()).isEqualTo(200);
        }
        MockHttpServletRequest get = new MockHttpServletRequest("GET", "/api/v1/auth/login");
        get.setRemoteAddr("203.0.113.7");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(get, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void idleAddressesAreForgotten() throws Exception {
        post("/api/v1/auth/login", "203.0.113.7");
        assertThat(limiter.size()).isEqualTo(1);

        now.addAndGet(Duration.ofHours(1).toNanos());
        filter.evictIdleBuckets();
        assertThat(limiter.size()).isZero();
    }

    private MockHttpServletResponse post(String path, String address) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(address);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}

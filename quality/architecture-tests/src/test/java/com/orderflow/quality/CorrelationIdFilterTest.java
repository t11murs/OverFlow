package com.orderflow.quality;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorrelationIdFilterTest {
    private static final String HEADER = "X-Correlation-Id";

    @Test
    void preservesSafeIncomingCorrelationIdAndMakesItAvailableInMdc() throws Exception {
        Filter filter = createFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> valueInsideChain = new AtomicReference<>();
        request.addHeader(HEADER, "checkout-42:attempt-1");

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                valueInsideChain.set(MDC.get("correlationId")));

        assertThat(response.getHeader(HEADER)).isEqualTo("checkout-42:attempt-1");
        assertThat(valueInsideChain).hasValue("checkout-42:attempt-1");
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void replacesUnsafeIncomingCorrelationId() throws Exception {
        Filter filter = createFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(HEADER, "value with spaces");

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> { });

        assertThat(response.getHeader(HEADER))
                .isNotEqualTo(request.getHeader(HEADER))
                .satisfies(value -> UUID.fromString(value));
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void restoresPreviousMdcValueWhenRequestFails() throws Exception {
        Filter filter = createFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MDC.put("correlationId", "outer-request");
        try {
            assertThatThrownBy(() -> filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
                throw new IllegalStateException("failure");
            })).isInstanceOf(IllegalStateException.class);

            assertThat(MDC.get("correlationId")).isEqualTo("outer-request");
        } finally {
            MDC.remove("correlationId");
        }
    }

    private static Filter createFilter() throws ReflectiveOperationException {
        String rootPackage = System.getProperty("orderflow.architecture.root-package");
        return (Filter) Class.forName(rootPackage + ".config.CorrelationIdFilter")
                .getConstructor()
                .newInstance();
    }
}

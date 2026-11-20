package com.travel2go.agent;

import com.travel2go.agent.client.BookingStatusClient;
import com.travel2go.agent.client.TripQuoteClient;
import com.travel2go.agent.client.WaitlistClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract test: every agent endpoint the app actually exposes is documented in the
 * published OpenAPI spec, with the matching HTTP method. This is what keeps the spec
 * in sync - add an endpoint without documenting it and this fails.
 */
@SpringBootTest
class OpenApiContractTest {

    @MockitoBean private WaitlistClient waitlistClient;
    @MockitoBean private BookingStatusClient bookingStatusClient;
    @MockitoBean private TripQuoteClient tripQuoteClient;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    @SuppressWarnings("unchecked")
    void everyAgentEndpointIsDocumented() throws Exception {
        Map<String, Object> spec;
        try (InputStream in = new ClassPathResource("openapi/agent-api-v1.yaml").getInputStream()) {
            spec = new Yaml().load(in);
        }
        Map<String, Object> documentedPaths = (Map<String, Object>) spec.get("paths");
        assertThat(documentedPaths).isNotNull();

        int checked = 0;
        for (Map.Entry<RequestMappingInfo, ?> entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            Set<String> patterns = info.getPathPatternsCondition() == null
                    ? Set.of() : info.getPathPatternsCondition().getPatternValues();

            for (String path : patterns) {
                if (!path.startsWith("/api/agent/v1")) {
                    continue; // actuator / error dispatch etc.
                }
                if (path.equals("/api/agent/v1/openapi.yaml")) {
                    continue; // the spec-serving endpoint documents the API, not itself
                }

                assertThat(documentedPaths)
                        .as("OpenAPI spec is missing path %s", path)
                        .containsKey(path);

                Map<String, Object> ops = (Map<String, Object>) documentedPaths.get(path);
                for (var method : info.getMethodsCondition().getMethods()) {
                    String verb = method.name().toLowerCase();
                    assertThat(ops)
                            .as("OpenAPI spec is missing %s %s", verb, path)
                            .containsKey(verb);
                    checked++;
                }
            }
        }
        // sanity: we actually exercised the four agent operations
        assertThat(checked).isGreaterThanOrEqualTo(4);
    }
}

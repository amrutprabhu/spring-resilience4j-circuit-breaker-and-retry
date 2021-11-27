package com.amrut.prabhu;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.RequestPattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
public class CircuitBreakerAndRetryTest {

    @Autowired
    private MockMvc mockMvc;
    private static WireMockServer server = new WireMockServer();

    static {
        server.start();
    }

    @DynamicPropertySource
    static void propertiesSources(DynamicPropertyRegistry dynamicPropertyRegistry) {
        dynamicPropertyRegistry.add("service2.url", server::baseUrl);
    }

    @Test
    void testCircuitBreakerWithRetryScenario() throws Exception {

        makeServerReturn200Response();

        int serverHitCount = 0;
        for (int i = 1; i <= 7; i++) {
            if (i == 5) {
                makeServerReturn500Response();
            }

            MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.get("/"))
                    .andReturn();

            if (i >= 5) {
                assertThat(mvcResult.getResponse().getContentAsString()).isEqualTo("fallback value");
                // Since the server is failing, the server should be contacted 3 times because of the retry.
                serverHitCount = serverHitCount + 3;
            } else {
                assertThat(mvcResult.getResponse().getContentAsString()).isEqualTo("Response From Server");
                // the server should be contacted only once
                serverHitCount++;

            }
            assertThat(server.countRequestsMatching(RequestPattern.everything())
                    .getCount())
                    .isEqualTo(serverHitCount);
        }

        // At this stage, the circuit breaker should be in OPEN state because of 3 consecutive failures
        // which satisfies 60% failure threshold.
        // Out of 5 attempts if 3 attempts fail (60%), then circuit breaker is opened

        mockMvc.perform(MockMvcRequestBuilders.get("/"))
                .andExpect(MockMvcResultMatchers
                        .content()
                        .string("fallback value"));

        // circuit breaker should be in Open state. So server hit count should not change.
        assertThat(server.countRequestsMatching(RequestPattern.everything())
                .getCount())
                .isEqualTo(serverHitCount);

        // waiting for circuit breaker to pass wait time i.e 1 minute and move to half_open state
        long min1 = 100000l;
        System.out.println("Waiting for 1 min");
        Thread.sleep(min1);

        // At this stage, the circuit breker should be in HALF_OPEN state
        // and will evaluate the next 3 calls to move to OPEN or CLOSED state.
        for (int i = 1; i <= 5; i++) {

            if (i == 3) {
                // bringing the server back for the third call
                makeServerReturn200Response();
            }

            MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.get("/"))
                    .andReturn();

            if (i == 3) {
                // because the server is up, So should hit only once
                serverHitCount++;
                assertThat(mvcResult.getResponse().getContentAsString())
                        .isEqualTo("Response From Server");
            } else if (i >= 4) {
                //The circuit breaker changed from HALF_OPEN to OPEN at this stage
                // Reason : During the 3 permitted calls during HALF_OPEN state,
                // 2 failed out of 3 failed,
                // There by moving the circuit breaker back to OPEN state
                // and the server is not hit.

                assertThat(mvcResult.getResponse().getContentAsString())
                        .isEqualTo("fallback value");

            } else {
                // server is down. So should retry 3 times.
                serverHitCount = serverHitCount + 3;
                assertThat(mvcResult.getResponse().getContentAsString())
                        .isEqualTo("fallback value");
            }
            assertThat(server.countRequestsMatching(RequestPattern.everything()).getCount()).isEqualTo(serverHitCount);
        }

    }

    private static void makeServerReturn200Response() {
        server.stubFor(WireMock.get("/")
                .willReturn(ResponseDefinitionBuilder.responseDefinition()
                        .withBody("Response From Server")
                        .withStatus(200)));
    }

    private void makeServerReturn500Response() {
        server.stubFor(WireMock.get("/")
                .willReturn(ResponseDefinitionBuilder
                        .responseDefinition()
                        .withStatus(500)));
    }
}

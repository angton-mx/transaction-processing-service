package io.github.angtonmx.transactionprocessing.transaction.provider;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.github.tomakehurst.wiremock.WireMockServer;

import io.github.angtonmx.transactionprocessing.transaction.ProviderResult;
import io.github.angtonmx.transactionprocessing.transaction.ProviderStatus;
import io.github.angtonmx.transactionprocessing.transaction.Transaction;
import io.github.angtonmx.transactionprocessing.transaction.TransactionType;

class HttpTransactionProviderTest {

    private static final String EXECUTE_PATH = "/provider/v1/execute";
    private static final String APPROVED_RESPONSE = """
            {
              "transactionId": "provider-transaction-123",
              "status": "APPROVED",
              "balance": 8500.00,
              "executedAt": "2026-08-09T20:00:00Z"
            }
            """;

    private WireMockServer wireMock;
    private HttpTransactionProvider provider;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();

        RestClient restClient = RestClient.builder()
                .requestFactory(new SimpleClientHttpRequestFactory())
                .baseUrl(wireMock.baseUrl())
                .build();
        provider = new HttpTransactionProvider(restClient);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void sendsExpectedExecutionRequest() {
        stubApprovedResponse();

        provider.execute(validTransaction());

        wireMock.verify(1, postRequestedFor(urlEqualTo(EXECUTE_PATH))
                .withHeader(HttpHeaders.CONTENT_TYPE, equalTo(MediaType.APPLICATION_JSON_VALUE))
                .withRequestBody(equalToJson("""
                        {
                          "accountId": "acc-123456",
                          "type": "CREDIT",
                          "amount": 1500.00,
                          "currency": "MXN"
                        }
                        """, true, false)));
    }

    @Test
    void mapsApprovedResponse() {
        stubApprovedResponse();

        ProviderResult result = provider.execute(validTransaction());

        assertThat(result).isEqualTo(new ProviderResult(
                "provider-transaction-123",
                ProviderStatus.APPROVED,
                new BigDecimal("8500.00"),
                Instant.parse("2026-08-09T20:00:00Z"),
                null,
                null));
    }

    @Test
    void maps4xxToRejectedResult() {
        wireMock.stubFor(post(EXECUTE_PATH)
                .willReturn(aResponse()
                        .withStatus(422)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "code": "INSUFFICIENT_FUNDS",
                                  "message": "Insufficient funds"
                                }
                                """)));

        ProviderResult result = provider.execute(validTransaction());

        assertThat(result).isEqualTo(new ProviderResult(
                null,
                ProviderStatus.REJECTED,
                null,
                null,
                "INSUFFICIENT_FUNDS",
                "Insufficient funds"));
    }

    @Test
    void maps5xxToRejectedResult() {
        wireMock.stubFor(post(EXECUTE_PATH)
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("""
                                {
                                  "code": "PROVIDER_UNAVAILABLE",
                                  "message": "Provider unavailable"
                                }
                                """)));

        ProviderResult result = provider.execute(validTransaction());

        assertThat(result).isEqualTo(new ProviderResult(
                null,
                ProviderStatus.REJECTED,
                null,
                null,
                "PROVIDER_UNAVAILABLE",
                "Provider unavailable"));
        wireMock.verify(1, postRequestedFor(urlEqualTo(EXECUTE_PATH)));
    }

    private void stubApprovedResponse() {
        wireMock.stubFor(post(EXECUTE_PATH)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(APPROVED_RESPONSE)));
    }

    private Transaction validTransaction() {
        return new Transaction(
                "acc-123456",
                TransactionType.CREDIT,
                new BigDecimal("1500.00"),
                "MXN",
                "Description must not be sent");
    }
}

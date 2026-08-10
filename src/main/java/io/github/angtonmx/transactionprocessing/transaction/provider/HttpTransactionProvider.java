package io.github.angtonmx.transactionprocessing.transaction.provider;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import io.github.angtonmx.transactionprocessing.transaction.ProviderResult;
import io.github.angtonmx.transactionprocessing.transaction.ProviderStatus;
import io.github.angtonmx.transactionprocessing.transaction.Transaction;
import io.github.angtonmx.transactionprocessing.transaction.TransactionProvider;
import io.github.angtonmx.transactionprocessing.transaction.TransactionType;

public class HttpTransactionProvider implements TransactionProvider {

    private static final String EXECUTE_PATH = "/provider/v1/execute";

    private final RestClient restClient;

    public HttpTransactionProvider(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public ProviderResult execute(Transaction transaction) {
        ProviderExecutionRequest providerRequest = new ProviderExecutionRequest(
                transaction.accountId(),
                transaction.type(),
                transaction.amount(),
                transaction.currency());

        return restClient.post()
                .uri(EXECUTE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(providerRequest)
                .exchange((request, response) -> {
                    if (response.getStatusCode().value() == HttpStatus.OK.value()) {
                        return mapApproved(response.bodyTo(ProviderApprovedResponse.class));
                    }

                    if (response.getStatusCode().is4xxClientError()
                            || response.getStatusCode().is5xxServerError()) {
                        return mapRejected(response.bodyTo(ProviderRejectedResponse.class));
                    }

                    throw new IllegalStateException(
                            "Unexpected provider HTTP status: "
                                    + response.getStatusCode().value());
                });
    }

    private ProviderResult mapApproved(ProviderApprovedResponse response) {
        return new ProviderResult(
                response.transactionId(),
                ProviderStatus.APPROVED,
                response.balance(),
                response.executedAt(),
                null,
                null);
    }

    private ProviderResult mapRejected(ProviderRejectedResponse response) {
        return new ProviderResult(
                null,
                ProviderStatus.REJECTED,
                null,
                null,
                response.code(),
                response.message());
    }

    private record ProviderExecutionRequest(
            String accountId,
            TransactionType type,
            BigDecimal amount,
            String currency) {
    }

    private record ProviderApprovedResponse(
            String transactionId,
            String status,
            BigDecimal balance,
            Instant executedAt) {
    }

    private record ProviderRejectedResponse(String code, String message) {
    }
}

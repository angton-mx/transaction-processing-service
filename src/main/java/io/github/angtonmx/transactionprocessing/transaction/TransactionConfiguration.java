package io.github.angtonmx.transactionprocessing.transaction;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import io.github.angtonmx.transactionprocessing.transaction.persistence.TransactionRepository;
import io.github.angtonmx.transactionprocessing.transaction.provider.HttpTransactionProvider;

@Configuration(proxyBeanMethods = false)
public class TransactionConfiguration {

    @Bean
    RestClient providerRestClient(
            RestClient.Builder restClientBuilder,
            @Value("${provider.base-url}") String providerBaseUrl) {
        return restClientBuilder
                .requestFactory(new SimpleClientHttpRequestFactory())
                .baseUrl(providerBaseUrl)
                .build();
    }

    @Bean
    TransactionProvider transactionProvider(RestClient providerRestClient) {
        return new HttpTransactionProvider(providerRestClient);
    }

    @Bean
    TransactionService transactionService(
            TransactionProvider transactionProvider,
            TransactionRepository transactionRepository) {
        return new TransactionService(
                transactionProvider,
                transactionRepository);
    }
}

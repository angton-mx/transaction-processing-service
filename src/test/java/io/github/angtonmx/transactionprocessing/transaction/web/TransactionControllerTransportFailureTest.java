package io.github.angtonmx.transactionprocessing.transaction.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.github.angtonmx.transactionprocessing.transaction.ProviderTransportException;
import io.github.angtonmx.transactionprocessing.transaction.Transaction;
import io.github.angtonmx.transactionprocessing.transaction.TransactionService;

@WebMvcTest(TransactionController.class)
class TransactionControllerTransportFailureTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionService transactionService;

    @Test
    void returnsServiceUnavailableForProviderTransportFailure()
            throws Exception {
        when(transactionService.execute(any(Transaction.class)))
                .thenThrow(new ProviderTransportException(
                        "Provider outcome is unknown due to a transport failure",
                        new IllegalStateException("Connection reset")));

        mockMvc.perform(post("/transactions")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "acc-transport-failure",
                                  "type": "CREDIT",
                                  "amount": 1500.00,
                                  "currency": "MXN",
                                  "description": "Unknown provider outcome"
                                }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(""));

        verify(transactionService).execute(any(Transaction.class));
    }
}

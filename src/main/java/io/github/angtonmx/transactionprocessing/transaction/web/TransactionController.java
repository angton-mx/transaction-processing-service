package io.github.angtonmx.transactionprocessing.transaction.web;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.github.angtonmx.transactionprocessing.transaction.ProviderTransportException;
import io.github.angtonmx.transactionprocessing.transaction.Transaction;
import io.github.angtonmx.transactionprocessing.transaction.TransactionService;
import io.github.angtonmx.transactionprocessing.transaction.TransactionStatus;
import io.github.angtonmx.transactionprocessing.transaction.TransactionType;
import io.github.angtonmx.transactionprocessing.transaction.persistence.TransactionEntity;

@RestController
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse create(@RequestBody TransactionRequest request) {
        Transaction transaction = request.toTransaction();
        TransactionEntity saved = transactionService.execute(transaction);
        return TransactionResponse.from(saved);
    }

    @GetMapping("/transactions")
    public List<TransactionResponse> find(
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit) {
        validatePagination(page, limit);
        return transactionService.find(accountId, status, type, page, limit)
                .stream()
                .map(TransactionResponse::from)
                .toList();
    }

    private void validatePagination(int page, int limit) {
        if (page < 0) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "page must be greater than or equal to 0");
        }
        if (limit < 1 || limit > 100) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "limit must be between 1 and 100");
        }
    }

    @ExceptionHandler(ProviderTransportException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    void handleProviderTransportFailure() {
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(BAD_REQUEST)
    Map<String, String> handleInvalidRequest(IllegalArgumentException exception) {
        return Map.of("error", exception.getMessage());
    }
}

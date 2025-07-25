package com.mhg.app.chalice.boot;

import com.mhg.app.chalice.repository.BookRepository;
import com.redis.lettucemod.api.StatefulRedisModulesConnection;
import com.redis.lettucemod.api.async.RedisModulesAsyncCommands;
import com.redis.lettucemod.search.Suggestion;
import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.RedisFuture;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@Order(7)
@Slf4j
public class CreateAuthorNameSuggestions implements CommandLineRunner {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    GenericObjectPool<StatefulRedisModulesConnection<String, String>> pool;

    @Value("${app.autoCompleteKey}")
    private String autoCompleteKey;

    @Override
    public void run(String... args) throws Exception {
        try (StatefulRedisModulesConnection<String, String> connection = pool.borrowObject()) {
            RedisModulesAsyncCommands<String, String> commands = connection.async();

            if (!redisTemplate.hasKey(autoCompleteKey)) {
                log.info("CreateAuthorNameSuggestions::Everytime at start - Auto-complete key '{}' not found. Populating suggestions...", autoCompleteKey);
                List<RedisFuture<Long>> futures = new ArrayList<>(); // To collect all async results

                bookRepository.findAll().forEach(book -> {
                    if (book.getAuthors() != null) {
                        book.getAuthors().forEach(author -> {
                            // Create Suggestion object (assuming your Suggestion.of works as expected)
                            Suggestion<String> suggestion = Suggestion.of(author, 1d); // Score of 1d for example
                            // Add the suggestion asynchronously and collect the future
                            futures.add(commands.ftSugadd(autoCompleteKey, suggestion));
                        });
                    }
                });
                // Wait for all asynchronous suggestion additions to complete
                // Use CompletableFuture.allOf() to combine all futures and wait for them.
                // Add a reasonable timeout for the entire batch operation.
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(60, TimeUnit.SECONDS); // 60 seconds timeout for all adds

                log.info("Successfully populated auto-complete suggestions into key '{}'. Total suggestions added (approx): {}", autoCompleteKey, futures.size());

            } else {
                log.info("CreateAuthorNameSuggestions::Everytime at start - Auto-complete key '{}' already exists. Skipping population.", autoCompleteKey);
            }

        } catch (ExecutionException e) {
            // This catches exceptions wrapped by CompletableFuture.allOf().get()
            Throwable cause = e.getCause(); // Get the root cause of the failure

            if (cause instanceof RedisCommandExecutionException) {
                RedisCommandExecutionException rcee = (RedisCommandExecutionException) cause;
                log.error("Redis command execution error during auto-complete population for key '{}': {}. Details: {}", autoCompleteKey, rcee.getMessage(), rcee.getClass().getSimpleName(), rcee);
                // FT.SUGADD typically won't throw "Unknown index name" unless autoCompleteKey itself is an index name
                // (which it shouldn't be for FT.SUGADD). Other common errors might be invalid arguments or Redis issues.
            } else {
                // Catches other causes of ExecutionException (e.g., non-Redis specific issues)
                log.error("Asynchronous operation failed during auto-complete population for key '{}' due to unexpected cause: {}. Details: {}", autoCompleteKey, e.getMessage(), cause != null ? cause.getClass().getSimpleName() : "N/A", e);
            }
            // Rethrow as RuntimeException to prevent application startup if this is critical
            throw new RuntimeException("Failed to populate auto-complete suggestions during startup", e);

        } catch (InterruptedException e) {
            // Catches if the current thread was interrupted while waiting for the futures
            log.error("Auto-complete population interrupted for key '{}': {}", autoCompleteKey, e.getMessage(), e);
            Thread.currentThread().interrupt(); // Restore interrupted status
            throw new RuntimeException("Auto-complete population interrupted during startup", e);

        } catch (TimeoutException e) {
            // Catches if the entire batch operation timed out
            log.error("Auto-complete population timed out after {} seconds for key '{}': {}", 60, autoCompleteKey, e.getMessage(), e);
            throw new RuntimeException("Auto-complete population timed out during startup", e);

        } catch (Exception e) {
            // This final catch block catches any other unexpected exceptions:
            // - Failures from `pool.borrowObject()`
            // - Exceptions from `bookRepository.findAll()` or `forEach` loops
            // - Any other unhandled synchronous exceptions within the try block
            log.error("Failed to obtain connection from pool or an unexpected error occurred during auto-complete population for key '{}': {}", autoCompleteKey, e.getMessage(), e);
            throw new RuntimeException("Could not complete auto-complete population during startup", e);
        }
    }
}

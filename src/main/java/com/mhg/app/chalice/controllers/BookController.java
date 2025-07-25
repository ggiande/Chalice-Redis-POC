package com.mhg.app.chalice.controllers;

import com.mhg.app.chalice.model.Book;
import com.mhg.app.chalice.model.Category;
import com.mhg.app.chalice.repository.BookRepository;
import com.mhg.app.chalice.repository.CategoryRepository;
import com.redis.lettucemod.api.StatefulRedisModulesConnection;
import com.redis.lettucemod.api.async.RedisModulesAsyncCommands;
import com.redis.lettucemod.search.SearchResults;
import com.redis.lettucemod.search.Suggestion;
import com.redis.lettucemod.search.SuggetOptions;
import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.RedisFuture;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@RestController
@RequestMapping("/api/books")
public class BookController {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Value("${app.booksSearchIndexName:books-idx}")
    private String searchIndexName;

    @Value("${app.autoCompleteKey:author-autocomplete}")
    private String autoCompleteKey;

    @Autowired
    GenericObjectPool<StatefulRedisModulesConnection<String, String>> pool;

    public static final Integer MAX_TIMEOUT = 3;

    @GetMapping("/categories")
    public Iterable<Category> getCategories() {
        long startTime = System.currentTimeMillis(); // Millisecond precision
        Iterable<Category> categories = categoryRepository.findAll();
        log.info("Finished processing in {} ms.", System.currentTimeMillis() - startTime);
        return categories;
    }

    @GetMapping("/{isbn}")
    public Book get(@PathVariable("isbn") String isbn) {
        long startTime = System.currentTimeMillis(); // Millisecond precision
        Book book = bookRepository.findById(isbn);
        log.info("Finished processing in {} ms.", System.currentTimeMillis() - startTime);
        return book;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> all(@RequestParam(defaultValue = "0") Integer page, @RequestParam(defaultValue = "10") Integer size) {
        long startTime = System.currentTimeMillis(); // Millisecond precision
        Pageable paging = PageRequest.of(page, size);
        Page<Book> pagedResult = bookRepository.findAll(paging);
        List<Book> books = pagedResult.hasContent() ? pagedResult.getContent() : Collections.emptyList();

        Map<String, Object> response = new HashMap<>();
        response.put("books", books);
        response.put("page", pagedResult.getNumber());
        response.put("pages", pagedResult.getTotalPages());
        response.put("total", pagedResult.getTotalElements());
        log.info("Finished processing in {} ms.", System.currentTimeMillis() - startTime);
        return new ResponseEntity<>(response, new HttpHeaders(), HttpStatus.OK);
    }

    @GetMapping("/search")
    public SearchResults<String, String> search(@RequestParam(name = "q") String query) throws Exception {
        long startTime = System.currentTimeMillis(); // Millisecond precision
        try (StatefulRedisModulesConnection<String, String> connection = pool.borrowObject()) {
            RedisModulesAsyncCommands<String, String> commands = connection.async();
            log.info("Executing search for index {} with query '{}'", searchIndexName, query);

            try {
                RedisFuture<SearchResults<String, String>> futureResults = commands.ftSearch(searchIndexName, query);
                SearchResults<String, String> results = futureResults.get(5, TimeUnit.SECONDS); // Specify a timeout
                log.info(">>>> Search for query '{}' on index '{}' returned results {}", query, searchIndexName, results);
                log.info("Finished processing in {} ms.", System.currentTimeMillis() - startTime);
                return results;
            } catch (RedisCommandExecutionException rcee) {
                // This correctly catches the direct execution exception from the synchronous command
                if (rcee.getMessage() != null && rcee.getMessage().equalsIgnoreCase("Unknown index name")) {
                    log.error("Search failed: Redis Search Index '{}' does not exist. Please ensure it is created and data is indexed.", searchIndexName);
                    // Decide your recovery action:
                    log.info("Finished processing in {} ms.", System.currentTimeMillis() - startTime);
                    return new SearchResults<>(); // Option 1: Return empty results gracefully
                    // throw new IndexNotFoundException("Redis Search index not found: " + searchIndexName, rcee); // Option 2: Throw custom app exception
                } else {
                    // Handle other specific command execution errors (e.g., query syntax error, Redis memory issues)
                    log.error("Redis command execution error during search on index '{}' for query '{}': {}", searchIndexName, query, rcee.getMessage(), rcee);
                    throw new RuntimeException("Redis search command failed unexpectedly", rcee);
                }
            } catch (Exception e) { // Catch any other unexpected exceptions from the synchronous command
                // This would catch things like a direct TimeoutException (if configured for sync clients),
                // or other unforeseen runtime issues specific to the command's execution.
                log.error("An unexpected error occurred during synchronous Redis search on index '{}' for query '{}': {}", searchIndexName, query, e.getMessage(), e);
                throw new RuntimeException("Redis search failed due to unexpected error", e);
            }
        } catch (Exception e) { // This outer catch handles pool.borrowObject() failures and general connection issues
            log.error("Failed to obtain connection from pool for search: {}", e.getMessage(), e);
            throw new RuntimeException("Could not get Redis connection for search", e);
        }
    }

    @GetMapping("/authors")
    public List<Suggestion<String>> authorAutoComplete(@RequestParam(name = "q") String query) {
        long startTime = System.currentTimeMillis(); // Millisecond precision
        try (StatefulRedisModulesConnection<String, String> connection = pool.borrowObject()) {
            RedisModulesAsyncCommands<String, String> commands = connection.async();
            log.info("Executing autocomplete search on key '{}' for query '{}'", autoCompleteKey, query);

            try {
                SuggetOptions options = SuggetOptions.builder().max(20L).build();

                // *** CRITICAL FIX 1: Directly call ftSugget and store its future ***
                RedisFuture<List<Suggestion<String>>> suggestionFuture = commands.ftSugget(autoCompleteKey, query, options);

                // *** CRITICAL FIX 2: Call .get() on the actual future and get the correct return type ***
                List<Suggestion<String>> suggestions = suggestionFuture.get(5, TimeUnit.SECONDS); // 5 seconds timeout for this operation

                log.info(">>>> Autocomplete for query '{}' on key '{}' returned {} results.", query, autoCompleteKey, suggestions.size());
                log.info("Finished processing in {} ms.", System.currentTimeMillis() - startTime);
                return suggestions; // Return the correct type
            } catch (ExecutionException e) {
                // This block correctly catches the ExecutionException thrown by .get()
                Throwable cause = e.getCause(); // Get the underlying cause of the ExecutionException

                if (cause instanceof RedisCommandExecutionException) {
                    RedisCommandExecutionException rcee = (RedisCommandExecutionException) cause;

                    // FT.SUGGET generally doesn't throw "Unknown index name" but "Unknown key" if the suggester key doesn't exist
                    if (rcee.getMessage() != null && rcee.getMessage().contains("Unknown key")) {
                        log.error("Autocomplete failed: Redis Suggester Key '{}' does not exist. Please ensure it is created and populated with data.", autoCompleteKey);
                        log.info("Finished processing in {} ms.", System.currentTimeMillis() - startTime);
                        return new ArrayList<>(); // Option 1: Return empty list gracefully
                    } else {
                        // Handle other specific command execution errors (e.g., general Redis issues)
                        log.error("Redis command execution error during autocomplete on key '{}' for query '{}': {}", autoCompleteKey, query, rcee.getMessage(), rcee);
                        throw new RuntimeException("Redis autocomplete command failed unexpectedly", rcee);
                    }
                } else {
                    // Handle other types of ExecutionException causes (e.g., network issues, client-side problems)
                    log.error("Asynchronous command execution failed during autocomplete on key '{}' for query '{}' due to unexpected cause: {}", autoCompleteKey, query, e.getMessage(), e);
                    throw new RuntimeException("Redis autocomplete failed due to unexpected future exception", e);
                }
            } catch (InterruptedException e) {
                // Handle cases where the thread waiting for the result is interrupted
                log.error("Autocomplete for query '{}' on key '{}' was interrupted: {}", query, autoCompleteKey, e.getMessage(), e);
                Thread.currentThread().interrupt(); // Restore interrupted status
                throw new RuntimeException("Redis autocomplete interrupted", e);
            } catch (TimeoutException e) {
                // Handle cases where the command takes longer than the specified timeout
                log.error("Autocomplete for query '{}' on key '{}' timed out after 5 seconds: {}", query, autoCompleteKey, e.getMessage(), e);
                throw new RuntimeException("Redis autocomplete timed out", e);
            }
        } catch (Exception e) { // This outer catch handles pool.borrowObject() failures and general connection issues
            log.error("Failed to obtain connection from pool for autocomplete: {}", e.getMessage(), e);
            throw new RuntimeException("Could not get Redis connection for autocomplete", e);
        }
    }
}

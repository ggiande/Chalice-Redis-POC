package com.mhg.app.chalice.boot;

import com.mhg.app.chalice.model.Book;
import com.redis.lettucemod.api.StatefulRedisModulesConnection;
import com.redis.lettucemod.api.async.RedisModulesAsyncCommands;
import com.redis.lettucemod.search.CreateOptions;
import com.redis.lettucemod.search.Field;
import io.lettuce.core.RedisCommandExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


@Slf4j
@Order(6)
@Component
public class CreateBooksSearchIndex implements CommandLineRunner {

    @Autowired
    GenericObjectPool<StatefulRedisModulesConnection<String, String>> pool;

    @Value("${app.booksSearchIndexName:books-idx}")
    private String searchIndexName;

    public static final Integer MAX_TIMEOUT = 3;

    @Override
    @SuppressWarnings({"unchecked"})
    public void run(String... args) throws Exception {
        try (StatefulRedisModulesConnection<String, String> connection = pool.borrowObject()) {
            RedisModulesAsyncCommands<String, String> commands = connection.async();
            log.info("CreateBooksSearchIndex::BookSearchIndex Everytime at start - Created a connection executing search for index {}", searchIndexName);
            try {
                // This call to .get() is correctly placed.
                List<Object> ftInfoResult = commands.ftInfo(searchIndexName).get(MAX_TIMEOUT, TimeUnit.SECONDS);
                log.info("CreateBooksSearchIndex::BookSearchIndex Everytime at start - Books Search Index '{}' already exists. Info: {}", searchIndexName, ftInfoResult);
            } catch (ExecutionException e) {
                // This is the primary catch for exceptions from .get()
                Throwable cause = e.getCause();

                if (cause instanceof RedisCommandExecutionException) {
                    RedisCommandExecutionException rcee = (RedisCommandExecutionException) cause;

                    // *** THE FIX IS HERE: Use .equalsIgnoreCase() for robustness ***
                    // or check for the *exact* string: "Unknown index name"
                    if (rcee.getMessage() != null && rcee.getMessage().equalsIgnoreCase("Unknown index name")) {
                        log.info(">>>> Books Search Index '{}' does not exist. Attempting to create it...", searchIndexName);
                        // Call your index creation logic here
                        createBookSearchIndex(commands);
                    } else {
                        // This handles other RedisCommandExecutionException types
                        log.error("Redis command execution error via async future for index '{}': {}", searchIndexName, rcee.getMessage(), rcee);
                        throw new RuntimeException("Redis command error via async future", rcee);
                    }
                } else {
                    // This handles ExecutionExceptions with other underlying causes
                    log.error("Asynchronous command execution failed due to unexpected cause for index '{}': {}", searchIndexName, e.getMessage(), e);
                    throw new RuntimeException("Redis index check failed due to unexpected future exception", e);
                }
            } catch (InterruptedException e) {
                log.error("Redis index check was interrupted during FT.INFO for index '{}': {}", searchIndexName, e.getMessage(), e);
                Thread.currentThread().interrupt();
                throw new RuntimeException("Redis index check interrupted", e);
            } catch (TimeoutException e) {
                log.error("Redis index check timed out during FT.INFO for index '{}' after 5 seconds: {}", searchIndexName, e.getMessage(), e);
                throw new RuntimeException("Redis index check timed out", e);
            }
        } catch (Exception e) {
            log.error("Failed to obtain connection from pool: {}", e.getMessage(), e);
            throw new RuntimeException("Could not get Redis connection for index check", e);
        }
    }

    @SuppressWarnings({"unchecked"})
    public void createBookSearchIndex(RedisModulesAsyncCommands<String, String> commands) throws ExecutionException, InterruptedException, TimeoutException {
        try {
            CreateOptions<String, String> options = CreateOptions.<String, String>builder()//
                    .prefix(String.format("%s:", Book.class.getSimpleName())).build();

            Field<String> title = Field.text("title").sortable(true).build();
            Field<String> subtitle = Field.text("subtitle").build();
            Field<String> description = Field.text("description").build();

            List<Field<String>> allFields = new ArrayList<>();
            allFields.add(title);
            allFields.add(subtitle);
            allFields.add(description);

            for (int i = 0; i < 7; i++) {
                allFields.add(Field.text(String.format("authors.[%d]", i)).build());
            }

            commands.ftCreate(
                    searchIndexName,
                    options,
                    allFields.toArray(Field[]::new) // Still use Field[]::new for type-safe array
            ).get(10, TimeUnit.SECONDS);
            log.info(">>>> Created Books Search Index...");

        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RedisCommandExecutionException) {
                log.error("Error creating Redis Search Index '{}': {}", searchIndexName, cause.getMessage(), cause);
                throw new RuntimeException("Failed to create Redis Search Index: " + searchIndexName, cause);
            } else {
                log.error("Unexpected error during async index creation for '{}': {}", searchIndexName, e.getMessage(), e);
                throw new RuntimeException("Redis index creation failed due to unexpected future exception", e);
            }
        } catch (InterruptedException | TimeoutException e) {
            log.error("Index creation for '{}' interrupted or timed out: {}", searchIndexName, e.getMessage(), e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Redis index creation interrupted or timed out", e);
        }
    }
}

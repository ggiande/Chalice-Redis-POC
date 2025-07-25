package com.mhg.app.chalice.boot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mhg.app.chalice.model.Book;
import com.mhg.app.chalice.model.Category;
import com.mhg.app.chalice.repository.BookRepository;
import com.mhg.app.chalice.repository.CategoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Order(3)
@Slf4j
public class CreateBooks implements CommandLineRunner {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Override
    public void run(String... args) throws Exception {
        if (bookRepository.count() != 0) {
            return;
        }
        log.info(">>>> CreateBooks | Inserting Books");
        ObjectMapper mapper = new ObjectMapper();
        TypeReference<List<Book>> typeReference = new TypeReference<List<Book>>() {
        };

        List<File> files = //
                Files.list(Paths.get(getClass().getResource("/data/books").toURI())) //
                        .filter(Files::isRegularFile) //
                        .filter(path -> path.toString().endsWith(".json")) //
                        .map(java.nio.file.Path::toFile) //
                        .collect(Collectors.toList());

        Map<String, Category> categories = new HashMap<String, Category>();
        files.forEach(file -> {
            try {
//                log.info(">>>> CreateBooks | Processing Book File: " + file.getPath());
                String categoryName = file.getName().substring(0, file.getName().lastIndexOf("_"));
//                log.info(">>>> CreateBooks | Category: " + categoryName);

                Category category;
                if (!categories.containsKey(categoryName)) {
                    category = Category.builder().name(categoryName).build();
                    categoryRepository.save(category);
                    categories.put(categoryName, category);
                } else {
                    category = categories.get(categoryName);
                }

                InputStream inputStream = new FileInputStream(file);
                List<Book> books = mapper.readValue(inputStream, typeReference);
                books.stream().forEach((book) -> {
                    book.addCategory(category);
                    bookRepository.save(book);
                });
                log.info(">>>> CreateBooks | {} Books Saved!", books.size());
            } catch (IOException e) {
                log.error(">>>> CreateBooks | Unable to import books: {}", e.getMessage());
            }
        });
        log.info(">>>> CreateBooks | Loaded Book Data and Created books...");
    }
}

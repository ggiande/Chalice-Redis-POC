package com.mhg.app.chalice.controllers;

import com.mhg.app.chalice.model.Book;
import com.mhg.app.chalice.model.Category;
import com.mhg.app.chalice.repository.BookRepository;
import com.mhg.app.chalice.repository.CategoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/books")
public class BookController {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private CategoryRepository categoryRepository;


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
    public ResponseEntity<Map<String, Object>> all(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size
    ) {
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
}

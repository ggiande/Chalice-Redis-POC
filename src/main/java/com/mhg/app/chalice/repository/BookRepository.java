package com.mhg.app.chalice.repository;

import com.mhg.app.chalice.model.Book;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookRepository extends PagingAndSortingRepository<Book, String>  {
    Iterable<Book> findAll();

    int count();

    void save(Book book);

    Book findById(String isbn);
}
package com.mhg.app.chalice.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Reference;
import org.springframework.data.redis.core.RedisHash;

import java.util.HashSet;
import java.util.Set;

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@RedisHash("Book")
public class Book {

    @Id
    @EqualsAndHashCode.Include
    private String id;

    private String title;
    private String subtitle;
    private String description;
    private String language;
    private Long pageCount;
    private String thumbnail;
    private Double price;
    private String currency;
    private String infoLink;

    private Set<String> authors;

    @Reference
    private Set<Category> categories = new HashSet<Category>();

    public void addCategory(Category category) {
        categories.add(category);
    }
}
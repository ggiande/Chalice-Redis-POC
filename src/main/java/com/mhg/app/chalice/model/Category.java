package com.mhg.app.chalice.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

@Data
@Builder
@RedisHash("Category")
public class Category {

    @Id
    private String id;

    private String name;

}
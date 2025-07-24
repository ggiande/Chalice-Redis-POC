package com.mhg.app.chalice.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CartItem {
    private String isbn;
    private Double price;
    private Long quantity;
}
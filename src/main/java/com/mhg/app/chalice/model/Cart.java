package com.mhg.app.chalice.model;

import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.Set;

@Data
@Builder
public class Cart {

    private String id;

    private String userId;

    @Singular
    private Set<CartItem> cartItems;

    public Integer count() {
        return getCartItems().size();
    }

    public Double getTotal() {
        return cartItems //
                .stream() //
                .mapToDouble(ci -> ci.getPrice() * ci.getQuantity()) //
                .sum();
    }
}
package com.mhg.app.chalice.service;

import com.mhg.app.chalice.model.Book;
import com.mhg.app.chalice.model.Cart;
import com.mhg.app.chalice.model.CartItem;
import com.mhg.app.chalice.model.User;
import com.mhg.app.chalice.repository.BookRepository;
import com.mhg.app.chalice.repository.CartRepository;
import com.mhg.app.chalice.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.json.Path2;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.IntStream;

@Slf4j
@Service
public class CartService {

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private UserRepository userRepository;

    private final JedisPooled jedisPooled;

    Path2 cartItemsPath = Path2.of(".cartItems");

    public CartService(JedisPooled jedisPooled) {
        this.jedisPooled = jedisPooled;
    }

    public Cart get(String id) {
        return cartRepository.findById(id).get();
    }

    public void addToCart(String id, CartItem item) {
        Optional<Book> book = Optional.ofNullable(bookRepository.findById(item.getIsbn()));
        if (book.isPresent()) {
            String cartKey = CartRepository.getKey(id);
            item.setPrice(book.get().getPrice());
            jedisPooled.jsonArrAppend(cartKey, cartItemsPath, item);
        }
    }

    public void removeFromCart(String id, String isbn) {
        Optional<Cart> cartFinder = cartRepository.findById(id);
        if (cartFinder.isPresent()) {
            Cart cart = cartFinder.get();
            String cartKey = CartRepository.getKey(cart.getId());
            List<CartItem> cartItems = new ArrayList<CartItem>(cart.getCartItems());
            OptionalInt cartItemIndex = IntStream.range(0, cartItems.size()) //
                    .filter(i -> cartItems.get(i).getIsbn().equals(isbn))
                    .findFirst();
            if (cartItemIndex.isPresent()) {
                jedisPooled.jsonArrPop(cartKey, cartItemsPath, cartItemIndex.getAsInt());
            } else {
                log.error("Unable to find isbn {} in cache", isbn);
            }
        }
    }

    public void checkout(String id) {
        Cart cart = cartRepository.findById(id).get();
        User user = userRepository.findById(cart.getUserId()).get();
        cart.getCartItems().forEach(cartItem -> {
            Book book = bookRepository.findById(cartItem.getIsbn());
            user.addBook(book);
        });
        userRepository.save(user);
    }
}

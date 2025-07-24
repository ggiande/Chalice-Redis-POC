package com.mhg.app.chalice.repository;

import com.google.gson.Gson;
import com.mhg.app.chalice.model.Cart;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.json.Path2;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
@Repository
public class CartRepository implements CrudRepository<Cart, String> {

    private final JedisPooled jedisPooled;
    private static final Gson GSON = new Gson(); // Initialize your JSON (de)serializer
    private final static String idPrefix = Cart.class.getName();

    @Autowired
    private RedisTemplate<String, String> template;

    public CartRepository(JedisPooled jedisPooled) {
        this.jedisPooled = jedisPooled;
    }

    private SetOperations<String, String> redisSets() {
        return template.opsForSet();
    }

    private HashOperations<String, String, String> redisHash() {
        return template.opsForHash();
    }

    // TESTED
    @Override
    public <S extends Cart> S save(S cart) {
        // set cart id
        if (cart.getId() == null) {
            cart.setId(UUID.randomUUID().toString());
        }
        String key = getKey(cart);
        String jsonString = GSON.toJson(cart); // Serialize POJO to JSON string
        jedisPooled.jsonSet(key, Path2.ROOT_PATH, jsonString);
        redisSets().add(idPrefix, key);
        log.info("cart user id {} cart id {}", cart.getUserId(), cart.getId());
        redisHash().put("carts-by-user-id-idx", cart.getUserId(), cart.getId());
        return cart;
    }

    // TESTED
    @Override
    public <S extends Cart> Iterable<S> saveAll(Iterable<S> carts) {
        return StreamSupport.stream(carts.spliterator(), false).map(cart -> save(cart)).collect(Collectors.toList());
    }

    // TESTED
    // jedisPooled.jsonGet("cart:abc-123", new Path2("$.items[0].productId")) // this is what path introduces
    @Override
    public Optional<Cart> findById(String id) {
        Object jsonString = jedisPooled.jsonGet(getKey(id));
        Cart cart = null;
        if (jsonString != null) {
            cart = GSON.fromJson(jsonString.toString(), Cart.class);
        }
        return Optional.ofNullable(cart);
    }

    // TESTED
    @Override
    public boolean existsById(String id) {
        return template.hasKey(getKey(id));
    }

    // TESTED
    @Override
    public Iterable<Cart> findAll() {
        String[] keys = redisSets().members(idPrefix).stream().toArray(String[]::new);
        List<JSONArray> jsonArrays = jedisPooled.jsonMGet(Path2.ROOT_PATH, keys);
        List<Cart> carts = jsonArrays.stream().filter(Objects::nonNull) // Filter out nulls (for non-existent keys)
                .map(jsonArray -> {
                    if (jsonArray.isEmpty()) {
                        return null; // No JSON document found for this key
                    }
                    Object firstElement = jsonArray.get(0);
                    if (firstElement instanceof JSONObject) {
                        String jsonString = firstElement.toString();
                        return GSON.fromJson(jsonString, Cart.class);
                    }
                    return null; // Return null if unexpected type
                }).toList();
        return carts;
    }

    // TESTED
    @Override
    public Iterable<Cart> findAllById(Iterable<String> ids) {
        String[] keys = StreamSupport.stream(ids.spliterator(), false)
                .map(id -> getKey(id)).toArray(String[]::new);
        List<JSONArray> jsonArrays = jedisPooled.jsonMGet(Path2.ROOT_PATH, keys);
        List<Cart> carts = jsonArrays.stream().filter(Objects::nonNull) // Filter out nulls (for non-existent keys)
                .map(jsonArray -> {
                    if (jsonArray.isEmpty()) {
                        return null; // No JSON document found for this key
                    }
                    Object firstElement = jsonArray.get(0);
                    if (firstElement instanceof JSONObject) {
                        String jsonString = firstElement.toString();
                        return GSON.fromJson(jsonString, Cart.class);
                    }
                    return null; // Return null if unexpected type
                }).toList();
        return carts;
    }

    // TESTED
    @Override
    public long count() {
        Long sizeIdPrefix = redisSets().size(idPrefix);
        return (sizeIdPrefix != null) ? sizeIdPrefix : -1;
    }

    // TESTED
    @Override
    public void deleteById(String id) {
        jedisPooled.del(getKey(id));
        Long removedCount = redisSets().remove(idPrefix, getKey(id));
        if (removedCount != null && removedCount > 0) {
            log.info("Successfully removed {} member(s) from set '{}'.", removedCount, getKey(id));
        } else {
            log.warn("No members found or removed from set '{}' for the provided carts.", getKey(id));
        }
    }

    // TESTED
    @Override
    public void delete(Cart cart) {
        deleteById(cart.getId());
    }

    // TESTED
    @Override
    public void deleteAllById(Iterable<? extends String> keys) {
        Set<String> keySet = StreamSupport.stream(keys.spliterator(), false)
                .collect(Collectors.toSet());
        Long removedCount = redisSets().remove(idPrefix, keySet.toArray());
        if (removedCount != null && removedCount > 0) {
            log.info("Successfully removed {} member(s) from set '{}'.", removedCount, keys);
        } else {
            log.warn("No members found or removed from set '{}' for the provided carts.", keys);
        }
    }

    // TESTED
    @Override
    public void deleteAll(Iterable<? extends Cart> carts) {
        List<String> keys = StreamSupport //
                .stream(carts.spliterator(), false) //
                .map(cart -> String.format("%s:%s", idPrefix, cart.getId())) //
                .toList();
        Long removedCount = redisSets().remove(idPrefix, keys.toArray());
        if (removedCount != null && removedCount > 0) {
            log.info("Successfully removed {} member(s) from set '{}'.", removedCount, keys);
        } else {
            log.warn("No members found or removed from set '{}' for the provided carts.", keys);
        }
    }

    // TESTED
    @Override
    public void deleteAll() {
        Boolean deleted = redisSets().getOperations().delete(idPrefix); // Or just redisOperations.delete(setKey);
        if (Boolean.TRUE.equals(deleted)) {
            log.info("Successfully deleted Redis Set with key: {}. All members removed.", idPrefix);
        } else {
            // deleted will be false if the key did not exist in Redis.
            log.warn("Failed to delete Redis Set with key: {}. It might not have existed or was already empty.", idPrefix);
        }
    }

    // TESTED
    public Optional<Cart> findByUserId(Long id) {
        String cartId = redisHash().get("carts-by-user-id-idx", id.toString());
        return (cartId != null) ? findById(cartId) : Optional.empty();
    }

    // TESTED
    public static String getKey(Cart cart) {
        return String.format("%s:%s", idPrefix, cart.getId());
    }

    // TESTED
    public static String getKey(String id) {
        return String.format("%s:%s", idPrefix, id);
    }
}
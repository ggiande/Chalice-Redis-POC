package com.mhg.app.chalice.boot;

import com.mhg.app.chalice.model.Book;
import com.mhg.app.chalice.model.BookRating;
import com.mhg.app.chalice.model.User;
import com.mhg.app.chalice.repository.BookRatingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.stream.IntStream;

@Component
@Order(4)
@Slf4j
public class CreateBookRatings implements CommandLineRunner {

    @Value("${app.numberOfRatings:5000}")
    private Integer numberOfRatings;

    @Value("${app.ratingStars:5}")
    private Integer ratingStars;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private BookRatingRepository bookRatingRepo;

    @Override
    public void run(String... args) throws Exception {
        if (bookRatingRepo.count() == 0) {
            log.info("CreateBookRatings::Inserting Book Ratings");
            Random random = new Random();
            IntStream.range(0, numberOfRatings).forEach(n -> {
                String bookId = redisTemplate.opsForSet().randomMember(Book.class.getName());
                String userId = redisTemplate.opsForSet().randomMember(User.class.getName());
                int stars = random.nextInt(ratingStars) + 1;

                User user = new User();
                user.setId(userId);

                Book book = new Book();
                book.setId(bookId);

                BookRating rating = BookRating.builder() //
                        .user(user) //
                        .book(book) //
                        .rating(stars).build();
                bookRatingRepo.save(rating);
            });
            log.info(">>>> BookRating created...");
        }
    }
}
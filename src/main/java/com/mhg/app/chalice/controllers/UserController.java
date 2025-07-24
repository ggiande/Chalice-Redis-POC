package com.mhg.app.chalice.controllers;

import com.mhg.app.chalice.model.User;
import com.mhg.app.chalice.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @GetMapping
    public Iterable<User> all(@RequestParam(defaultValue = "") String email) {
        long startTime = System.currentTimeMillis(); // Millisecond precision
        if (email.isEmpty()) {
            Iterable<User> users = userRepository.findAll();
            log.info("Finished processing in {} ms.", System.currentTimeMillis() - startTime);
            return users;
        }
        Optional<User> user = Optional.ofNullable(userRepository.findFirstByEmail(email));
        log.info("Finished processing in {} ms.", System.currentTimeMillis() - startTime);
        return user.isPresent() ? List.of(user.get()) : Collections.emptyList();
    }
}
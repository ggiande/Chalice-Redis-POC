package com.mhg.app.chalice.boot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mhg.app.chalice.model.Role;
import com.mhg.app.chalice.model.User;
import com.mhg.app.chalice.repository.RoleRepository;
import com.mhg.app.chalice.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;


@Slf4j
@Order(2)
@Component
public class CreateUsers implements CommandLineRunner {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        Role customerRole = roleRepository.findFirstByName("customer");
        Role adminRole = roleRepository.findFirstByName("admin");

        if (customerRole == null || adminRole == null) {
            // If the 'customer' role doesn't exist, create it and save it
            customerRole = Role.builder().name("customer").id("1234").build();
            adminRole = Role.builder().name("admin").id("12345").build();

            roleRepository.save(customerRole);
            roleRepository.save(adminRole);
            log.info(">>>> 'customer' Role created and saved: {}", customerRole);
            log.info(">>>> 'admin' Role created and saved: {}", adminRole);
        } else {
            log.info(">>>> 'admin/customer' Role found: {}", customerRole);
        }

        if (userRepository.count() == 0) {
            // load the roles
            Role admin = roleRepository.findFirstByName("admin");
            Role customer = roleRepository.findFirstByName("customer");
            log.info("Loaded admin {} and customer: {}", admin, customer);
            try {
                // create a Jackson object mapper
                ObjectMapper mapper = new ObjectMapper();
                // create a type definition to convert the array of JSON into a List of Users
                TypeReference<List<User>> typeReference = new TypeReference<List<User>>() {
                };
                // make the JSON data available as an input stream
                InputStream inputStream = getClass().getResourceAsStream("/data/users/users.json");
                // convert the JSON to objects
                List<User> users = mapper.readValue(inputStream, typeReference);

                users.stream()
                        .filter(Objects::nonNull)
                        .forEach((user) -> {
                            user.setPassword(passwordEncoder.encode(user.getPassword()));
                            user.addRole(customer);
                    userRepository.save(user);
                });
                log.info(">>>> {} Users Saved!", users.size());
            } catch (IOException e) {
                log.info(">>>> Unable to import users: {}",  e.getMessage());
            }

            User adminUser = new User();
            adminUser.setName("Adminus Admistradore");
            adminUser.setEmail("admin@example.com");
            adminUser.setPassword(passwordEncoder.encode("Reindeer Flotilla"));//
            adminUser.addRole(admin);

            userRepository.save(adminUser);
            log.info(">>>> Loaded User Data and Created users...");
        }
    }
}
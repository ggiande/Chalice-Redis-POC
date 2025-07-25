package com.mhg.app.chalice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPooled;

import java.time.Duration;

@EnableCaching
@SpringBootApplication
public class ChaliceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChaliceApplication.class, args);
    }

    @Bean
    public RedisTemplate<?, ?> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<?, ?> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        return template;
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    // You should get these values from your application.properties or application.yml
    @Value("${spring.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.redis.port:6379}")
    private int redisPort;

    @Value("${spring.redis.password:}") // Optional: if your Redis requires authentication
    private String redisPassword;

    @Value("${spring.redis.database:0}") // Optional: Redis database index
    private int redisDatabase;

    @Value("${spring.redis.timeout:2000}") // Connection timeout in milliseconds
    private int redisTimeout;

    @Bean
    public JedisPooled jedisPooled() {
        // Build the HostAndPort for your Redis instance
        HostAndPort hostAndPort = new HostAndPort(redisHost, redisPort);

        // Build the JedisClientConfig (recommended for more control)
        DefaultJedisClientConfig.Builder clientConfigBuilder = DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(redisTimeout)
                .database(redisDatabase);

        if (redisPassword != null && !redisPassword.isEmpty()) {
            clientConfigBuilder.password(redisPassword);
        }

        JedisClientConfig clientConfig = clientConfigBuilder.build();

        // Create and return the JedisPooled instance
        return new JedisPooled(hostAndPort, clientConfig);
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig() //
                .prefixCacheNameWith(this.getClass().getPackageName() + ".") //
                .entryTtl(Duration.ofMinutes(3)) //
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory) //
                .cacheDefaults(config) //
                .build();
    }
}


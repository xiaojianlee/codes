//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.lls.framework.redis.autoconfigure;

import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping;
import com.lls.framework.distributedlock.redislock.RedisDistributedLock;
import com.lls.framework.redis.client.RedisClient;
import com.lls.framework.redis.interceptor.SqlStatementInterceptor;
import com.lls.framework.redis.serializer.FastJson2JsonRedisSerializer;
import java.util.Iterator;
import java.util.List;
import org.apache.ibatis.plugin.Interceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisNode;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration.JedisPoolingClientConfigurationBuilder;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.util.StringUtils;
import redis.clients.jedis.JedisPoolConfig;

@Configuration
@EnableCaching
@EnableConfigurationProperties({RedisProperties.class})
public class RedisConfiguration extends CachingConfigurerSupport {
    private static final Logger lg = LoggerFactory.getLogger(RedisConfiguration.class);
    @Autowired
    private RedisProperties jedisConfig;
    public static final String REDIS_STANDALONE = "standalone";
    public static final String REDIS_SENTINEL = "sentinel";

    public RedisConfiguration() {
    }

    @Bean(
        name = {"connectionFactory"}
    )
    public JedisConnectionFactory jedisConnectionFactory() {
        String mode = this.jedisConfig.getMode();
        if (mode.equals("standalone")) {
            lg.info("init redis config: mode {}, host {}, port {}, db {}", new Object[]{this.jedisConfig.getMode(), this.jedisConfig.getHost(), this.jedisConfig.getPort(), this.jedisConfig.getDatabase()});
            RedisStandaloneConfiguration rf = new RedisStandaloneConfiguration();
            rf.setDatabase(this.jedisConfig.getDatabase());
            rf.setHostName(this.jedisConfig.getHost());
            rf.setPort(this.jedisConfig.getPort());
            if (!StringUtils.isEmpty(this.jedisConfig.getPassword())) {
                rf.setPassword(RedisPassword.of(this.jedisConfig.getPassword()));
            }

            JedisPoolingClientConfigurationBuilder jpb = (JedisPoolingClientConfigurationBuilder)JedisClientConfiguration.builder();
            JedisPoolConfig jedisPoolConfig = this.createJedisPoolConfig();
            jpb.poolConfig(jedisPoolConfig);
            JedisConnectionFactory jedisConnectionFactory = new JedisConnectionFactory(rf, jpb.build());
            return jedisConnectionFactory;
        } else if (!mode.equals("sentinel")) {
            return null;
        } else {
            List<String> redisNodes = this.jedisConfig.getSentinel().getNodes();
            String master = this.jedisConfig.getSentinel().getMaster();
            lg.info("init redis config: mode {}, nodes {}, master {}, db {}", new Object[]{this.jedisConfig.getMode(), redisNodes.toString(), master, this.jedisConfig.getDatabase()});
            RedisSentinelConfiguration configuration = new RedisSentinelConfiguration();
            Iterator var5 = redisNodes.iterator();

            while(var5.hasNext()) {
                String redisHost = (String)var5.next();
                String[] item = redisHost.split(":");
                String ip = item[0];
                String port = item[1];
                configuration.addSentinel(new RedisNode(ip, Integer.parseInt(port)));
            }

            configuration.setMaster(master);
            configuration.setDatabase(this.jedisConfig.getDatabase());
            if (!StringUtils.isEmpty(this.jedisConfig.getPassword())) {
                configuration.setPassword(RedisPassword.of(this.jedisConfig.getPassword()));
            }

            JedisPoolingClientConfigurationBuilder jpb = (JedisPoolingClientConfigurationBuilder)JedisClientConfiguration.builder();
            JedisPoolConfig jedisPoolConfig = this.createJedisPoolConfig();
            jpb.poolConfig(jedisPoolConfig);
            JedisConnectionFactory jedisConnectionFactory = new JedisConnectionFactory(configuration, jpb.build());
            return jedisConnectionFactory;
        }
    }

    @Bean
    public RedisSerializer<Object> fastJson2JsonRedisSerializer() {
        return new FastJson2JsonRedisSerializer(Object.class);
    }

    @Bean(
        name = {"redisTemplate"}
    )
    @ConditionalOnMissingBean
    public RedisTemplate<String, Object> initRedisTemplate(@Qualifier("connectionFactory") JedisConnectionFactory jedisConnectionFactory, RedisSerializer<Object> fastJson2JsonRedisSerializer) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate();
        redisTemplate.setConnectionFactory(jedisConnectionFactory);
        RedisSerializer<String> stringSerializer = new StringRedisSerializer();
        redisTemplate.setKeySerializer(stringSerializer);
        redisTemplate.setValueSerializer(fastJson2JsonRedisSerializer);
        redisTemplate.setHashKeySerializer(stringSerializer);
        redisTemplate.setHashValueSerializer(fastJson2JsonRedisSerializer);
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }

    @Bean
    public CacheManager init(@Qualifier("connectionFactory") JedisConnectionFactory jedisConnectionFactory) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, Visibility.ANY);
        objectMapper.enableDefaultTyping(DefaultTyping.NON_FINAL);
        Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer(Object.class);
        jackson2JsonRedisSerializer.setObjectMapper(objectMapper);
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig().serializeValuesWith(SerializationPair.fromSerializer(jackson2JsonRedisSerializer));
        RedisCacheManager cacheManager = RedisCacheManager.builder(jedisConnectionFactory).cacheDefaults(config).build();
        cacheManager.initializeCaches();
        return cacheManager;
    }

    @Bean
    public CacheErrorHandler errorHandler() {
        lg.info("initializing -> [{}]", "Redis CacheErrorHandler");
        CacheErrorHandler cacheErrorHandler = new CacheErrorHandler() {
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                RedisConfiguration.lg.error("Redis occur handleCacheGetError：key -> [{}]", key, e);
            }

            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                RedisConfiguration.lg.error("Redis occur handleCachePutError：key -> [{}]；value -> [{}]", new Object[]{key, value, e});
            }

            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                RedisConfiguration.lg.error("Redis occur handleCacheEvictError：key -> [{}]", key, e);
            }

            public void handleCacheClearError(RuntimeException e, Cache cache) {
                RedisConfiguration.lg.error("Redis occur handleCacheClearError：", e);
            }
        };
        return cacheErrorHandler;
    }

    @Bean(
        name = {"redisClient"}
    )
    @ConditionalOnProperty(
        value = {"spring.redis.enabled.client"},
        havingValue = "true",
        matchIfMissing = true
    )
    public RedisClient initRedisClient(@Qualifier("redisTemplate") RedisTemplate<String, Object> redisTemplate) {
        return new RedisClient(redisTemplate);
    }

    @Bean(
        name = {"jedisDistributedLock"}
    )
    @ConditionalOnProperty(
        value = {"spring.redis.enabled.lock"},
        havingValue = "true"
    )
    public RedisDistributedLock jedisDistributedLock() {
        RedisDistributedLock lock = new RedisDistributedLock();
        return lock;
    }

    private JedisPoolConfig createJedisPoolConfig() {
        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
        jedisPoolConfig.setMaxIdle(this.jedisConfig.getJedis().getPool().getMaxIdle());
        jedisPoolConfig.setMinIdle(this.jedisConfig.getJedis().getPool().getMinIdle());
        jedisPoolConfig.setMaxTotal(this.jedisConfig.getJedis().getPool().getMaxActive());
        jedisPoolConfig.setMaxWaitMillis(this.jedisConfig.getJedis().getPool().getMaxWait().toMillis());
        return jedisPoolConfig;
    }

    @ConditionalOnClass(
        name = {"org.apache.ibatis.plugin.Interceptor"}
    )
    static class MybatisPlugin {
        MybatisPlugin() {
        }

        @Bean
        @ConditionalOnProperty(
            value = {"spring.redis.enabled.cache"},
            havingValue = "true"
        )
        public Interceptor getInterceptor(@Qualifier("redisClient") RedisClient redisClient, RedisProperties redisProperties) {
            return new SqlStatementInterceptor(redisClient, redisProperties);
        }
    }
}

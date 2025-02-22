package redis.clients.jedis;

import redis.clients.jedis.args.*;
import redis.clients.jedis.commands.BinaryJedisClusterCommands;
import redis.clients.jedis.commands.JedisClusterBinaryScriptingCommands;
import redis.clients.jedis.commands.MultiKeyBinaryJedisClusterCommands;
import redis.clients.jedis.commands.ProtocolCommand;
import redis.clients.jedis.params.*;
import redis.clients.jedis.util.JedisClusterHashTagUtil;
import redis.clients.jedis.util.KeyMergeUtil;
import redis.clients.jedis.util.SafeEncoder;

import java.io.Closeable;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

public class BinaryJedisCluster implements BinaryJedisClusterCommands,
    MultiKeyBinaryJedisClusterCommands, JedisClusterBinaryScriptingCommands, Closeable {

  public static final int HASHSLOTS = 16384;

  /**
   * Default timeout in milliseconds.
   */
  public static final int DEFAULT_TIMEOUT = 2000;
  public static final int DEFAULT_MAX_ATTEMPTS = 5;

  protected int maxAttempts;

  /**
   * After this amount of time we will do no more retries and report the operation as failed.
   *
   * Defaults to {@link #DEFAULT_TIMEOUT} if unset, or {@code soTimeout} if available.
   */
  protected Duration maxTotalRetriesDuration;

  protected JedisClusterConnectionHandler connectionHandler;

  public BinaryJedisCluster(Set<HostAndPort> nodes) {
    this(nodes, DEFAULT_TIMEOUT);
  }

  public BinaryJedisCluster(Set<HostAndPort> nodes, int timeout) {
    this(nodes, timeout, DEFAULT_MAX_ATTEMPTS, new GenericObjectPoolConfig<Jedis>());
  }

  public BinaryJedisCluster(Set<HostAndPort> jedisClusterNode, int timeout, int maxAttempts,
      final GenericObjectPoolConfig<Jedis> poolConfig) {
    this(jedisClusterNode, timeout, timeout, maxAttempts, poolConfig);
  }

  public BinaryJedisCluster(Set<HostAndPort> jedisClusterNode, int connectionTimeout,
      int soTimeout, int maxAttempts, final GenericObjectPoolConfig<Jedis> poolConfig) {
    this(jedisClusterNode, connectionTimeout, soTimeout, maxAttempts, null, poolConfig);
  }

  public BinaryJedisCluster(Set<HostAndPort> jedisClusterNode, int connectionTimeout,
      int soTimeout, int maxAttempts, String password, GenericObjectPoolConfig<Jedis> poolConfig) {
    this(jedisClusterNode, connectionTimeout, soTimeout, maxAttempts, password, null, poolConfig);
  }

  public BinaryJedisCluster(Set<HostAndPort> jedisClusterNode, int connectionTimeout,
      int soTimeout, int maxAttempts, String password, String clientName,
      GenericObjectPoolConfig<Jedis> poolConfig) {
    this(jedisClusterNode, connectionTimeout, soTimeout, maxAttempts, null, password, clientName,
        poolConfig);
  }

  public BinaryJedisCluster(Set<HostAndPort> jedisClusterNode, int connectionTimeout,
      int soTimeout, int maxAttempts, String user, String password, String clientName,
      GenericObjectPoolConfig<Jedis> poolConfig) {
    this.connectionHandler = new JedisSlotBasedConnectionHandler(jedisClusterNode, poolConfig,
        connectionTimeout, soTimeout, user, password, clientName);
    this.maxAttempts = maxAttempts;
    this.maxTotalRetriesDuration = Duration.ofMillis(soTimeout);
  }

  public BinaryJedisCluster(Set<HostAndPort> jedisClusterNode, int connectionTimeout,
      int soTimeout, int infiniteSoTimeout, int maxAttempts, String user, String password,
      String clientName, GenericObjectPoolConfig<Jedis> poolConfig) {
    this.connectionHandler = new JedisSlotBasedConnectionHandler(jedisClusterNode, poolConfig,
        connectionTimeout, soTimeout, infiniteSoTimeout, user, password, clientName);
    this.maxAttempts = maxAttempts;
    this.maxTotalRetriesDuration = Duration.ofMillis(soTimeout);
  }

  public BinaryJedisCluster(Set<HostAndPort> jedisClusterNode, int connectionTimeout,
      int soTimeout, int maxAttempts, String password, String clientName,
      GenericObjectPoolConfig<Jedis> poolConfig, boolean ssl) {
    this(jedisClusterNode, connectionTimeout, soTimeout, maxAttempts, null, password, clientName,
        poolConfig, ssl);
  }

  public BinaryJedisCluster(Set<HostAndPort> jedisClusterNode, int connectionTimeout,
      int soTimeout, int maxAttempts, String user, String password, String clientName,
      GenericObjectPoolConfig<Jedis> poolConfig, boolean ssl) {
    this(jedisClusterNode,
        DefaultJedisClientConfig.builder().connectionTimeoutMillis(connectionTimeout)
            .socketTimeoutMillis(soTimeout).user(user).password(password).clientName(clientName)
            .ssl(ssl).build(),
        maxAttempts, poolConfig);
  }

  public BinaryJedisCluster(Set<HostAndPort> jedisClusterNode, JedisClientConfig clientConfig,
      int maxAttempts, GenericObjectPoolConfig<Jedis> poolConfig) {
    this(jedisClusterNode, clientConfig, maxAttempts,
        Duration.ofMillis((long) DEFAULT_TIMEOUT * maxAttempts), poolConfig);
  }

  public BinaryJedisCluster(Set<HostAndPort> jedisClusterNode, JedisClientConfig clientConfig,
      int maxAttempts, Duration maxTotalRetriesDuration, GenericObjectPoolConfig<Jedis> poolConfig) {
    this.connectionHandler = new JedisSlotBasedConnectionHandler(jedisClusterNode, poolConfig,
        clientConfig);
    this.maxAttempts = maxAttempts;
    this.maxTotalRetriesDuration = maxTotalRetriesDuration;
  }

  @Override
  public void close() {
    if (connectionHandler != null) {
      connectionHandler.close();
    }
  }

  public Map<String, JedisPool> getClusterNodes() {
    return connectionHandler.getNodes();
  }

  public Jedis getConnectionFromSlot(int slot) {
    return this.connectionHandler.getConnectionFromSlot(slot);
  }

  @Override
  public boolean copy(byte[] srcKey, byte[] dstKey, boolean replace) {
    return new JedisClusterCommand<Boolean>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Boolean execute(Jedis connection) {
        return connection.copy(srcKey, dstKey, replace);
      }
    }.runBinary(2, srcKey, dstKey);
  }

  @Override
  public String set(final byte[] key, final byte[] value) {
    return new JedisClusterCommand<String>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public String execute(Jedis connection) {
        return connection.set(key, value);
      }
    }.runBinary(key);
  }

  @Override
  public String set(final byte[] key, final byte[] value, final SetParams params) {
    return new JedisClusterCommand<String>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public String execute(Jedis connection) {
        return connection.set(key, value, params);
      }
    }.runBinary(key);
  }

  @Override
  public byte[] get(final byte[] key) {
    return new JedisClusterCommand<byte[]>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public byte[] execute(Jedis connection) {
        return connection.get(key);
      }
    }.runBinary(key);
  }

  @Override
  public byte[] getDel(final byte[] key) {
    return new JedisClusterCommand<byte[]>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public byte[] execute(Jedis connection) {
        return connection.getDel(key);
      }
    }.runBinary(key);
  }

  @Override
  public byte[] getEx(byte[] key, GetExParams params) {
    return new JedisClusterCommand<byte[]>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public byte[] execute(Jedis connection) {
        return connection.getEx(key, params);
      }
    }.runBinary(key);
  }

  @Override
  public long exists(final byte[]... keys) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.exists(keys);
      }
    }.runBinary(keys.length, keys);
  }

  @Override
  public boolean exists(final byte[] key) {
    return new JedisClusterCommand<Boolean>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Boolean execute(Jedis connection) {
        return connection.exists(key);
      }
    }.runBinary(key);
  }

  @Override
  public long persist(final byte[] key) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.persist(key);
      }
    }.runBinary(key);
  }

  @Override
  public String type(final byte[] key) {
    return new JedisClusterCommand<String>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public String execute(Jedis connection) {
        return connection.type(key);
      }
    }.runBinary(key);
  }

  @Override
  public byte[] dump(final byte[] key) {
    return new JedisClusterCommand<byte[]>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public byte[] execute(Jedis connection) {
        return connection.dump(key);
      }
    }.runBinary(key);
  }

  @Override
  public String restore(final byte[] key, final long ttl, final byte[] serializedValue) {
    return new JedisClusterCommand<String>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public String execute(Jedis connection) {
        return connection.restore(key, ttl, serializedValue);
      }
    }.runBinary(key);
  }

  @Override
  public String restore(final byte[] key, final long ttl, final byte[] serializedValue,
      final RestoreParams params) {
    return new JedisClusterCommand<String>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public String execute(Jedis connection) {
        return connection.restore(key, ttl, serializedValue, params);
      }
    }.runBinary(key);
  }

  @Override
  public long expire(final byte[] key, final long seconds) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.expire(key, seconds);
      }
    }.runBinary(key);
  }

  @Override
  public long pexpire(final byte[] key, final long milliseconds) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.pexpire(key, milliseconds);
      }
    }.runBinary(key);
  }

  @Override
  public long expireAt(final byte[] key, final long unixTime) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.expireAt(key, unixTime);
      }
    }.runBinary(key);
  }

  @Override
  public long pexpireAt(final byte[] key, final long millisecondsTimestamp) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.pexpireAt(key, millisecondsTimestamp);
      }
    }.runBinary(key);
  }

  @Override
  public long ttl(final byte[] key) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.ttl(key);
      }
    }.runBinary(key);
  }

  @Override
  public long pttl(final byte[] key) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.pttl(key);
      }
    }.runBinary(key);
  }

  @Override
  public long touch(final byte[] key) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.touch(key);
      }
    }.runBinary(key);
  }

  @Override
  public long touch(final byte[]... keys) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.touch(keys);
      }
    }.runBinary(keys.length, keys);
  }

  @Override
  public boolean setbit(final byte[] key, final long offset, final boolean value) {
    return new JedisClusterCommand<Boolean>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Boolean execute(Jedis connection) {
        return connection.setbit(key, offset, value);
      }
    }.runBinary(key);
  }

  @Override
  public Boolean setbit(final byte[] key, final long offset, final byte[] value) {
    return new JedisClusterCommand<Boolean>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Boolean execute(Jedis connection) {
        return connection.setbit(key, offset, value);
      }
    }.runBinary(key);
  }

  @Override
  public boolean getbit(final byte[] key, final long offset) {
    return new JedisClusterCommand<Boolean>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Boolean execute(Jedis connection) {
        return connection.getbit(key, offset);
      }
    }.runBinary(key);
  }

  @Override
  public long setrange(final byte[] key, final long offset, final byte[] value) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.setrange(key, offset, value);
      }
    }.runBinary(key);
  }

  @Override
  public byte[] getrange(final byte[] key, final long startOffset, final long endOffset) {
    return new JedisClusterCommand<byte[]>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public byte[] execute(Jedis connection) {
        return connection.getrange(key, startOffset, endOffset);
      }
    }.runBinary(key);
  }

  @Override
  public byte[] getSet(final byte[] key, final byte[] value) {
    return new JedisClusterCommand<byte[]>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public byte[] execute(Jedis connection) {
        return connection.getSet(key, value);
      }
    }.runBinary(key);
  }

  @Override
  public long setnx(final byte[] key, final byte[] value) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.setnx(key, value);
      }
    }.runBinary(key);
  }

  @Override
  public String psetex(final byte[] key, final long milliseconds, final byte[] value) {
    return new JedisClusterCommand<String>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public String execute(Jedis connection) {
        return connection.psetex(key, milliseconds, value);
      }
    }.runBinary(key);
  }

  @Override
  public String setex(final byte[] key, final long seconds, final byte[] value) {
    return new JedisClusterCommand<String>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public String execute(Jedis connection) {
        return connection.setex(key, seconds, value);
      }
    }.runBinary(key);
  }

  @Override
  public long decrBy(final byte[] key, final long decrement) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.decrBy(key, decrement);
      }
    }.runBinary(key);
  }

  @Override
  public long decr(final byte[] key) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.decr(key);
      }
    }.runBinary(key);
  }

  @Override
  public long incrBy(final byte[] key, final long increment) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.incrBy(key, increment);
      }
    }.runBinary(key);
  }

  @Override
  public double incrByFloat(final byte[] key, final double increment) {
    return new JedisClusterCommand<Double>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Double execute(Jedis connection) {
        return connection.incrByFloat(key, increment);
      }
    }.runBinary(key);
  }

  @Override
  public long incr(final byte[] key) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.incr(key);
      }
    }.runBinary(key);
  }

  @Override
  public long append(final byte[] key, final byte[] value) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.append(key, value);
      }
    }.runBinary(key);
  }

  @Override
  public byte[] substr(final byte[] key, final int start, final int end) {
    return new JedisClusterCommand<byte[]>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public byte[] execute(Jedis connection) {
        return connection.substr(key, start, end);
      }
    }.runBinary(key);
  }

  @Override
  public long hset(final byte[] key, final byte[] field, final byte[] value) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.hset(key, field, value);
      }
    }.runBinary(key);
  }

  @Override
  public long hset(final byte[] key, final Map<byte[], byte[]> hash) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.hset(key, hash);
      }
    }.runBinary(key);
  }

  @Override
  public byte[] hget(final byte[] key, final byte[] field) {
    return new JedisClusterCommand<byte[]>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public byte[] execute(Jedis connection) {
        return connection.hget(key, field);
      }
    }.runBinary(key);
  }

  @Override
  public long hsetnx(final byte[] key, final byte[] field, final byte[] value) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.hsetnx(key, field, value);
      }
    }.runBinary(key);
  }

  @Override
  public String hmset(final byte[] key, final Map<byte[], byte[]> hash) {
    return new JedisClusterCommand<String>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public String execute(Jedis connection) {
        return connection.hmset(key, hash);
      }
    }.runBinary(key);
  }

  @Override
  public List<byte[]> hmget(final byte[] key, final byte[]... fields) {
    return new JedisClusterCommand<List<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<byte[]> execute(Jedis connection) {
        return connection.hmget(key, fields);
      }
    }.runBinary(key);
  }

  @Override
  public long hincrBy(final byte[] key, final byte[] field, final long value) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.hincrBy(key, field, value);
      }
    }.runBinary(key);
  }

  @Override
  public double hincrByFloat(final byte[] key, final byte[] field, final double value) {
    return new JedisClusterCommand<Double>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Double execute(Jedis connection) {
        return connection.hincrByFloat(key, field, value);
      }
    }.runBinary(key);
  }

  @Override
  public boolean hexists(final byte[] key, final byte[] field) {
    return new JedisClusterCommand<Boolean>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Boolean execute(Jedis connection) {
        return connection.hexists(key, field);
      }
    }.runBinary(key);
  }

  @Override
  public long hdel(final byte[] key, final byte[]... field) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.hdel(key, field);
      }
    }.runBinary(key);
  }

  @Override
  public long hlen(final byte[] key) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.hlen(key);
      }
    }.runBinary(key);
  }

  @Override
  public Set<byte[]> hkeys(final byte[] key) {
    return new JedisClusterCommand<Set<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Set<byte[]> execute(Jedis connection) {
        return connection.hkeys(key);
      }
    }.runBinary(key);
  }

  @Override
  public List<byte[]> hvals(final byte[] key) {
    return new JedisClusterCommand<List<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<byte[]> execute(Jedis connection) {
        return connection.hvals(key);
      }
    }.runBinary(key);
  }

  @Override
  public Map<byte[], byte[]> hgetAll(final byte[] key) {
    return new JedisClusterCommand<Map<byte[], byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Map<byte[], byte[]> execute(Jedis connection) {
        return connection.hgetAll(key);
      }
    }.runBinary(key);
  }

  @Override
  public byte[] hrandfield(final byte[] key) {
    return new JedisClusterCommand<byte[]>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public byte[] execute(Jedis connection) {
        return connection.hrandfield(key);
      }
    }.runBinary(key);
  }

  @Override
  public List<byte[]> hrandfield(final byte[] key, final long count) {
    return new JedisClusterCommand<List<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<byte[]> execute(Jedis connection) {
        return connection.hrandfield(key, count);
      }
    }.runBinary(key);
  }

  @Override
  public Map<byte[], byte[]> hrandfieldWithValues(final byte[] key, final long count) {
    return new JedisClusterCommand<Map<byte[], byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Map<byte[], byte[]> execute(Jedis connection) {
        return connection.hrandfieldWithValues(key, count);
      }
    }.runBinary(key);
  }

  @Override
  public long rpush(final byte[] key, final byte[]... args) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.rpush(key, args);
      }
    }.runBinary(key);
  }

  @Override
  public long lpush(final byte[] key, final byte[]... args) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.lpush(key, args);
      }
    }.runBinary(key);
  }

  @Override
  public long llen(final byte[] key) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.llen(key);
      }
    }.runBinary(key);
  }

  @Override
  public List<byte[]> lrange(final byte[] key, final long start, final long stop) {
    return new JedisClusterCommand<List<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<byte[]> execute(Jedis connection) {
        return connection.lrange(key, start, stop);
      }
    }.runBinary(key);
  }

  @Override
  public String ltrim(final byte[] key, final long start, final long stop) {
    return new JedisClusterCommand<String>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public String execute(Jedis connection) {
        return connection.ltrim(key, start, stop);
      }
    }.runBinary(key);
  }

  @Override
  public byte[] lindex(final byte[] key, final long index) {
    return new JedisClusterCommand<byte[]>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public byte[] execute(Jedis connection) {
        return connection.lindex(key, index);
      }
    }.runBinary(key);
  }

  @Override
  public String lset(final byte[] key, final long index, final byte[] value) {
    return new JedisClusterCommand<String>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public String execute(Jedis connection) {
        return connection.lset(key, index, value);
      }
    }.runBinary(key);
  }

  @Override
  public long lrem(final byte[] key, final long count, final byte[] value) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.lrem(key, count, value);
      }
    }.runBinary(key);
  }

  @Override
  public byte[] lpop(final byte[] key) {
    return new JedisClusterCommand<byte[]>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public byte[] execute(Jedis connection) {
        return connection.lpop(key);
      }
    }.runBinary(key);
  }

  @Override
  public List<byte[]> lpop(final byte[] key, final int count) {
    return new JedisClusterCommand<List<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<byte[]> execute(Jedis connection) {
        return connection.lpop(key, count);
      }
    }.runBinary(key);
  }

  @Override
  public Long lpos(final byte[] key, final byte[] element) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.lpos(key, element);
      }
    }.runBinary(key);
  }

  @Override
  public Long lpos(final byte[] key, final byte[] element, final LPosParams params) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.lpos(key, element, params);
      }
    }.runBinary(key);
  }

  @Override
  public List<Long> lpos(final byte[] key, final byte[] element, final LPosParams params,
      final long count) {
    return new JedisClusterCommand<List<Long>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<Long> execute(Jedis connection) {
        return connection.lpos(key, element, params, count);
      }
    }.runBinary(key);
  }

  @Override
  public byte[] rpop(final byte[] key) {
    return new JedisClusterCommand<byte[]>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public byte[] execute(Jedis connection) {
        return connection.rpop(key);
      }
    }.runBinary(key);
  }

  @Override
  public List<byte[]> rpop(final byte[] key, final int count) {
    return new JedisClusterCommand<List<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<byte[]> execute(Jedis connection) {
        return connection.rpop(key, count);
      }
    }.runBinary(key);
  }

  @Override
  public long sadd(final byte[] key, final byte[]... member) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.sadd(key, member);
      }
    }.runBinary(key);
  }

  @Override
  public Set<byte[]> smembers(final byte[] key) {
    return new JedisClusterCommand<Set<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Set<byte[]> execute(Jedis connection) {
        return connection.smembers(key);
      }
    }.runBinary(key);
  }

  @Override
  public long srem(final byte[] key, final byte[]... member) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.srem(key, member);
      }
    }.runBinary(key);
  }

  @Override
  public byte[] spop(final byte[] key) {
    return new JedisClusterCommand<byte[]>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public byte[] execute(Jedis connection) {
        return connection.spop(key);
      }
    }.runBinary(key);
  }

  @Override
  public Set<byte[]> spop(final byte[] key, final long count) {
    return new JedisClusterCommand<Set<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Set<byte[]> execute(Jedis connection) {
        return connection.spop(key, count);
      }
    }.runBinary(key);
  }

  @Override
  public long scard(final byte[] key) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.scard(key);
      }
    }.runBinary(key);
  }

  @Override
  public boolean sismember(final byte[] key, final byte[] member) {
    return new JedisClusterCommand<Boolean>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Boolean execute(Jedis connection) {
        return connection.sismember(key, member);
      }
    }.runBinary(key);
  }

  @Override
  public List<Boolean> smismember(final byte[] key, final byte[]... members) {
    return new JedisClusterCommand<List<Boolean>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<Boolean> execute(Jedis connection) {
        return connection.smismember(key, members);
      }
    }.runBinary(key);
  }

  @Override
  public byte[] srandmember(final byte[] key) {
    return new JedisClusterCommand<byte[]>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public byte[] execute(Jedis connection) {
        return connection.srandmember(key);
      }
    }.runBinary(key);
  }

  @Override
  public long strlen(final byte[] key) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.strlen(key);
      }
    }.runBinary(key);
  }

  @Override
  public long zadd(final byte[] key, final double score, final byte[] member) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.zadd(key, score, member);
      }
    }.runBinary(key);
  }

  @Override
  public long zadd(final byte[] key, final double score, final byte[] member,
      final ZAddParams params) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.zadd(key, score, member, params);
      }
    }.runBinary(key);
  }

  @Override
  public long zadd(final byte[] key, final Map<byte[], Double> scoreMembers) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.zadd(key, scoreMembers);
      }
    }.runBinary(key);
  }

  @Override
  public long zadd(final byte[] key, final Map<byte[], Double> scoreMembers, final ZAddParams params) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.zadd(key, scoreMembers, params);
      }
    }.runBinary(key);
  }

  @Override
  public Double zaddIncr(byte[] key, double score, byte[] member, ZAddParams params) {
    return new JedisClusterCommand<Double>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Double execute(Jedis connection) {
        return connection.zaddIncr(key, score, member, params);
      }
    }.runBinary(key);
  }

  @Override
  public Set<byte[]> zdiff(final byte[]... keys) {
    return new JedisClusterCommand<Set<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Set<byte[]> execute(Jedis connection) {
        return connection.zdiff(keys);
      }
    }.runBinary(keys.length, keys);
  }

  @Override
  public Set<Tuple> zdiffWithScores(final byte[]... keys) {
    return new JedisClusterCommand<Set<Tuple>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Set<Tuple> execute(Jedis connection) {
        return connection.zdiffWithScores(keys);
      }
    }.runBinary(keys.length, keys);
  }

  @Override
  public long zdiffStore(final byte[] dstkey, final byte[]... keys) {
    byte[][] wholeKeys = KeyMergeUtil.merge(dstkey, keys);
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.zdiffStore(dstkey, keys);
      }
    }.runBinary(wholeKeys.length, wholeKeys);
  }

  @Override
  public Set<byte[]> zrange(final byte[] key, final long start, final long stop) {
    return new JedisClusterCommand<Set<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Set<byte[]> execute(Jedis connection) {
        return connection.zrange(key, start, stop);
      }
    }.runBinary(key);
  }

  @Override
  public long zrem(final byte[] key, final byte[]... members) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.zrem(key, members);
      }
    }.runBinary(key);
  }

  @Override
  public double zincrby(final byte[] key, final double increment, final byte[] member) {
    return new JedisClusterCommand<Double>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Double execute(Jedis connection) {
        return connection.zincrby(key, increment, member);
      }
    }.runBinary(key);
  }

  @Override
  public Double zincrby(final byte[] key, final double increment, final byte[] member,
      final ZIncrByParams params) {
    return new JedisClusterCommand<Double>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Double execute(Jedis connection) {
        return connection.zincrby(key, increment, member, params);
      }
    }.runBinary(key);
  }

  @Override
  public Long zrank(final byte[] key, final byte[] member) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.zrank(key, member);
      }
    }.runBinary(key);
  }

  @Override
  public Long zrevrank(final byte[] key, final byte[] member) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.zrevrank(key, member);
      }
    }.runBinary(key);
  }

  @Override
  public Set<byte[]> zrevrange(final byte[] key, final long start, final long stop) {
    return new JedisClusterCommand<Set<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Set<byte[]> execute(Jedis connection) {
        return connection.zrevrange(key, start, stop);
      }
    }.runBinary(key);
  }

  @Override
  public Set<Tuple> zrangeWithScores(final byte[] key, final long start, final long stop) {
    return new JedisClusterCommand<Set<Tuple>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Set<Tuple> execute(Jedis connection) {
        return connection.zrangeWithScores(key, start, stop);
      }
    }.runBinary(key);
  }

  @Override
  public Set<Tuple> zrevrangeWithScores(final byte[] key, final long start, final long stop) {
    return new JedisClusterCommand<Set<Tuple>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Set<Tuple> execute(Jedis connection) {
        return connection.zrevrangeWithScores(key, start, stop);
      }
    }.runBinary(key);
  }

  @Override
  public byte[] zrandmember(final byte[] key) {
    return new JedisClusterCommand<byte[]>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public byte[] execute(Jedis connection) {
        return connection.zrandmember(key);
      }
    }.runBinary(key);
  }

  @Override
  public Set<byte[]> zrandmember(final byte[] key, final long count) {
    return new JedisClusterCommand<Set<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Set<byte[]> execute(Jedis connection) {
        return connection.zrandmember(key, count);
      }
    }.runBinary(key);
  }

  @Override
  public Set<Tuple> zrandmemberWithScores(final byte[] key, final long count) {
    return new JedisClusterCommand<Set<Tuple>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Set<Tuple> execute(Jedis connection) {
        return connection.zrandmemberWithScores(key, count);
      }
    }.runBinary(key);
  }

  @Override
  public long zcard(final byte[] key) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.zcard(key);
      }
    }.runBinary(key);
  }

  @Override
  public Double zscore(final byte[] key, final byte[] member) {
    return new JedisClusterCommand<Double>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Double execute(Jedis connection) {
        return connection.zscore(key, member);
      }
    }.runBinary(key);
  }

  @Override
  public List<Double> zmscore(final byte[] key, final byte[]... members) {
    return new JedisClusterCommand<List<Double>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<Double> execute(Jedis connection) {
        return connection.zmscore(key, members);
      }
    }.runBinary(key);
  }

  @Override
  public Tuple zpopmax(final byte[] key) {
    return new JedisClusterCommand<Tuple>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Tuple execute(Jedis connection) {
        return connection.zpopmax(key);
      }
    }.runBinary(key);
  }

  @Override
  public Set<Tuple> zpopmax(final byte[] key, final int count) {
    return new JedisClusterCommand<Set<Tuple>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Set<Tuple> execute(Jedis connection) {
        return connection.zpopmax(key, count);
      }
    }.runBinary(key);
  }

  @Override
  public Tuple zpopmin(final byte[] key) {
    return new JedisClusterCommand<Tuple>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Tuple execute(Jedis connection) {
        return connection.zpopmin(key);
      }
    }.runBinary(key);
  }

  @Override
  public Set<Tuple> zpopmin(final byte[] key, final int count) {
    return new JedisClusterCommand<Set<Tuple>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Set<Tuple> execute(Jedis connection) {
        return connection.zpopmin(key, count);
      }
    }.runBinary(key);
  }

  @Override
  public List<byte[]> sort(final byte[] key) {
    return new JedisClusterCommand<List<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<byte[]> execute(Jedis connection) {
        return connection.sort(key);
      }
    }.runBinary(key);
  }

  @Override
  public List<byte[]> sort(final byte[] key, final SortingParams sortingParameters) {
    return new JedisClusterCommand<List<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<byte[]> execute(Jedis connection) {
        return connection.sort(key, sortingParameters);
      }
    }.runBinary(key);
  }

  @Override
  public long zcount(final byte[] key, final double min, final double max) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.zcount(key, min, max);
      }
    }.runBinary(key);
  }

  @Override
  public long zcount(final byte[] key, final byte[] min, final byte[] max) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.zcount(key, min, max);
      }
    }.runBinary(key);
  }

  @Override
  public Set<byte[]> zrangeByScore(final byte[] key, final double min, final double max) {
    return new JedisClusterCommand<Set<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Set<byte[]> execute(Jedis connection) {
        return connection.zrangeByScore(key, min, max);
      }
    }.runBinary(key);
  }

  @Override
  public Set<byte[]> zrangeByScore(final byte[] key, final byte[] min, final byte[] max) {
    return new JedisClusterCommand<Set<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Set<byte[]> execute(Jedis connection) {
        return connection.zrangeByScore(key, min, max);
      }
    }.runBinary(key);
  }

  @Override
  public Set<byte[]> zrevrangeByScore(final byte[] key, final double max, final double min) {
    return new JedisClusterCommand<Set<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Set<byte[]> execute(Jedis connection) {
        return connection.zrevrangeByScore(key, max, min);
      }
    }.runBinary(key);
  }

  @Override
  public Set<byte[]> zrangeByScore(final byte[] key, final double min, final double max,
      final int offset, final int count) {
    return new JedisClusterCommand<Set<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Set<byte[]> execute(Jedis connection) {
        return connection.zrangeByScore(key, min, max, offset, count);
      }
    }.runBinary(key);
  }

  @Override
  public Set<byte[]> zrevrangeByScore(final byte[] key, final byte[] max, final byte[] min) {
    return new JedisClusterCommand<Set<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Set<byte[]> execute(Jedis connection) {
        return connection.zrevrangeByScore(key, max, min);
      }
    }.runBinary(key);
  }

  @Override
  public Set<byte[]> zrangeByScore(final byte[] key, final byte[] min, final byte[] max,
      final int offset, final int count) {
    return new JedisClusterCommand<Set<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Set<byte[]> execute(Jedis connection) {
        return connection.zrangeByScore(key, min, max, offset, count);
      }
    }.runBinary(key);
  }

  @Override
  public Set<byte[]> zrevrangeByScore(final byte[] key, final double max, final double min,
      final int offset, final int count) {
    return new JedisClusterCommand<Set<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Set<byte[]> execute(Jedis connection) {
        return connection.zrevrangeByScore(key, max, min, offset, count);
      }
    }.runBinary(key);
  }

  @Override
  public Set<Tuple> zrangeByScoreWithScores(final byte[] key, final double min, final double max) {
    return new JedisClusterCommand<Set<Tuple>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Set<Tuple> execute(Jedis connection) {
        return connection.zrangeByScoreWithScores(key, min, max);
      }
    }.runBinary(key);
  }

  @Override
  public Set<Tuple> zrevrangeByScoreWithScores(final byte[] key, final double max, final double min) {
    return new JedisClusterCommand<Set<Tuple>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Set<Tuple> execute(Jedis connection) {
        return connection.zrevrangeByScoreWithScores(key, max, min);
      }
    }.runBinary(key);
  }

  @Override
  public Set<Tuple> zrangeByScoreWithScores(final byte[] key, final double min, final double max,
      final int offset, final int count) {
    return new JedisClusterCommand<Set<Tuple>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Set<Tuple> execute(Jedis connection) {
        return connection.zrangeByScoreWithScores(key, min, max, offset, count);
      }
    }.runBinary(key);
  }

  @Override
  public Set<byte[]> zrevrangeByScore(final byte[] key, final byte[] max, final byte[] min,
      final int offset, final int count) {
    return new JedisClusterCommand<Set<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Set<byte[]> execute(Jedis connection) {
        return connection.zrevrangeByScore(key, max, min, offset, count);
      }
    }.runBinary(key);
  }

  @Override
  public Set<Tuple> zrangeByScoreWithScores(final byte[] key, final byte[] min, final byte[] max) {
    return new JedisClusterCommand<Set<Tuple>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Set<Tuple> execute(Jedis connection) {
        return connection.zrangeByScoreWithScores(key, min, max);
      }
    }.runBinary(key);
  }

  @Override
  public Set<Tuple> zrevrangeByScoreWithScores(final byte[] key, final byte[] max, final byte[] min) {
    return new JedisClusterCommand<Set<Tuple>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Set<Tuple> execute(Jedis connection) {
        return connection.zrevrangeByScoreWithScores(key, max, min);
      }
    }.runBinary(key);
  }

  @Override
  public Set<Tuple> zrangeByScoreWithScores(final byte[] key, final byte[] min, final byte[] max,
      final int offset, final int count) {
    return new JedisClusterCommand<Set<Tuple>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Set<Tuple> execute(Jedis connection) {
        return connection.zrangeByScoreWithScores(key, min, max, offset, count);
      }
    }.runBinary(key);
  }

  @Override
  public Set<Tuple> zrevrangeByScoreWithScores(final byte[] key, final double max,
      final double min, final int offset, final int count) {
    return new JedisClusterCommand<Set<Tuple>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Set<Tuple> execute(Jedis connection) {
        return connection.zrevrangeByScoreWithScores(key, max, min, offset, count);
      }
    }.runBinary(key);
  }

  @Override
  public Set<Tuple> zrevrangeByScoreWithScores(final byte[] key, final byte[] max,
      final byte[] min, final int offset, final int count) {
    return new JedisClusterCommand<Set<Tuple>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Set<Tuple> execute(Jedis connection) {
        return connection.zrevrangeByScoreWithScores(key, max, min, offset, count);
      }
    }.runBinary(key);
  }

  @Override
  public long zremrangeByRank(final byte[] key, final long start, final long stop) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.zremrangeByRank(key, start, stop);
      }
    }.runBinary(key);
  }

  @Override
  public long zremrangeByScore(final byte[] key, final double min, final double max) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.zremrangeByScore(key, min, max);
      }
    }.runBinary(key);
  }

  @Override
  public long zremrangeByScore(final byte[] key, final byte[] min, final byte[] max) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.zremrangeByScore(key, min, max);
      }
    }.runBinary(key);
  }

  @Override
  public long linsert(final byte[] key, final ListPosition where, final byte[] pivot,
      final byte[] value) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.linsert(key, where, pivot, value);
      }
    }.runBinary(key);
  }

  @Override
  public long lpushx(final byte[] key, final byte[]... arg) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.lpushx(key, arg);
      }
    }.runBinary(key);
  }

  @Override
  public long rpushx(final byte[] key, final byte[]... arg) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.rpushx(key, arg);
      }
    }.runBinary(key);
  }

  @Override
  public long del(final byte[] key) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.del(key);
      }
    }.runBinary(key);
  }

  @Override
  public long unlink(final byte[] key) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.unlink(key);
      }
    }.runBinary(key);
  }

  @Override
  public long unlink(final byte[]... keys) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.unlink(keys);
      }
    }.runBinary(keys.length, keys);
  }

  @Override
  public byte[] echo(final byte[] arg) {
    // note that it'll be run from arbitary node
    return new JedisClusterCommand<byte[]>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public byte[] execute(Jedis connection) {
        return connection.echo(arg);
      }
    }.runBinary(arg);
  }

  @Override
  public long bitcount(final byte[] key) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.bitcount(key);
      }
    }.runBinary(key);
  }

  @Override
  public long bitcount(final byte[] key, final long start, final long end) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.bitcount(key, start, end);
      }
    }.runBinary(key);
  }

  @Override
  public long pfadd(final byte[] key, final byte[]... elements) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.pfadd(key, elements);
      }
    }.runBinary(key);
  }

  @Override
  public long pfcount(final byte[] key) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.pfcount(key);
      }
    }.runBinary(key);
  }

  @Override
  public List<byte[]> srandmember(final byte[] key, final int count) {
    return new JedisClusterCommand<List<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<byte[]> execute(Jedis connection) {
        return connection.srandmember(key, count);
      }
    }.runBinary(key);
  }

  @Override
  public long zlexcount(final byte[] key, final byte[] min, final byte[] max) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.zlexcount(key, min, max);
      }
    }.runBinary(key);
  }

  @Override
  public Set<byte[]> zrangeByLex(final byte[] key, final byte[] min, final byte[] max) {
    return new JedisClusterCommand<Set<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Set<byte[]> execute(Jedis connection) {
        return connection.zrangeByLex(key, min, max);
      }
    }.runBinary(key);
  }

  @Override
  public Set<byte[]> zrangeByLex(final byte[] key, final byte[] min, final byte[] max,
      final int offset, final int count) {
    return new JedisClusterCommand<Set<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Set<byte[]> execute(Jedis connection) {
        return connection.zrangeByLex(key, min, max, offset, count);
      }
    }.runBinary(key);
  }

  @Override
  public Set<byte[]> zrevrangeByLex(final byte[] key, final byte[] max, final byte[] min) {
    return new JedisClusterCommand<Set<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Set<byte[]> execute(Jedis connection) {
        return connection.zrevrangeByLex(key, max, min);
      }
    }.runBinary(key);
  }

  @Override
  public Set<byte[]> zrevrangeByLex(final byte[] key, final byte[] max, final byte[] min,
      final int offset, final int count) {
    return new JedisClusterCommand<Set<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Set<byte[]> execute(Jedis connection) {
        return connection.zrevrangeByLex(key, max, min, offset, count);
      }
    }.runBinary(key);
  }

  @Override
  public long zremrangeByLex(final byte[] key, final byte[] min, final byte[] max) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.zremrangeByLex(key, min, max);
      }
    }.runBinary(key);
  }

  @Override
  public Object eval(final byte[] script, final byte[] keyCount, final byte[]... params) {
    return new JedisClusterCommand<Object>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Object execute(Jedis connection) {
        return connection.eval(script, keyCount, params);
      }
    }.runBinary(Integer.parseInt(SafeEncoder.encode(keyCount)), params);
  }

  @Override
  public Object eval(final byte[] script, final int keyCount, final byte[]... params) {
    return new JedisClusterCommand<Object>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Object execute(Jedis connection) {
        return connection.eval(script, keyCount, params);
      }
    }.runBinary(keyCount, params);
  }

  @Override
  public Object eval(final byte[] script, final List<byte[]> keys, final List<byte[]> args) {
    return new JedisClusterCommand<Object>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Object execute(Jedis connection) {
        return connection.eval(script, keys, args);
      }
    }.runBinary(keys.size(), keys.toArray(new byte[keys.size()][]));
  }

  @Override
  public Object eval(final byte[] script, final byte[] sampleKey) {
    return new JedisClusterCommand<Object>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Object execute(Jedis connection) {
        return connection.eval(script);
      }
    }.runBinary(sampleKey);
  }

  @Override
  public Object evalsha(final byte[] sha1, final byte[] sampleKey) {
    return new JedisClusterCommand<Object>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Object execute(Jedis connection) {
        return connection.evalsha(sha1);
      }
    }.runBinary(sampleKey);
  }

  @Override
  public Object evalsha(final byte[] sha1, final List<byte[]> keys, final List<byte[]> args) {
    return new JedisClusterCommand<Object>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Object execute(Jedis connection) {
        return connection.evalsha(sha1, keys, args);
      }
    }.runBinary(keys.size(), keys.toArray(new byte[keys.size()][]));
  }

  @Override
  public Object evalsha(final byte[] sha1, final int keyCount, final byte[]... params) {
    return new JedisClusterCommand<Object>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Object execute(Jedis connection) {
        return connection.evalsha(sha1, keyCount, params);
      }
    }.runBinary(keyCount, params);
  }

  @Override
  public List<Long> scriptExists(final byte[] sampleKey, final byte[]... sha1) {
    return new JedisClusterCommand<List<Long>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<Long> execute(Jedis connection) {
        return connection.scriptExists(sha1);
      }
    }.runBinary(sampleKey);
  }

  @Override
  public byte[] scriptLoad(final byte[] script, final byte[] sampleKey) {
    return new JedisClusterCommand<byte[]>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public byte[] execute(Jedis connection) {
        return connection.scriptLoad(script);
      }
    }.runBinary(sampleKey);
  }

  @Override
  public String scriptFlush(final byte[] sampleKey) {
    return new JedisClusterCommand<String>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public String execute(Jedis connection) {
        return connection.scriptFlush();
      }
    }.runBinary(sampleKey);
  }

  @Override
  public String scriptFlush(final byte[] sampleKey, final FlushMode flushMode) {
    return new JedisClusterCommand<String>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public String execute(Jedis connection) {
        return connection.scriptFlush(flushMode);
      }
    }.runBinary(sampleKey);
  }

  @Override
  public String scriptKill(final byte[] sampleKey) {
    return new JedisClusterCommand<String>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public String execute(Jedis connection) {
        return connection.scriptKill();
      }
    }.runBinary(sampleKey);
  }

  @Override
  public long del(final byte[]... keys) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.del(keys);
      }
    }.runBinary(keys.length, keys);
  }

  @Override
  public byte[] lmove(final byte[] srcKey, final byte[] dstKey, final ListDirection from,
      final ListDirection to) {
    return new JedisClusterCommand<byte[]>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public byte[] execute(Jedis connection) {
        return connection.lmove(srcKey, dstKey, from, to);
      }
    }.runBinary(2, srcKey, dstKey);
  }

  @Override
  public byte[] blmove(final byte[] srcKey, final byte[] dstKey, final ListDirection from,
      final ListDirection to, final double timeout) {
    return new JedisClusterCommand<byte[]>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public byte[] execute(Jedis connection) {
        return connection.blmove(srcKey, dstKey, from, to, timeout);
      }
    }.runBinary(2, srcKey, dstKey);
  }

  @Override
  public List<byte[]> blpop(final int timeout, final byte[]... keys) {
    return new JedisClusterCommand<List<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<byte[]> execute(Jedis connection) {
        return connection.blpop(timeout, keys);
      }
    }.runBinary(keys.length, keys);
  }

  @Override
  public List<byte[]> blpop(final double timeout, final byte[]... keys) {
    return new JedisClusterCommand<List<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<byte[]> execute(Jedis connection) {
        return connection.blpop(timeout, keys);
      }
    }.runBinary(keys.length, keys);
  }

  @Override
  public List<byte[]> brpop(final int timeout, final byte[]... keys) {
    return new JedisClusterCommand<List<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<byte[]> execute(Jedis connection) {
        return connection.brpop(timeout, keys);
      }
    }.runBinary(keys.length, keys);
  }

  @Override
  public List<byte[]> brpop(final double timeout, final byte[]... keys) {
    return new JedisClusterCommand<List<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<byte[]> execute(Jedis connection) {
        return connection.brpop(timeout, keys);
      }
    }.runBinary(keys.length, keys);
  }

  @Override
  public List<byte[]> bzpopmax(double timeout, byte[]... keys) {
    return new JedisClusterCommand<List<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<byte[]> execute(Jedis connection) {
        return connection.bzpopmax(timeout, keys);
      }
    }.runBinary(keys.length, keys);
  }

  @Override
  public List<byte[]> bzpopmin(double timeout, byte[]... keys) {
    return new JedisClusterCommand<List<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<byte[]> execute(Jedis connection) {
        return connection.bzpopmin(timeout, keys);
      }
    }.runBinary(keys.length, keys);
  }

  @Override
  public List<byte[]> mget(final byte[]... keys) {
    return new JedisClusterCommand<List<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<byte[]> execute(Jedis connection) {
        return connection.mget(keys);
      }
    }.runBinary(keys.length, keys);
  }

  @Override
  public String mset(final byte[]... keysvalues) {
    byte[][] keys = new byte[keysvalues.length / 2][];

    for (int keyIdx = 0; keyIdx < keys.length; keyIdx++) {
      keys[keyIdx] = keysvalues[keyIdx * 2];
    }

    return new JedisClusterCommand<String>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public String execute(Jedis connection) {
        return connection.mset(keysvalues);
      }
    }.runBinary(keys.length, keys);
  }

  @Override
  public long msetnx(final byte[]... keysvalues) {
    byte[][] keys = new byte[keysvalues.length / 2][];

    for (int keyIdx = 0; keyIdx < keys.length; keyIdx++) {
      keys[keyIdx] = keysvalues[keyIdx * 2];
    }

    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.msetnx(keysvalues);
      }
    }.runBinary(keys.length, keys);
  }

  @Override
  public String rename(final byte[] oldkey, final byte[] newkey) {
    return new JedisClusterCommand<String>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public String execute(Jedis connection) {
        return connection.rename(oldkey, newkey);
      }
    }.runBinary(2, oldkey, newkey);
  }

  @Override
  public long renamenx(final byte[] oldkey, final byte[] newkey) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.renamenx(oldkey, newkey);
      }
    }.runBinary(2, oldkey, newkey);
  }

  @Override
  public byte[] rpoplpush(final byte[] srckey, final byte[] dstkey) {
    return new JedisClusterCommand<byte[]>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public byte[] execute(Jedis connection) {
        return connection.rpoplpush(srckey, dstkey);
      }
    }.runBinary(2, srckey, dstkey);
  }

  @Override
  public Set<byte[]> sdiff(final byte[]... keys) {
    return new JedisClusterCommand<Set<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Set<byte[]> execute(Jedis connection) {
        return connection.sdiff(keys);
      }
    }.runBinary(keys.length, keys);
  }

  @Override
  public long sdiffstore(final byte[] dstkey, final byte[]... keys) {
    byte[][] wholeKeys = KeyMergeUtil.merge(dstkey, keys);

    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.sdiffstore(dstkey, keys);
      }
    }.runBinary(wholeKeys.length, wholeKeys);
  }

  @Override
  public Set<byte[]> sinter(final byte[]... keys) {
    return new JedisClusterCommand<Set<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Set<byte[]> execute(Jedis connection) {
        return connection.sinter(keys);
      }
    }.runBinary(keys.length, keys);
  }

  @Override
  public long sinterstore(final byte[] dstkey, final byte[]... keys) {
    byte[][] wholeKeys = KeyMergeUtil.merge(dstkey, keys);

    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.sinterstore(dstkey, keys);
      }
    }.runBinary(wholeKeys.length, wholeKeys);
  }

  @Override
  public long smove(final byte[] srckey, final byte[] dstkey, final byte[] member) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.smove(srckey, dstkey, member);
      }
    }.runBinary(2, srckey, dstkey);
  }

  @Override
  public long sort(final byte[] key, final SortingParams sortingParameters, final byte[] dstkey) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.sort(key, sortingParameters, dstkey);
      }
    }.runBinary(2, key, dstkey);
  }

  @Override
  public long sort(final byte[] key, final byte[] dstkey) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.sort(key, dstkey);
      }
    }.runBinary(2, key, dstkey);
  }

  @Override
  public Set<byte[]> sunion(final byte[]... keys) {
    return new JedisClusterCommand<Set<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Set<byte[]> execute(Jedis connection) {
        return connection.sunion(keys);
      }
    }.runBinary(keys.length, keys);
  }

  @Override
  public long sunionstore(final byte[] dstkey, final byte[]... keys) {
    byte[][] wholeKeys = KeyMergeUtil.merge(dstkey, keys);

    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.sunionstore(dstkey, keys);
      }
    }.runBinary(wholeKeys.length, wholeKeys);
  }

  @Override
  public Set<byte[]> zinter(final ZParams params, final byte[]... keys) {
    return new JedisClusterCommand<Set<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Set<byte[]> execute(Jedis connection) {
        return connection.zinter(params, keys);
      }
    }.runBinary(keys.length, keys);
  }

  @Override
  public Set<Tuple> zinterWithScores(final ZParams params, final byte[]... keys) {
    return new JedisClusterCommand<Set<Tuple>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Set<Tuple> execute(Jedis connection) {
        return connection.zinterWithScores(params, keys);
      }
    }.runBinary(keys.length, keys);
  }

  @Override
  public long zinterstore(final byte[] dstkey, final byte[]... sets) {
    byte[][] wholeKeys = KeyMergeUtil.merge(dstkey, sets);

    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.zinterstore(dstkey, sets);
      }
    }.runBinary(wholeKeys.length, wholeKeys);
  }

  @Override
  public long zinterstore(final byte[] dstkey, final ZParams params, final byte[]... sets) {
    byte[][] wholeKeys = KeyMergeUtil.merge(dstkey, sets);

    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.zinterstore(dstkey, params, sets);
      }
    }.runBinary(wholeKeys.length, wholeKeys);
  }

  @Override
  public Set<byte[]> zunion(final ZParams params, final byte[]... keys) {
    return new JedisClusterCommand<Set<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Set<byte[]> execute(Jedis connection) {
        return connection.zunion(params, keys);
      }
    }.runBinary(keys.length, keys);
  }

  @Override
  public Set<Tuple> zunionWithScores(final ZParams params, final byte[]... keys) {
    return new JedisClusterCommand<Set<Tuple>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Set<Tuple> execute(Jedis connection) {
        return connection.zunionWithScores(params, keys);
      }
    }.runBinary(keys.length, keys);
  }

  @Override
  public long zunionstore(final byte[] dstkey, final byte[]... sets) {
    byte[][] wholeKeys = KeyMergeUtil.merge(dstkey, sets);

    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.zunionstore(dstkey, sets);
      }
    }.runBinary(wholeKeys.length, wholeKeys);
  }

  @Override
  public long zunionstore(final byte[] dstkey, final ZParams params, final byte[]... sets) {
    byte[][] wholeKeys = KeyMergeUtil.merge(dstkey, sets);

    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.zunionstore(dstkey, params, sets);
      }
    }.runBinary(wholeKeys.length, wholeKeys);
  }

  @Override
  public byte[] brpoplpush(final byte[] source, final byte[] destination, final int timeout) {
    return new JedisClusterCommand<byte[]>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public byte[] execute(Jedis connection) {
        return connection.brpoplpush(source, destination, timeout);
      }
    }.runBinary(2, source, destination);
  }

  @Override
  public Long publish(final byte[] channel, final byte[] message) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.publish(channel, message);
      }
    }.runWithAnyNode();
  }

  @Override
  public void subscribe(final BinaryJedisPubSub jedisPubSub, final byte[]... channels) {
    new JedisClusterCommand<Integer>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Integer execute(Jedis connection) {
        connection.subscribe(jedisPubSub, channels);
        return 0;
      }
    }.runWithAnyNode();
  }

  @Override
  public void psubscribe(final BinaryJedisPubSub jedisPubSub, final byte[]... patterns) {
    new JedisClusterCommand<Integer>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Integer execute(Jedis connection) {
        connection.psubscribe(jedisPubSub, patterns);
        return 0;
      }
    }.runWithAnyNode();
  }

  @Override
  public long bitop(final BitOP op, final byte[] destKey, final byte[]... srcKeys) {
    byte[][] wholeKeys = KeyMergeUtil.merge(destKey, srcKeys);

    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.bitop(op, destKey, srcKeys);
      }
    }.runBinary(wholeKeys.length, wholeKeys);
  }

  @Override
  public String pfmerge(final byte[] destkey, final byte[]... sourcekeys) {
    byte[][] wholeKeys = KeyMergeUtil.merge(destkey, sourcekeys);

    return new JedisClusterCommand<String>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public String execute(Jedis connection) {
        return connection.pfmerge(destkey, sourcekeys);
      }
    }.runBinary(wholeKeys.length, wholeKeys);
  }

  @Override
  public long pfcount(final byte[]... keys) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.pfcount(keys);
      }
    }.runBinary(keys.length, keys);
  }

  @Override
  public long geoadd(final byte[] key, final double longitude, final double latitude,
      final byte[] member) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.geoadd(key, longitude, latitude, member);
      }
    }.runBinary(key);
  }

  @Override
  public long geoadd(final byte[] key, final Map<byte[], GeoCoordinate> memberCoordinateMap) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.geoadd(key, memberCoordinateMap);
      }
    }.runBinary(key);
  }

  @Override
  public long geoadd(byte[] key, GeoAddParams params, Map<byte[], GeoCoordinate> memberCoordinateMap) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.geoadd(key, params, memberCoordinateMap);
      }
    }.runBinary(key);
  }

  @Override
  public Double geodist(final byte[] key, final byte[] member1, final byte[] member2) {
    return new JedisClusterCommand<Double>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Double execute(Jedis connection) {
        return connection.geodist(key, member1, member2);
      }
    }.runBinary(key);
  }

  @Override
  public Double geodist(final byte[] key, final byte[] member1, final byte[] member2,
      final GeoUnit unit) {
    return new JedisClusterCommand<Double>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Double execute(Jedis connection) {
        return connection.geodist(key, member1, member2, unit);
      }
    }.runBinary(key);
  }

  @Override
  public List<byte[]> geohash(final byte[] key, final byte[]... members) {
    return new JedisClusterCommand<List<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<byte[]> execute(Jedis connection) {
        return connection.geohash(key, members);
      }
    }.runBinary(key);
  }

  @Override
  public List<GeoCoordinate> geopos(final byte[] key, final byte[]... members) {
    return new JedisClusterCommand<List<GeoCoordinate>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<GeoCoordinate> execute(Jedis connection) {
        return connection.geopos(key, members);
      }
    }.runBinary(key);
  }

  @Override
  public List<GeoRadiusResponse> georadius(final byte[] key, final double longitude,
      final double latitude, final double radius, final GeoUnit unit) {
    return new JedisClusterCommand<List<GeoRadiusResponse>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<GeoRadiusResponse> execute(Jedis connection) {
        return connection.georadius(key, longitude, latitude, radius, unit);
      }
    }.runBinary(key);
  }

  @Override
  public List<GeoRadiusResponse> georadiusReadonly(final byte[] key, final double longitude,
      final double latitude, final double radius, final GeoUnit unit) {
    return new JedisClusterCommand<List<GeoRadiusResponse>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<GeoRadiusResponse> execute(Jedis connection) {
        return connection.georadiusReadonly(key, longitude, latitude, radius, unit);
      }
    }.runBinary(key);
  }

  @Override
  public List<GeoRadiusResponse> georadius(final byte[] key, final double longitude,
      final double latitude, final double radius, final GeoUnit unit, final GeoRadiusParam param) {
    return new JedisClusterCommand<List<GeoRadiusResponse>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<GeoRadiusResponse> execute(Jedis connection) {
        return connection.georadius(key, longitude, latitude, radius, unit, param);
      }
    }.runBinary(key);
  }

  @Override
  public long georadiusStore(final byte[] key, final double longitude, final double latitude,
      final double radius, final GeoUnit unit, final GeoRadiusParam param,
      final GeoRadiusStoreParam storeParam) {
    byte[][] keys = storeParam.getByteKeys(key);
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.georadiusStore(key, longitude, latitude, radius, unit, param, storeParam);
      }
    }.runBinary(keys.length, keys);
  }

  @Override
  public List<GeoRadiusResponse> georadiusReadonly(final byte[] key, final double longitude,
      final double latitude, final double radius, final GeoUnit unit, final GeoRadiusParam param) {
    return new JedisClusterCommand<List<GeoRadiusResponse>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<GeoRadiusResponse> execute(Jedis connection) {
        return connection.georadiusReadonly(key, longitude, latitude, radius, unit, param);
      }
    }.runBinary(key);
  }

  @Override
  public List<GeoRadiusResponse> georadiusByMember(final byte[] key, final byte[] member,
      final double radius, final GeoUnit unit) {
    return new JedisClusterCommand<List<GeoRadiusResponse>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<GeoRadiusResponse> execute(Jedis connection) {
        return connection.georadiusByMember(key, member, radius, unit);
      }
    }.runBinary(key);
  }

  @Override
  public List<GeoRadiusResponse> georadiusByMemberReadonly(final byte[] key, final byte[] member,
      final double radius, final GeoUnit unit) {
    return new JedisClusterCommand<List<GeoRadiusResponse>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<GeoRadiusResponse> execute(Jedis connection) {
        return connection.georadiusByMemberReadonly(key, member, radius, unit);
      }
    }.runBinary(key);
  }

  @Override
  public List<GeoRadiusResponse> georadiusByMember(final byte[] key, final byte[] member,
      final double radius, final GeoUnit unit, final GeoRadiusParam param) {
    return new JedisClusterCommand<List<GeoRadiusResponse>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<GeoRadiusResponse> execute(Jedis connection) {
        return connection.georadiusByMember(key, member, radius, unit, param);
      }
    }.runBinary(key);
  }

  @Override
  public long georadiusByMemberStore(final byte[] key, final byte[] member, final double radius,
      final GeoUnit unit, final GeoRadiusParam param, final GeoRadiusStoreParam storeParam) {
    byte[][] keys = storeParam.getByteKeys(key);
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.georadiusByMemberStore(key, member, radius, unit, param, storeParam);
      }
    }.runBinary(keys.length, keys);
  }

  @Override
  public List<GeoRadiusResponse> georadiusByMemberReadonly(final byte[] key, final byte[] member,
      final double radius, final GeoUnit unit, final GeoRadiusParam param) {
    return new JedisClusterCommand<List<GeoRadiusResponse>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<GeoRadiusResponse> execute(Jedis connection) {
        return connection.georadiusByMemberReadonly(key, member, radius, unit, param);
      }
    }.runBinary(key);
  }

  @Override
  public String unwatch() {
    throw new UnsupportedOperationException("UNWATCH is not supported in cluster mode.");
  }

  @Override
  public Set<byte[]> keys(final byte[] pattern) {
    if (pattern == null || pattern.length == 0) {
      throw new IllegalArgumentException(this.getClass().getSimpleName()
          + " only supports KEYS commands with non-empty patterns");
    }
    if (!JedisClusterHashTagUtil.isClusterCompliantMatchPattern(pattern)) {
      throw new IllegalArgumentException(
          this.getClass().getSimpleName()
          + " only supports KEYS commands with patterns containing hash-tags "
          + "( curly-brackets enclosed strings )");
    }
    return new JedisClusterCommand<Set<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Set<byte[]> execute(Jedis connection) {
        return connection.keys(pattern);
      }
    }.runBinary(pattern);
  }

  @Override
  public ScanResult<byte[]> scan(final byte[] cursor, final ScanParams params) {

    byte[] matchPattern = null;

    if (params == null || (matchPattern = params.binaryMatch()) == null || matchPattern.length == 0) {
      throw new IllegalArgumentException(BinaryJedisCluster.class.getSimpleName()
          + " only supports SCAN commands with non-empty MATCH patterns");
    }

    if (!JedisClusterHashTagUtil.isClusterCompliantMatchPattern(matchPattern)) {
      throw new IllegalArgumentException(
          BinaryJedisCluster.class.getSimpleName()
          + " only supports SCAN commands with MATCH patterns containing hash-tags"
          + " ( curly-brackets enclosed strings )");
    }

    return new JedisClusterCommand<ScanResult<byte[]>>(connectionHandler, maxAttempts,
        maxTotalRetriesDuration) {
      @Override
      public ScanResult<byte[]> execute(Jedis connection) {
        return connection.scan(cursor, params);
      }
    }.runBinary(matchPattern);
  }

  @Override
  public ScanResult<Map.Entry<byte[], byte[]>> hscan(final byte[] key, final byte[] cursor) {
    return new JedisClusterCommand<ScanResult<Map.Entry<byte[], byte[]>>>(connectionHandler,
        maxAttempts, maxTotalRetriesDuration) {
      @Override
      public ScanResult<Map.Entry<byte[], byte[]>> execute(Jedis connection) {
        return connection.hscan(key, cursor);
      }
    }.runBinary(key);
  }

  @Override
  public ScanResult<Map.Entry<byte[], byte[]>> hscan(final byte[] key, final byte[] cursor,
      final ScanParams params) {
    return new JedisClusterCommand<ScanResult<Map.Entry<byte[], byte[]>>>(connectionHandler,
        maxAttempts, maxTotalRetriesDuration) {
      @Override
      public ScanResult<Map.Entry<byte[], byte[]>> execute(Jedis connection) {
        return connection.hscan(key, cursor, params);
      }
    }.runBinary(key);
  }

  @Override
  public ScanResult<byte[]> sscan(final byte[] key, final byte[] cursor) {
    return new JedisClusterCommand<ScanResult<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public ScanResult<byte[]> execute(Jedis connection) {
        return connection.sscan(key, cursor);
      }
    }.runBinary(key);
  }

  @Override
  public ScanResult<byte[]> sscan(final byte[] key, final byte[] cursor, final ScanParams params) {
    return new JedisClusterCommand<ScanResult<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public ScanResult<byte[]> execute(Jedis connection) {
        return connection.sscan(key, cursor, params);
      }
    }.runBinary(key);
  }

  @Override
  public ScanResult<Tuple> zscan(final byte[] key, final byte[] cursor) {
    return new JedisClusterCommand<ScanResult<Tuple>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public ScanResult<Tuple> execute(Jedis connection) {
        return connection.zscan(key, cursor);
      }
    }.runBinary(key);
  }

  @Override
  public ScanResult<Tuple> zscan(final byte[] key, final byte[] cursor, final ScanParams params) {
    return new JedisClusterCommand<ScanResult<Tuple>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public ScanResult<Tuple> execute(Jedis connection) {
        return connection.zscan(key, cursor, params);
      }
    }.runBinary(key);
  }

  @Override
  public List<Long> bitfield(final byte[] key, final byte[]... arguments) {
    return new JedisClusterCommand<List<Long>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<Long> execute(Jedis connection) {
        return connection.bitfield(key, arguments);
      }
    }.runBinary(key);
  }

  @Override
  public List<Long> bitfieldReadonly(final byte[] key, final byte[]... arguments) {
    return new JedisClusterCommand<List<Long>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<Long> execute(Jedis connection) {
        return connection.bitfieldReadonly(key, arguments);
      }
    }.runBinary(key);
  }

  @Override
  public long hstrlen(final byte[] key, final byte[] field) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.hstrlen(key, field);
      }
    }.runBinary(key);
  }

  @Override
  public Long memoryUsage(final byte[] key) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.memoryUsage(key);
      }
    }.runBinary(key);
  }

  @Override
  public Long memoryUsage(final byte[] key, final int samples) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.memoryUsage(key, samples);
      }
    }.runBinary(key);
  }

  @Override
  public byte[] xadd(final byte[] key, final byte[] id, final Map<byte[], byte[]> hash,
      final long maxLen, final boolean approximateLength) {
    return new JedisClusterCommand<byte[]>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public byte[] execute(Jedis connection) {
        return connection.xadd(key, id, hash, maxLen, approximateLength);
      }
    }.runBinary(key);
  }

  @Override
  public byte[] xadd(final byte[] key, final Map<byte[], byte[]> hash, final XAddParams params) {
    return new JedisClusterCommand<byte[]>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public byte[] execute(Jedis connection) {
        return connection.xadd(key, hash, params);
      }
    }.runBinary(key);
  }

  @Override
  public long xlen(final byte[] key) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.xlen(key);
      }
    }.runBinary(key);
  }

  @Override
  public List<byte[]> xrange(final byte[] key, final byte[] start, final byte[] end) {
    return new JedisClusterCommand<List<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<byte[]> execute(Jedis connection) {
        return connection.xrange(key, start, end);
      }
    }.runBinary(key);
  }

  @Override
  public List<byte[]> xrange(final byte[] key, final byte[] start, final byte[] end,
      final long count) {
    return new JedisClusterCommand<List<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<byte[]> execute(Jedis connection) {
        return connection.xrange(key, start, end, count);
      }
    }.runBinary(key);
  }

  @Override
  public List<byte[]> xrange(final byte[] key, final byte[] start, final byte[] end, final int count) {
    return new JedisClusterCommand<List<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<byte[]> execute(Jedis connection) {
        return connection.xrange(key, start, end, count);
      }
    }.runBinary(key);
  }

  @Override
  public List<byte[]> xrevrange(final byte[] key, final byte[] end, final byte[] start) {
    return new JedisClusterCommand<List<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<byte[]> execute(Jedis connection) {
        return connection.xrevrange(key, end, start);
      }
    }.runBinary(key);
  }

  @Override
  public List<byte[]> xrevrange(final byte[] key, final byte[] end, final byte[] start,
      final int count) {
    return new JedisClusterCommand<List<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<byte[]> execute(Jedis connection) {
        return connection.xrevrange(key, end, start, count);
      }
    }.runBinary(key);
  }

  @Override
  public List<byte[]> xread(final int count, final long block, final Map<byte[], byte[]> streams) {
    byte[][] keys = streams.keySet().toArray(new byte[streams.size()][]);

    return new JedisClusterCommand<List<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<byte[]> execute(Jedis connection) {
        return connection.xread(count, block, streams);
      }
    }.runBinary(keys.length, keys);
  }

  @Override
  public List<byte[]> xread(final XReadParams xReadParams, final Entry<byte[], byte[]>... streams) {
    return new JedisClusterCommand<List<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<byte[]> execute(Jedis connection) {
        return connection.xread(xReadParams, streams);
      }
    }.runBinary(streams.length, getKeys(streams));
  }

  @Override
  public long xack(final byte[] key, final byte[] group, final byte[]... ids) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.xack(key, group, ids);
      }
    }.runBinary(key);
  }

  @Override
  public String xgroupCreate(final byte[] key, final byte[] consumer, final byte[] id,
      final boolean makeStream) {
    return new JedisClusterCommand<String>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public String execute(Jedis connection) {
        return connection.xgroupCreate(key, consumer, id, makeStream);
      }
    }.runBinary(key);
  }

  @Override
  public String xgroupSetID(final byte[] key, final byte[] consumer, final byte[] id) {
    return new JedisClusterCommand<String>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public String execute(Jedis connection) {
        return connection.xgroupSetID(key, consumer, id);
      }
    }.runBinary(key);
  }

  @Override
  public long xgroupDestroy(final byte[] key, final byte[] consumer) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.xgroupDestroy(key, consumer);
      }
    }.runBinary(key);
  }

  @Override
  public long xgroupDelConsumer(final byte[] key, final byte[] consumer, final byte[] consumerName) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.xgroupDelConsumer(key, consumer, consumerName);
      }
    }.runBinary(key);
  }

  @Override
  public List<byte[]> xreadGroup(final byte[] groupname, final byte[] consumer, final int count,
      final long block, final boolean noAck, final Map<byte[], byte[]> streams) {

    byte[][] keys = streams.keySet().toArray(new byte[streams.size()][]);

    return new JedisClusterCommand<List<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<byte[]> execute(Jedis connection) {
        return connection.xreadGroup(groupname, consumer, count, block, noAck, streams);
      }
    }.runBinary(keys.length, keys);
  }

  @Override
  public List<byte[]> xreadGroup(final byte[] groupname, final byte[] consumer, final XReadGroupParams xReadGroupParams,
      final Entry<byte[], byte[]>... streams) {
    return new JedisClusterCommand<List<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<byte[]> execute(Jedis connection) {
        return connection.xreadGroup(groupname, consumer, xReadGroupParams, streams);
      }
    }.runBinary(streams.length, getKeys(streams));
  }

  @Override
  public long xdel(final byte[] key, final byte[]... ids) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.xdel(key, ids);
      }
    }.runBinary(key);
  }

  @Override
  public long xtrim(final byte[] key, final long maxLen, final boolean approximateLength) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.xtrim(key, maxLen, approximateLength);
      }
    }.runBinary(key);
  }

  @Override
  public long xtrim(final byte[] key, final XTrimParams params) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.xtrim(key, params);
      }
    }.runBinary(key);
  }

  @Override
  public List<Object> xpending(final byte[] key, final byte[] groupname, final byte[] start,
      final byte[] end, final int count, final byte[] consumername) {
    return new JedisClusterCommand<List<Object>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<Object> execute(Jedis connection) {
        return connection.xpending(key, groupname, start, end, count, consumername);
      }
    }.runBinary(key);
  }

  @Override
  public Object xpending(final byte[] key, final byte[] groupname) {
    return new JedisClusterCommand<Object>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Object execute(Jedis connection) {
        return connection.xpending(key, groupname);
      }
    }.runBinary(key);
  }

  @Override
  public List<Object> xpending(final byte[] key, final byte[] groupname, final XPendingParams params) {
    return new JedisClusterCommand<List<Object>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<Object> execute(Jedis connection) {
        return connection.xpending(key, groupname, params);
      }
    }.runBinary(key);
  }

  @Override
  public List<byte[]> xclaim(final byte[] key, final byte[] groupname, final byte[] consumername,
      final long minIdleTime, final long newIdleTime, final int retries, final boolean force,
      final byte[][] ids) {
    return new JedisClusterCommand<List<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<byte[]> execute(Jedis connection) {
        return connection.xclaim(key, groupname, consumername, minIdleTime, newIdleTime, retries,
          force, ids);
      }
    }.runBinary(key);
  }

  @Override
  public List<byte[]> xclaim(byte[] key, byte[] group, byte[] consumername, long minIdleTime,
      XClaimParams params, byte[]... ids) {
    return new JedisClusterCommand<List<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<byte[]> execute(Jedis connection) {
        return connection.xclaim(key, group, consumername, minIdleTime, params, ids);
      }
    }.runBinary(key);
  }

  @Override
  public List<byte[]> xclaimJustId(byte[] key, byte[] group, byte[] consumername, long minIdleTime,
      XClaimParams params, byte[]... ids) {
    return new JedisClusterCommand<List<byte[]>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<byte[]> execute(Jedis connection) {
        return connection.xclaimJustId(key, group, consumername, minIdleTime, params, ids);
      }
    }.runBinary(key);
  }

  @Override
  public List<Object> xautoclaim(final byte[] key, final byte[] groupName, final byte[] consumerName,
      final long minIdleTime, final byte[] start, XAutoClaimParams params) {
    return new JedisClusterCommand<List<Object>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<Object> execute(Jedis connection) {
        return connection.xautoclaim(key, groupName, consumerName, minIdleTime, start, params);
      }
    }.runBinary(key);
  }

  @Override
  public List<Object> xautoclaimJustId(final byte[] key, final byte[] groupName, final byte[] consumerName,
      final long minIdleTime, final byte[] start, XAutoClaimParams params) {
    return new JedisClusterCommand<List<Object>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<Object> execute(Jedis connection) {
        return connection.xautoclaimJustId(key, groupName, consumerName, minIdleTime, start, params);
      }
    }.runBinary(key);
  }

  @Override
  public Object xinfoStreamBinary(byte[] key) {
    return new JedisClusterCommand<Object>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Object execute(Jedis connection) {
        return connection.xinfoStreamBinary(key);
      }
    }.runBinary(key);
  }

  @Override
  public List<Object> xinfoGroupBinary(byte[] key) {
    return new JedisClusterCommand<List<Object>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<Object> execute(Jedis connection) {
        return connection.xinfoGroupBinary(key);
      }
    }.runBinary(key);
  }

  @Override
  public List<Object> xinfoConsumersBinary(byte[] key, byte[] group) {
    return new JedisClusterCommand<List<Object>>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public List<Object> execute(Jedis connection) {
        return connection.xinfoConsumersBinary(key, group);
      }
    }.runBinary(key);
  }

  @Override
  public long waitReplicas(final byte[] key, final int replicas, final long timeout) {
    return new JedisClusterCommand<Long>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Long execute(Jedis connection) {
        return connection.waitReplicas(replicas, timeout);
      }
    }.runBinary(key);
  }

  public Object sendCommand(final byte[] sampleKey, final ProtocolCommand cmd, final byte[]... args) {
    return new JedisClusterCommand<Object>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Object execute(Jedis connection) {
        return connection.sendCommand(cmd, args);
      }
    }.runBinary(sampleKey);
  }

  public Object sendBlockingCommand(final byte[] sampleKey, final ProtocolCommand cmd,
      final byte[]... args) {
    return new JedisClusterCommand<Object>(connectionHandler, maxAttempts, maxTotalRetriesDuration) {
      @Override
      public Object execute(Jedis connection) {
        return connection.sendBlockingCommand(cmd, args);
      }
    }.runBinary(sampleKey);
  }

  private static byte[][] getKeys(final Entry<byte[], ?>... entries) {
    byte[][] keys = new byte[entries.length][];
    for (int i = 0; i < entries.length; i++) {
      keys[i] = entries[i].getKey();
    }
    return keys;
  }
}

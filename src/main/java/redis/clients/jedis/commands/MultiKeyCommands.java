package redis.clients.jedis.commands;

import redis.clients.jedis.BitOP;
import redis.clients.jedis.GeoUnit;
import redis.clients.jedis.resps.KeyedZSetElement;
import redis.clients.jedis.StreamEntryID;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;
import redis.clients.jedis.SortingParams;
import redis.clients.jedis.StreamEntry;
import redis.clients.jedis.Tuple;
import redis.clients.jedis.ZParams;
import redis.clients.jedis.args.*;
import redis.clients.jedis.params.GeoRadiusParam;
import redis.clients.jedis.params.GeoRadiusStoreParam;
import redis.clients.jedis.params.XReadGroupParams;
import redis.clients.jedis.params.XReadParams;
import redis.clients.jedis.resps.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface MultiKeyCommands {

  boolean copy(String srcKey, String dstKey, int db, boolean replace);

  boolean copy(String srcKey, String dstKey, boolean replace);

  long del(String... keys);

  long unlink(String... keys);

  long exists(String... keys);

  String lmove(String srcKey, String dstKey, ListDirection from, ListDirection to);

  String blmove(String srcKey, String dstKey, ListDirection from, ListDirection to, double timeout);

  List<String> blpop(int timeout, String... keys);

  KeyedListElement blpop(double timeout, String... keys);

  List<String> brpop(int timeout, String... keys);

  KeyedListElement brpop(double timeout, String... keys);

  List<String> blpop(String... args);

  List<String> brpop(String... args);

  KeyedZSetElement bzpopmax(double timeout, String... keys);

  KeyedZSetElement bzpopmin(double timeout, String... keys);

  /**
   * Returns all the keys matching the glob-style pattern. For example if you have in the database
   * the keys "foo" and "foobar" the command "KEYS foo*" will return "foo foobar".<br>
   * <strong>Warning:</strong> consider this as a command that should be used in production
   * environments with <strong>extreme care</strong>. It may ruin performance when it is executed
   * against large databases. This command is intended for debugging and special operations, such as
   * changing your keyspace layout. <strong>Don't use it in your regular application code.</strong>
   * If you're looking for a way to find keys in a subset of your keyspace, consider using
   * {@link #scan(String, ScanParams)} or sets.
   * <p>
   * While the time complexity for this operation is O(N), the constant times are fairly low. For
   * example, Redis running on an entry level laptop can scan a 1 million key database in 40
   * milliseconds.
   * <p>
   * Glob style patterns examples:
   * <ul>
   * <li>h?llo will match hello hallo hhllo
   * <li>h*llo will match hllo heeeello
   * <li>h[ae]llo will match hello and hallo, but not hillo
   * </ul>
   * <p>
   * Use \ to escape special chars if you want to match them verbatim.
   * <p>
   * Time complexity: O(n) (with n being the number of keys in the DB, and assuming keys and pattern
   * of limited length)
   * @param pattern
   * @return Multi bulk reply
   * @see <a href="https://redis.io/commands/keys">Redis KEYS documentation</a>
   */
  Set<String> keys(String pattern);

  List<String> mget(String... keys);

  String mset(String... keysvalues);

  long msetnx(String... keysvalues);

  String rename(String oldkey, String newkey);

  long renamenx(String oldkey, String newkey);

  String rpoplpush(String srckey, String dstkey);

  Set<String> sdiff(String... keys);

  long sdiffstore(String dstkey, String... keys);

  Set<String> sinter(String... keys);

  long sinterstore(String dstkey, String... keys);

  long smove(String srckey, String dstkey, String member);

  long sort(String key, SortingParams sortingParameters, String dstkey);

  long sort(String key, String dstkey);

  Set<String> sunion(String... keys);

  long sunionstore(String dstkey, String... keys);

  String watch(String... keys);

  String unwatch();

  Set<String> zdiff(String... keys);

  Set<Tuple> zdiffWithScores(String... keys);

  long zdiffStore(String dstkey, String... keys);

  long zinterstore(String dstkey, String... sets);

  long zinterstore(String dstkey, ZParams params, String... sets);

  Set<String> zinter(ZParams params, String... keys);

  Set<Tuple> zinterWithScores(ZParams params, String... keys);

  Set<String> zunion(ZParams params, String... keys);

  Set<Tuple> zunionWithScores(ZParams params, String... keys);

  long zunionstore(String dstkey, String... sets);

  long zunionstore(String dstkey, ZParams params, String... sets);

  String brpoplpush(String source, String destination, int timeout);

  Long publish(String channel, String message);

  void subscribe(JedisPubSub jedisPubSub, String... channels);

  void psubscribe(JedisPubSub jedisPubSub, String... patterns);

  String randomKey();

  long bitop(BitOP op, String destKey, String... srcKeys);

  /**
   * @see #scan(String, ScanParams)
   *
   * @param cursor
   * @return
   */
  ScanResult<String> scan(String cursor);

  /**
   * Iterates the set of keys in the currently selected Redis database.
   * <p>
   * Since this command allows for incremental iteration, returning only a small number of elements
   * per call, it can be used in production without the downside of commands like
   * {@link #keys(String)} or {@link JedisCommands#smembers(String)} )} that may block the server
   * for a long time (even several seconds) when called against big collections of keys or elements.
   * <p>
   * SCAN basic usage<br>
   * SCAN is a cursor based iterator. This means that at every call of the command, the server
   * returns an updated cursor that the user needs to use as the cursor argument in the next call.
   * An iteration starts when the cursor is set to 0, and terminates when the cursor returned by the
   * server is 0.
   * <p>
   * Scan guarantees<br>
   * The SCAN command, and the other commands in the SCAN family, are able to provide to the user a
   * set of guarantees associated to full iterations.
   * <ul>
   * <li>A full iteration always retrieves all the elements that were present in the collection from
   * the start to the end of a full iteration. This means that if a given element is inside the
   * collection when an iteration is started, and is still there when an iteration terminates, then
   * at some point SCAN returned it to the user.
   * <li>A full iteration never returns any element that was NOT present in the collection from the
   * start to the end of a full iteration. So if an element was removed before the start of an
   * iteration, and is never added back to the collection for all the time an iteration lasts, SCAN
   * ensures that this element will never be returned.
   * </ul>
   * However because SCAN has very little state associated (just the cursor) it has the following
   * drawbacks:
   * <ul>
   * <li>A given element may be returned multiple times. It is up to the application to handle the
   * case of duplicated elements, for example only using the returned elements in order to perform
   * operations that are safe when re-applied multiple times.
   * <li>Elements that were not constantly present in the collection during a full iteration, may be
   * returned or not: it is undefined.
   * </ul>
   * <p>
   * Time complexity: O(1) for every call. O(N) for a complete iteration, including enough command
   * calls for the cursor to return back to 0. N is the number of elements inside the DB.
   * @param cursor The cursor.
   * @param params the scan parameters. For example a glob-style match pattern
   * @return the scan result with the results of this iteration and the new position of the cursor
   * @see <a href="https://redis.io/commands/scan">Redis SCAN documentation</a>
   */
  ScanResult<String> scan(String cursor, ScanParams params);

  String pfmerge(String destkey, String... sourcekeys);

  long pfcount(String... keys);

  long touch(String... keys);

  /**
   * XREAD [COUNT count] [BLOCK milliseconds] STREAMS key [key ...] ID [ID ...]
   *
   * @param count
   * @param block
   * @param streams
   * @return
   * @deprecated This method will be removed due to bug regarding {@code block} param. Use
   * {@link #xread(redis.clients.jedis.params.XReadParams, java.util.Map)}.
   */
  @Deprecated
  List<Map.Entry<String, List<StreamEntry>>> xread(int count, long block,
      Map.Entry<String, StreamEntryID>... streams);

  List<Map.Entry<String, List<StreamEntry>>> xread(XReadParams xReadParams,
      Map<String, StreamEntryID> streams);

  /**
   * XREAD [COUNT count] [BLOCK milliseconds] STREAMS key [key ...] ID [ID ...]
   *
   * @param groupname
   * @param consumer
   * @param count
   * @param block
   * @param noAck
   * @param streams
   * @return
   * @deprecated This method will be removed due to bug regarding {@code block} param. Use
   * {@link #xreadGroup(java.lang.String, java.lang.String, redis.clients.jedis.params.XReadGroupParams, java.util.Map)}.
   */
  @Deprecated
  List<Map.Entry<String, List<StreamEntry>>> xreadGroup(String groupname, String consumer,
      int count, long block, boolean noAck, Map.Entry<String, StreamEntryID>... streams);

  List<Map.Entry<String, List<StreamEntry>>> xreadGroup(String groupname, String consumer,
      XReadGroupParams xReadGroupParams, Map<String, StreamEntryID> streams);

  long georadiusStore(String key, double longitude, double latitude, double radius, GeoUnit unit,
      GeoRadiusParam param, GeoRadiusStoreParam storeParam);

  long georadiusByMemberStore(String key, String member, double radius, GeoUnit unit,
      GeoRadiusParam param, GeoRadiusStoreParam storeParam);
}

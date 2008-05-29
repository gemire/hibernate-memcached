/* Copyright 2008 Ray Krueger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.googlecode.hibernate.memcached;

import net.spy.memcached.MemcachedClient;
import net.spy.memcached.OperationTimeoutException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.cache.Cache;
import org.hibernate.cache.CacheException;
import org.hibernate.cache.Timestamper;

import java.util.Map;

/**
 * It is recommended that you use an instance MemcachedClient that is version 2.0.3 or better.
 *
 * @author Ray Krueger
 */
public class MemcachedCache implements Cache {

    private static final Log log = LogFactory.getLog(MemcachedCache.class);

    private final String regionName;
    private final MemcachedClient memcachedClient;
    private final String cleanIndexKey;
    private int cacheTimeSeconds = 300;
    private boolean clearSupported = false;
    private KeyStrategy keyStrategy = new DefaultKeyStrategy();

    public MemcachedCache(String regionName, MemcachedClient memcachedClient) {
        this.regionName = (regionName != null) ? regionName : "default";
        this.memcachedClient = memcachedClient;
        cleanIndexKey = this.regionName.replace(' ', '_') + ":index_key";
    }

    public int getCacheTimeSeconds() {
        return cacheTimeSeconds;
    }

    public void setCacheTimeSeconds(int cacheTimeSeconds) {
        this.cacheTimeSeconds = cacheTimeSeconds;
    }

    public boolean isClearSupported() {
        return clearSupported;
    }

    public void setClearSupported(boolean clearSupported) {
        this.clearSupported = clearSupported;
    }

    private Object memcacheGet(Object key) {
        String stringKey = toKey(key);
        log.debug("Memcache.get(" + stringKey + "}");
        try {
            return memcachedClient.get(stringKey);
        } catch (OperationTimeoutException e) {
            log.warn("Cache 'get' timed out for key [" + stringKey + "]", e);
        }
        return null;
    }

    private void memcacheSet(Object key, Object o) {
        String stringKey = toKey(key);
        log.debug("Memcache.set(" + stringKey + "}");
        memcachedClient.set(stringKey, cacheTimeSeconds, o);
    }

    private String toKey(Object key) {
        return keyStrategy.toKey(regionName, getCleanIndex(), key);
    }

    public Object read(Object key) throws CacheException {
        return memcacheGet(key);
    }

    public Object get(Object key) throws CacheException {
        return memcacheGet(key);
    }

    public void put(Object key, Object value) throws CacheException {
        memcacheSet(key, value);
    }

    public void update(Object key, Object value) throws CacheException {
        memcacheSet(key, value);
    }

    public void remove(Object key) throws CacheException {
        memcachedClient.delete(toKey(key));
    }

    public void clear() throws CacheException {
        if (clearSupported) {
            memcachedClient.incr(cleanIndexKey, 1, 1);
        }
    }

    public void destroy() throws CacheException {
        //the client is shared by default with all cache instances, so don't shut it down.
    }

    public void lock(Object key) throws CacheException {
    }

    public void unlock(Object key) throws CacheException {
    }

    public long nextTimestamp() {
        return Timestamper.next();
    }

    public int getTimeout() {
        return cacheTimeSeconds;
    }

    public String getRegionName() {
        return regionName;
    }

    public long getSizeInMemory() {
        return -1;
    }

    public long getElementCountInMemory() {
        return -1;
    }

    public long getElementCountOnDisk() {
        return -1;
    }

    public Map toMap() {
        throw new UnsupportedOperationException();
    }

    public String toString() {
        return "Memcached (" + regionName + ")";
    }

    private long getCleanIndex() {
        Long index = null;

        if (clearSupported) {
            try {
                Object value = memcachedClient.get(cleanIndexKey);
                if (value instanceof String) {
                    index = Long.parseLong((String) value);
                } else if (value instanceof Long) {
                    index = (Long) value;
                } else {
                    throw new IllegalArgumentException(
                            "Unsupported type found for clean index at cache key [" + cleanIndexKey + "]");
                }
            } catch (OperationTimeoutException e) {
                log.warn("Cache 'get' timed out for key [" + cleanIndexKey + "]", e);
            }
            if (index != null) {
                return index;
            }
        }

        return 0L;
    }

    public KeyStrategy getKeyStrategy() {
        return keyStrategy;
    }

    public void setKeyStrategy(KeyStrategy keyStrategy) {
        this.keyStrategy = keyStrategy;
    }
}
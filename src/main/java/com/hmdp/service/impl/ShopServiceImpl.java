package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  Service implementation
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // A thread pool is needed here: cache breakdown recovery below rebuilds the cache on a separate thread
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    @Override
    public Result queryById(Long id) {
        // Cache-penetration-proof logic
        Shop shop = querywithchuantou(id);
        // Mutex-based cache-breakdown-proof logic
//        Shop shop = querywithjichuan_mutex(id);
//       Shop shop = queryWithLogicalExpire(id);
        if (shop == null) {
            return Result.fail("Shop not found");
        }
        return Result.ok(shop);
    }

    // Guard against cache penetration
    public Shop querywithchuantou(Long id) {
        // Check Redis first; the key is a fixed prefix + shop ID
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        // Cache hit: convert to Shop and return directly
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }

        // Cache a missing entry as an empty string with a short TTL, then return the error.
        // On the next query, an empty-string value means we cached a miss: return the error directly

        // An empty string means this is our cached miss
        if (shopJson!=null) {
            return  null;
        }

        // Otherwise query the database
        Shop shop = getById(id);

        // Miss: write an empty string to Redis
        if (shop == null) {
            // TTL here is 2 minutes
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, null, CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // Hit: serialize to JSON
        String jsonStr = JSONUtil.toJsonStr(shop);
        // Store in Redis with a TTL to avoid stale cache entries
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, jsonStr,CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // Finally return the shop info to the frontend
        return shop;
    }

    // Mutex-based cache breakdown protection
    public Shop querywithjichuan_mutex(Long id) {
        // Check Redis first; the key is a fixed prefix + shop ID
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        // Cache hit: convert to Shop and return directly
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }

        // An empty string "" means this is our cached miss
        if (shopJson!=null) {
            return  null;
        }

        // Rebuild the cache under high concurrency
        Shop shop = null;
        try {
            // 1. Acquire the mutex
            boolean flag = tryLock(LOCK_SHOP_KEY + id);
// 2. Failed: sleep and retry
            while (!flag) {
                Thread.sleep(50);
                return querywithjichuan_mutex(id);
            }
            // 3. Acquired: read the DB and rebuild the cache
            // Miss: write the null marker to Redis
            shop = getById(id);
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // Hit: serialize to JSON
            String jsonStr = JSONUtil.toJsonStr(shop);
            // Store in Redis with a TTL
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, jsonStr, CACHE_SHOP_TTL, TimeUnit.MINUTES);
            // Finally return the shop info to the frontend
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unlock(LOCK_SHOP_KEY + id);
        }
        return shop;
    }

    // Logical-expiration cache breakdown protection
    public Shop queryWithLogicalExpire(Long id) {
        // 1. Read the shop cache from Redis
        String json = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        // 2. Miss: return null
        if (StrUtil.isBlank(json)) {
            return null;
        }
        // 3. Hit: deserialize the JSON
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        // 3.1 Convert data to a Shop
        JSONObject shopJson = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(shopJson, Shop.class);
        // 3.2 Get the expiration time
        LocalDateTime expireTime = redisData.getExpireTime();
        // 4. Check whether it is expired
        if (LocalDateTime.now().isBefore(expireTime)) {
            // 5. Not expired: return the shop info directly
            return shop;
        }
        // 6. Expired: try to acquire the mutex
        boolean flag = tryLock(LOCK_SHOP_KEY + id);
        // 7. Lock acquired
        if (flag) {
            // 8. Start a background thread
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    this.saveShop2Redis(id, 20L);// expirSeconds should be the item's active duration; 20 is just for testing
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(LOCK_SHOP_KEY + id);
                }
            });
            // 9. Return the shop info directly
            return shop;
        }
        // 10. Lock not acquired: return the shop info directly
        return shop;
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
// Null check first
        if (shop.getId() == null){
            return Result.fail("Shop ID must not be null");
        }
        // Update the database first
        updateById(shop);
        // Then evict the cache
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }

    // Below: cache breakdown protection for hot keys under high concurrency

    // Lock acquisition logic
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        // Guard against a null return with BooleanUtil
        return BooleanUtil.isTrue(flag);
    }

    // Release the lock
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    //-------------------------------------------------------------
    // Logical expiration for cache breakdown; pre-warm hot data
    public void saveShop2Redis(Long id, Long expirSeconds) throws InterruptedException {
        Shop shop = getById(id);
        Thread.sleep(200); // Simulate the fetch latency above

        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expirSeconds));
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 1. Decide whether a geo query is needed
        if (x == null || y == null) {
            // Query by type with pagination
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // Return the data
            return Result.ok(page.getRecords());
        }
// The following is the geo query path

        // 2. Compute pagination bounds
        int from = (current - 1) * SystemConstants.MAX_PAGE_SIZE;
        int end = current * SystemConstants.MAX_PAGE_SIZE;


        String key = SHOP_GEO_KEY + typeId;
        // 3. Query Redis sorted by distance with pagination; result: shopId + distance
        //GEOSEARCH key FROMLONLAT x y BYRADIUS 5000 m WITHDIST
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(key,
                GeoReference.fromCoordinate(x, y),
                new Distance(5000),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end));

        if (results == null) {
            return Result.ok(Collections.emptyList());
        }

        // 4. Parse out the IDs
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();

        if (list.size() < from) {
            // Start offset beyond the total: no more data, return an empty list
            return Result.ok(Collections.emptyList());
        }

        ArrayList<Long> ids = new ArrayList<>(list.size());
        HashMap<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });


        // 5. Query shops by IDs
        String idsStr = StrUtil.join(",", ids);

        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD( id," + idsStr + ")").list();
        for (Shop shop : shops) {
            // Set each shop's distance from distanceMap by shop ID
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        // 6. Return
        return Result.ok(shops);
    }
}

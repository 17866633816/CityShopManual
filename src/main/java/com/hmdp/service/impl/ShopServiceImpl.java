package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisClient;
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

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 周星星
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisClient redisClient;

    /**
     * 根据id查询商户信息
     *
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {

        //非热点key，使用Redis缓存商户信息,不解决任何问题
        //shop = commonCache(id);

        //缓存穿透
        //Shop shop = redisClient.queryByIdWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, id2 -> getById(id2), 20l, TimeUnit.SECONDS);

        //互斥锁解决缓存击穿
        //Shop shop = queryByIdWithMutex(id);

        //逻辑过期解决缓存击穿
        //Shop shop = redisClient.queryByIdWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, id2 -> getById(id2), 20l, TimeUnit.SECONDS);

        //判断此店铺是否是热点key
        Shop shop = null;
        if (id == 1) {
            //是热点key，走逻辑过期解决缓存击穿
            //shop = queryByIdWithLogicalExpire(id);
            shop = redisClient.queryByIdWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, id2 -> getById(id2), 60l, TimeUnit.SECONDS);
        }else{
            //非热点key，走缓存空值解决缓存穿透
            //shop = queryByIdWithPassThrough(id);
            shop = redisClient.queryByIdWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, id2 -> getById(id2), 60l, TimeUnit.SECONDS);
        }

        if (shop == null) {
            return Result.fail("不存在此店铺!!");
        }

        return Result.ok(shop);
    }

    @Transactional
    @Override
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为null");
        }
        //更新数据库中的商家信息
        updateById(shop);

        //删除缓存中的商家信息
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);

        return Result.ok();
    }

    /**
     * 根据店铺类型分页查询店铺
     * @param typeId
     * @param current
     * @param x
     * @param y
     * @return
     */
    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {

        // 1.判断是否需要根据坐标查询
        if (x == null || y == null) {
            // 不需要坐标查询，按数据库查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }

        // 2.计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        // 3.查询redis、按照距离排序、分页。结果：shopId、distance
        String key = SHOP_GEO_KEY + typeId;
        // GEOSEARCH key BYLONLAT x y BYRADIUS 10 WITHDISTANCE
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );

        // 4.解析出id
        if (results == null) {
            //如果一个商家也没查询出来，返回给前端一个空集合
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from) {
            // 没有下一页了，结束
            return Result.ok(Collections.emptyList());
        }
        // 4.1.截取 from ~ end 的部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            // 4.2.获取店铺id
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            // 4.3.获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });

        // 5.根据id查询Shop
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }

        // 6.返回
        return Result.ok(shops);
    }

    private Shop commonCache(Long id) {
        //1.从Redis中查询
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        //2.判断Redis是否存在商户信息
        if (shopJson != null && !shopJson.isEmpty()) {
            //2.1 存在，直接返回给前端
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }

        //3.不存在，缓存重建
        //3.1 从数据库中查询
        Shop shop1 = getById(id);
        //3.2 判断数据库中是否存在
        if (shop1 == null) {
            //没有查询到，返回null
            return null;
        }

        //4.查询成功，缓存到redis中一份
        String jsonStr = JSONUtil.toJsonStr(shop1);
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, jsonStr, 10L, TimeUnit.MINUTES);

        //5.发送给前端
        return shop1;
    }

    //缓存空对象解决缓存穿透
    private Shop queryByIdWithPassThrough(long id) {
        //从redis中查询此id对应的商店信息
        String key = CACHE_SHOP_KEY + id;
        String cacheShop = stringRedisTemplate.opsForValue().get(key);

        //判断redis中是否存在此商店信息
        if (StrUtil.isNotBlank(cacheShop)) {
            //有此信息，返回给客户端
            Shop shop = JSONUtil.toBean(cacheShop, Shop.class);
            return shop;
        }

        //查看是否是空值。这里的cacheShop不是 null 就是 ""
        if (cacheShop != null) {
            return null;
        }

        //无此信息，从数据库中查询
        Shop shop = getById(id);

        //判断数据库中是否存在此商家信息
        if (shop == null) {
            //不存在
            //向redis中存入一个空值
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            // 返回错误信息
            return null;
        }

        //存在，保存到redis中一份，并设置过期时间
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //发给客户端一份
        return shop;
    }

    //互斥锁解决缓存击穿、顺带解决了缓存穿透
    private Shop queryByIdWithMutex(long id) {

        //从redis中查询此id对应的商店信息
        String key = CACHE_SHOP_KEY + id;
        String cacheShop = stringRedisTemplate.opsForValue().get(key);

        //判断redis中是否存在此商店信息
        //首先，isNotBlank()方法通过调用isBlank()方法来判断字符串是否为空。isBlank()方法首先检查字符串是否为null或长度为0，如果是，则返回true表示为空。
        //否则，它会遍历字符串的每一个字符，并检查每个字符是否为空白字符（包括空格、制表符、换行符等），使用Character.isWhitespace()方法进行判断。
        //如果字符串中存在非空白字符，则返回false表示不为空；如果所有字符都为空白字符，则返回true表示为空
        if (StrUtil.isNotBlank(cacheShop)) {
            //有此信息，返回给客户端
            Shop shop = JSONUtil.toBean(cacheShop, Shop.class);
            return shop;
        }

        //查看是否是空值。这里的cacheShop不是 null 就是 ""
        if (cacheShop != null) {
            return null;
        }
        String lockKey = null;
        Shop shop = null;

        try {
            //获取锁
            lockKey = LOCK_SHOP_KEY + id;
            boolean isLock = tryLock(lockKey);

            //判断是否获取锁成功
            if (isLock == false) {
                //获取失败，休眠一段时间
                Thread.sleep(50);
                //休眠完成，再次尝试获取商铺信息
                return queryByIdWithMutex(id);
            }

            //获取锁成功，进行缓存重建
            //从数据库中查询商家信息
            shop = getById(id);

            //判断数据库中是否存在此商家信息
            if (shop == null) {
                //不存在
                //向redis中存入一个空字符串
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                // 返回错误信息
                return null;
            }

            //存在，保存到redis中一份，并设置过期时间
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放锁
            unLock(lockKey);
        }

        //发给客户端一份
        return shop;

    }

    //逻辑过期解决缓存击穿
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public Shop queryByIdWithLogicalExpire(Long id) {

        //1.从redis查询商铺缓存
        String key = CACHE_SHOP_KEY + id;
        String cacheShop = stringRedisTemplate.opsForValue().get(key);

        //2.判断redis中是否存在此商店信息
        if (StrUtil.isBlank(cacheShop)) {
            //3.不存在，返回null
            return null;
        }

        //4.命中，需要将JSON反序列化为RedisData对象
        RedisData redisData = JSONUtil.toBean(cacheShop, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();

        //5.根据逻辑删除字段判断缓存是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //5.1 未过期，直接返回店铺信息
            return shop;
        }
        //5.2 已过期，需要缓存重建

        //6.缓存重建
        //6.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        //6.2 判断是否获取锁成功
        if (isLock) {
            //6.3 成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //重建缓存
                    this.saveShop2Redis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unLock(lockKey);
                }
            });
        }
        //6.4 返回过期的店铺信息
        return shop;

    }

    //获取锁
    public boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag); //flag为true时返回true，为false和null时返回false
    }

    //释放锁
    public void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

    //向redis中存入热点key
    public void saveShop2Redis(Long id, Long expireTime) throws InterruptedException {
        //1.查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(200);
        //2.封装逻辑过期时间和店铺数据
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireTime));
        //3.写入Redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

}

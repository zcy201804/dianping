package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    //    线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 缓存穿透+缓存击穿
     */
    @Override
    public Result getSopMsg(Long id) {

        //    缓存穿透
//        return cachePenetration(id);
//        调用工具类函数
        Shop shop = cacheClient.get1(CACHE_SHOP_KEY, id, Shop.class, id1 -> getById(id1), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return Result.ok(shop);

        //缓存击穿---互斥锁解决缓存击穿
//        return cacheBreakdown1(id);

        //   缓存击穿---逻辑过期
//        return cacheBreakdown2(id);
    }

    //    缓存穿透
    public Result cachePenetration(Long id) {
//        从redis中查询数据
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//        判断redis中是否存在
        if (StrUtil.isNotBlank(shopJson)) {
//        存在，则直接返回,先将json字符串转为对象
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
//        命中是否是空值,空字符串
        if (shopJson != null) {
            return Result.fail("商铺不存在");
        }
//        不存在，从数据库中查询
        Shop shop = getById(id);
//        判断数据库中是否存在
        if (shop == null) {
//        不存在，返回错误信息
//            将redis缓存和数据库中都不存在的id写入到redis缓存中，缓存空值，避免缓存穿透
            stringRedisTemplate.opsForValue().set(CACHE_NULL_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail("商铺不存在");
        }

        String toJsonStr = JSONUtil.toJsonStr(shop);
//        存在，需要先保存在redis中，将shop对象转为json字符串，设置过期时间
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, toJsonStr, CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        返回数据
        return Result.ok(shop);
    }

//   缓存击穿---互斥锁解决缓存击穿

    public Result cacheBreakdown1(Long id) {
//        从redis中查询数据
        String key = CACHE_SHOP_KEY + id;
        Shop shop = null;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        判断数据是否命中
        if (StrUtil.isNotBlank(shopJson)) {
//        命中，直接返回数据
//             将json字符串序列化
            shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
//        未命中，判断是否是空值
        if (shopJson != null) {
            return Result.fail("商铺不存在");
        }
//       未命中，不是空值 获取互斥锁
        String lockKey = "lock:shop" + id;
        boolean isLock = getLock(lockKey);
//        获取锁失败，休眠等待，休眠结束，继续从redis中查询
        try {
            if (!isLock) {
                Thread.sleep(50);
                cacheBreakdown1(id);
            }

//        获取锁成功，查询数据库，更新redis，返回数据，
            shop = getById(id);
//            模拟重建延迟
            Thread.sleep(200);
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(key, "", CACHE_SHOP_TTL, TimeUnit.MINUTES);
                return Result.fail("商铺不存在");
            }
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);


        } catch (InterruptedException e) {

        } finally {
            //        释放互斥锁
            delLock(lockKey);
        }
        return Result.ok(shop);
    }

    //   缓存击穿---逻辑过期

    public Result cacheBreakdown2(Long id) {
//      从redis中查询数据
        String shopKeys = CACHE_SHOP_KEY + id;
        String shopJsonStr = stringRedisTemplate.opsForValue().get(shopKeys);
//        判断是否命中
        if (StrUtil.isBlank(shopJsonStr)) {
            //        未命中。返回空
            return Result.ok("商铺不存在");
        }

//        命中判断 数据逻辑是否过期
        RedisData redisData = JSONUtil.toBean(shopJsonStr, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        JSONObject data1 = (JSONObject) redisData.getData();
        Shop data = JSONUtil.toBean(data1, Shop.class);
//        逻辑未过期，直接返回数据
        if (expireTime.isAfter(LocalDateTime.now())) {
            return Result.ok(data);
        }
//        逻辑过期，获取互斥锁
        String lockKey = CACHE_SHOP_Lock_KEY + id;
        boolean lock = getLock(lockKey);
//        判断获取锁是否成功
        if (!lock) {
            //        获取锁失败，直接从redis中查询，返回旧数据
            return Result.ok(data);
        }
//        获取锁成功，另起一个独立线程去查询数据库，更新redis,释放锁，本线程直接返回旧数据
        CACHE_REBUILD_EXECUTOR.submit(() -> {
            saveCacheBreakdown2Data(id, 20L);
//             释放锁
            delLock(lockKey);
        });
        return Result.ok(data);
    }

    //    保存需要逻辑过期的数据
    public void saveCacheBreakdown2Data(Long id, Long expiredTime) {
//        从数据库查询数据
        Shop shop = getById(id);
//        模拟缓存重建延迟
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (shop != null) {
            //        将数据封装成逻辑过期
            RedisData redisData = new RedisData();
            redisData.setData(shop);
            redisData.setExpireTime(LocalDateTime.now().plusSeconds(expiredTime));
            stringRedisTemplate.opsForValue()
                    .set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
        }
//        保存到redis
    }

    //    设置互斥锁
    private boolean getLock(String key) {
        Boolean aBoolean = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(aBoolean);
    }

    //释放互斥锁
    private void delLock(String key) {
        stringRedisTemplate.delete(key);
    }

    @Override
    @Transactional     //事务控制
    public Result updateShopMsg(Shop shop) {
        if (shop.getId() == null) {
            return Result.fail("商铺id不能为空");
        }
//        先更新数据库
        updateById(shop);
//        再删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }
}

package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Component
public class CacheClient {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    //    线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public void set1(String key, Object value, Long time, TimeUnit timeUnit){
     stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,timeUnit);
    }
//    逻辑过期
    public void set2(String key, Object value,Long time,TimeUnit timeUnit){
//        设置逻辑过期时间
        RedisData redisData=new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
//        存入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

//    缓存穿透获取数据
    public <R,ID> R get1(String keyPrefix, ID id , Class<R> type, Function<ID,R> dbFallback,Long time,TimeUnit timeUnit){
//        从redis中查询数据
        String key=keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
//        判断redis中是否存在
        if (StrUtil.isNotBlank(json)) {
//        存在，则直接返回,先将json字符串转为对象
            R r = JSONUtil.toBean(json, type);
            return r;
        }
//        命中是否是空值,空字符串
        if (json != null) {
            return null;
        }
//        不存在，从数据库中查询
//        Shop shop = getById(id);
        R r=dbFallback.apply(id);
//        判断数据库中是否存在
        if (r == null) {
//        不存在，返回错误信息
//            将redis缓存和数据库中都不存在的id写入到redis缓存中，缓存空值，避免缓存穿透
            stringRedisTemplate.opsForValue().set(key, "",time, timeUnit);
            return null;
        }

        String toJsonStr = JSONUtil.toJsonStr(r);
//        存在，需要先保存在redis中，将shop对象转为json字符串，设置过期时间
//        stringRedisTemplate.opsForValue().set(key, toJsonStr, time, timeUnit);
       this.set1(key,toJsonStr,time,timeUnit);
//        返回数据
        return r;
    }

    public <R,ID> R  get2(String keyPrefix,ID id,Class<R> type,String lockKey,Function<ID,R> idrFunction,Long time,TimeUnit timeUnit){
//      从redis中查询数据
        String key=keyPrefix+id;
//        String shopKeys = CACHE_SHOP_KEY + id;
        String shopJsonStr = stringRedisTemplate.opsForValue().get(key);
//        判断是否命中
        if (StrUtil.isBlank(shopJsonStr)) {
            //        未命中。返回空
            return null;
        }

//        命中判断 数据逻辑是否过期
        RedisData redisData = JSONUtil.toBean(shopJsonStr, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        JSONObject data1 = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data1,type);
//        逻辑未过期，直接返回数据
        if (expireTime.isAfter(LocalDateTime.now())) {
            return r;
        }
//        逻辑过期，获取互斥锁
//        String lockKey = CACHE_SHOP_Lock_KEY + id;
        boolean lock = getLock(lockKey);
//        判断获取锁是否成功
        if (!lock) {
            //        获取锁失败，直接从redis中查询，返回旧数据
            return r;
        }
//        获取锁成功，另起一个独立线程去查询数据库，更新redis,释放锁，本线程直接返回旧数据
        CACHE_REBUILD_EXECUTOR.submit(() -> {
//            saveCacheBreakdown2Data(id, 20L);
//            查询数据库
            R r1 = idrFunction.apply(id);
//            写入redis
            this.set1(key,r1,time,timeUnit);
//             释放锁
            delLock(lockKey);
        });
        return r;
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

}

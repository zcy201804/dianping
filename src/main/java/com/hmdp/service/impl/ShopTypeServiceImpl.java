package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOPTYPE_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOPTYPE_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public Result getShopTypeMsg() {
//        先去redis中查询
//        List<ShopType> leftPop=(List<ShopType>) redisTemplate.opsForList().leftPop(LOGIN_shopType_KEY);
       List<String> range = redisTemplate.opsForList().range(CACHE_SHOPTYPE_KEY, 0, -1);
//        判断数据是否存在
        if(range.size()>0) {
//        存在，转换类型返回
            List<ShopType> list= JSONUtil.toList(range.get(0),ShopType.class);
            return Result.ok(list);
        }
//        不存在，从数据库查询
        List<ShopType> typeList =query().orderByAsc("sort").list();
//        判断数据是否存在
        if(typeList.size()<0) {
//        不存在，返回错误信息
           return Result.fail("店铺不存在");
        }
        //        存在，先保存在redis中，
        String jsonStr = JSONUtil.toJsonStr(typeList);
        redisTemplate.opsForList().leftPush(CACHE_SHOPTYPE_KEY,jsonStr);
        redisTemplate.expire(CACHE_SHOPTYPE_KEY,CACHE_SHOPTYPE_TTL, TimeUnit.MINUTES);
//        再返回
        return Result.ok(typeList);
    }
}

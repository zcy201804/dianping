package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 前端请求拦截器，第一次拦截，拦截所有请求
 * */
@Component
public class RefreshTokenInterceptor implements HandlerInterceptor {

// 通过构造方法注入StringRedisTemplate
    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate=stringRedisTemplate;
    }

    //    前置拦截
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler){

        System.out.println("进入第一个拦截器");
//        获取请求头中的token
        String token = request.getHeader("authorization");
        if(StrUtil.isBlank(token)){
//            为空不拦截，直接放行
//            response.setStatus(401);
            return true;
        }
        String tokeny=RedisConstants.LOGIN_USER_KEY + token;
//        基于token去redis中获取用户信息
        Map<Object, Object> map = stringRedisTemplate.opsForHash().entries(tokeny);
//         判断用户是否存在
        if(map==null) {
//        不存在，不拦截
//            response.setStatus(401);
            return true;
        }
//        存在，将redis中查询到的数据转为UserDTO
        UserDTO userDTO = BeanUtil.fillBeanWithMap(map, new UserDTO(), false);
//        保存用户信息到ThreadLocal
        UserHolder.saveUser(userDTO);
//        刷新token的有效期
        stringRedisTemplate.expire(tokeny,RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }


    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}

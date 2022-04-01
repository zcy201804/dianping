package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 前端请求拦截器
 * */

public class LoginInterceptor implements HandlerInterceptor {

// 通过构造方法注入StringRedisTemplate
    /**
     *
    private StringRedisTemplate stringRedisTemplate;

    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate=stringRedisTemplate;
    }
     */
    //    前置拦截
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
/**

//        获取session
        HttpSession session = request.getSession();
//       获取session中的用户
        Object user = session.getAttribute("user");
//        判断用户是否存在
        if(user==null) {
//        不存在，拦截
            response.setStatus(401);
            return false;
        }
//       存在，将用户信息保存到ThreadLocal
         UserHolder.saveUser((UserDTO) user);
//        放行
*/

//       在此拦截器前增加了一个拦截器，所以很多拦截器功能转移到了第一个拦截器
        /**
        *

//        获取请求头中的token
        String token = request.getHeader("authorization");
        if(StrUtil.isBlank(token)){
//            拦截
            response.setStatus(401);
            return false;
        }
        String tokeny=RedisConstants.LOGIN_USER_KEY + token;
//        基于token去redis中获取用户信息
        Map<Object, Object> map = stringRedisTemplate.opsForHash().entries(tokeny);
//         判断用户是否存在
        if(map==null) {
//        不存在，拦截
            response.setStatus(401);
            return false;
        }
//        存在，将redis中查询到的数据转为UserDTO
        UserDTO userDTO = BeanUtil.fillBeanWithMap(map, new UserDTO(), false);
//        保存用户信息到ThreadLocal
        UserHolder.saveUser(userDTO);
//        刷新token的有效期
        stringRedisTemplate.expire(tokeny,RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
         * */
//      判断是否需要拦截，ThreadLocal中是否有用户
        UserDTO user = UserHolder.getUser();
        if(user==null){
            response.setStatus(401);
            return false;
        }

        return true;
    }

//
//    @Override
//    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
//        UserHolder.removeUser();
//
//    }
}

package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RefreshTokenInterceptor refreshTokenInterceptor;

//    @Autowired
//    private LoginInterceptor loginInterceptor;

    /**
     * 配置拦截器
     * */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        //默认拦截所有请求，需要先执行，调节order的值，值越大优先级越低
//        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).addPathPatterns("/**").order(0);
        registry.addInterceptor(refreshTokenInterceptor).addPathPatterns("/**").order(0);

       //登录拦截器
        registry.addInterceptor(new LoginInterceptor()).excludePathPatterns(
                     //不需要拦截的请求
                        "/user/login",
                        "/user/code",
                        "/blog/hot",
                        "/shop-type/**",
                        "/voucher/**",
                        "/shop/**"
                ).order(1);
    }
}

package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
//      1  校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
//       2  手机号不符合规则，返回错误信息
            return  Result.fail("手机格式错误");
        }
//       3手机号符合规则，完成校验
        String code = RandomUtil.randomNumbers(6);

////       4保存验证码到session
//         session.setAttribute("code",code);

//        保存验证码到redis，以前缀+手机号为key,并设置有效期
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);

//       5 发送验证码
         log.debug("发送短信验证码成功，验证码为："+code);
//        返回result.ok
        return Result.ok();
    }


    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
//        1 校验手机号
        String phone=loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
//         手机号不符合规则，返回错误信息
            return  Result.fail("手机格式错误");
        }

 /**
         *
//        从session中获取验证码
        Object cachecode = session.getAttribute("code");
        //        2  校验验证码
        String code=loginForm.getCode();
//        3  不一致
        if(cachecode==null || !cachecode.equals(code)){

            return Result.fail("验证码错误");
        }

//        4  一致，根据手机号查询用户信息
        User user = query().eq("phone", phone).one();
//        5 判断用户是否存在
        if(user==null) {
//        6 不存在创建新用户并保存
        user=insertUser(phone);
        }
//        7 保存用户信息到session中
        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
*/
//           从redis中获取验证码
        String code = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
        Object cachecode = loginForm.getCode();
//           判断验证码，不一致
        if(cachecode==null || !cachecode.equals(code)){

            return Result.fail("验证码错误");
        }
//        验证码一致,根据手机号查询用户信息
        User user = query().eq("phone", phone).one();
//        5 判断用户是否存在
        if(user==null) {
//        6 不存在创建新用户并保存
            user=insertUser(phone);
        }
//          随机生成token，作为登录令牌,将user保存到redis中
        String token=UUID.randomUUID().toString();
//        将uesr对象转为hash存储到redis，设置过期时间
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> stringObjectMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true)
                        .setFieldValueEditor((fileName,fileValue) -> fileValue.toString()));

        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY+token,stringObjectMap);
//        设置token有效期
        stringRedisTemplate.expire(LOGIN_USER_KEY+token,LOGIN_USER_TTL,TimeUnit.MINUTES);

        //返回token
        return Result.ok(token);
    }

    private User insertUser(String phone) {
        User user=new User();
        user.setNickName(USER_NICK_NAME_PREFIX +RandomUtil.randomString(8));
        user.setPhone(phone);
        save(user);
        return user;
    }
}

package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * @title 用户实现类
 * @description
 * @author wzy
 * @updateTime 2023/3/9 15:52
 * @throws
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public Result sedCode(String phone, HttpSession session) {
        //1. 校验手机号格式
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2.如果不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }

        //3. 符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
        //4. 保存验证码到Redis  set key value ex 2
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //5. 发送验证码
        log.debug("发送短信验证码成功，验证码:{}", code);
        //返回ok
        return Result.ok();
    }

    @Override
    public Result sign() {
        // 1.获取当前登录的用户
        Long userId = UserHolder.getUser().getId();

        // 2.获取日期
        LocalDateTime now = LocalDateTime.now();
        // 3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        // 5.写入Redis： setbit key offset 1
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    /**
     * 重点：涉及 BitMap，
     * 知识点：
     * 1.包括取一段时间内的数据
     * 2.涉及位运算符，与运算
     *
     * @return
     */
    @Override
    public Result signCount() {
        // 1.获取当前登录的用户
        Long userId = UserHolder.getUser().getId();

        // 2.获取日期
        LocalDateTime now = LocalDateTime.now();
        // 3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();

        // 5.获取本月截止今天为止的所有的签到记录，返回的是一个十进制的数字 bitfield bm1 get u2(u:代表无符号) 0
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if (result == null || result.isEmpty()) {
            // 没有任何签到结果
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null || num == 0) {
            return Result.ok(0);
        }
        // 6.循环遍历
        int count = 0; // 计数器
        while (true) {
            // 6.1.让这个数字与1做 与 运算，得到数字的最后一个bit位
            // 6.2.判断这个bit位是否为0
            if ((num & 1) == 0) {
                // 6.3.如果为0，说明未签到，结束
                break;
            } else {
                // 6.4.如果不为0，说明已签到，计数器+1
                count++;
            }

            // 6.5.把数字右移一位，抛弃最后一个bit位，继续下一个bit位
            num >>>= 1;
        }


        return Result.ok(count);
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1. 校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        //2. 从Redis中获取验证码，并校验验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            //3. 不一致，报错
            return Result.fail("验证码错误");
        }

        //4.一致，根据手机号查询用户，select * from tb_user where iphone = ?
        User user = query().eq("phone", phone).one();

        //5. 判断用户是否存在
        if (user == null) {
            //6. 不存在，创建新用户
            user = createUserWithPhone(phone);
        }

        // 7. 保存用户信息到Redis中
        // 7.1 随机生成token，作为登录令牌
        String token = UUID.randomUUID().toString(true); // 不带下划线
        // 7.2 将User对象转为 Hash存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // hutool工具类，对象转Map
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(), CopyOptions.create() // 创建CopyOptions，并自定义
                .setIgnoreNullValue(true) // 忽略空值
                .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString())); // 允许修改字段值
        // 7.3 存储
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);

        // 7.4 设置token有效期
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        return Result.ok(token);
    }


    private User createUserWithPhone(String phone) {
        // 1.创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        // 2.保存用户
        save(user);
        return user;
    }

    @Override
    public void logout(String token) {
        // 1.获取当前登录的用户
        Long userId = UserHolder.getUser().getId();

        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.delete(tokenKey);

        //移除用户
        UserHolder.removeUser();
    }
}

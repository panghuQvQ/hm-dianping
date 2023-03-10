package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.FOLLOWS_USERID_KEY;

/**
 * @author admin
 * @version 1.0.0
 * @ClassName FollowServiceImpl.java
 * @Description TODO
 * @createTime 2023年03月06日 15:45:00
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IUserService userService;

    @Override
    @Transactional
    public Result follow(Long followUserId, Boolean isFollow) {
        // 1.获取用户ID
        Long userId = UserHolder.getUser().getId();
        String key = FOLLOWS_USERID_KEY + userId;
        // 2.判断是关注还是取关
        if (isFollow) {
            // 3.关注，新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            if (isSuccess) { // 查看共同关注好友时有用
                // 把关注用户的Id，放入redis的set集合中
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        } else {
            // 4.取关，删除
            boolean isRemoveSuccess = remove(new LambdaUpdateWrapper<Follow>()
                    .eq(Follow::getUserId, userId)
                    .eq(Follow::getFollowUserId, followUserId));
            // 把关注用户的id 从Redis集合中移除
            if (isRemoveSuccess) {
                stringRedisTemplate.opsForSet().remove(key, followUserId);
            }

        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        // 1.获取当前用户id
        Long userId = UserHolder.getUser().getId();
        // 2.查询是否关注
        Integer count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        return Result.ok(count > 0);
    }

    @Override
    public Result followCommons(Long id) {
        // 1.获取当前用户的关注信息
        Long userId = UserHolder.getUser().getId();
        String userKey = FOLLOWS_USERID_KEY + userId; // 当前用户
        String targetKey = FOLLOWS_USERID_KEY + id; // 目标用户

        // 2.求交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(userKey, targetKey);
        if (intersect == null || intersect.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 3.解析id集合
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        // 4.查询用户
        List<UserDTO> collect = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(collect);
    }
}

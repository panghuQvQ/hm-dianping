package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;

/**
 * @author admin
 * @version 1.0.0
 * @ClassName IFollowService.java
 * @Description TODO
 * @createTime 2023年03月06日 15:45:00
 */
public interface IFollowService extends IService<Follow> {
    Result follow(Long followUserId, Boolean isFollow);

    Result isFollow(Long followUserId);

    Result followCommons(Long id);
}

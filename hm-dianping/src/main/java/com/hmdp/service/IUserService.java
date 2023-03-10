package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;

import javax.servlet.http.HttpSession;

/**
 * @title
 * @description
 * @author wzy
 * @updateTime 2023/3/9 15:56
 * @throws
 */
public interface IUserService extends IService<User> {

    Result login(LoginFormDTO loginForm, HttpSession session);

    Result sedCode(String phone, HttpSession session);

    Result sign();

    Result signCount();
}

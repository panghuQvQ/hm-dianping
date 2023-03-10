package com.hmdp.utils;

import com.hmdp.dto.UserDTO;

/**
 * @title ThreadLocal 设置 用户信息
 * @description
 * @author wzy
 * @updateTime 2023/2/24 15:28
 * @throws
 */
public class UserHolder {
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    public static void saveUser(UserDTO user){
        tl.set(user);
    }

    public static UserDTO getUser(){
        return tl.get();
    }

    public static void removeUser(){
        tl.remove();
    }
}

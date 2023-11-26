package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author 周星星
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm);

    Result sign();

    Result signDays();

    Result logout(HttpServletRequest request);

    Result page(Long page, Long pageSize);

    Result saveUser(User user);

    Result deleteUserById(Long id);

    Result updateUserById(User user);

    Result queryUserById(Long userId);

    Result queryBuySeckillUserById(Integer voucherId);
}

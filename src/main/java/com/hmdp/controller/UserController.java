package com.hmdp.controller;


import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.service.impl.UserServiceImpl;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 周星星
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    /**
     * 发送手机验证码
     */
    @PostMapping("/code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        // TODO 发送短信验证码并保存验证码
        return userService.sendCode(phone, session);
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm){
        // TODO 实现登录功能
        return userService.login(loginForm);
    }

    @GetMapping("/test")
    public Result test(){
        System.out.println("=================================================");
        return Result.ok("你成功访问到了");
    }

    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(HttpServletRequest request){
        // TODO 实现登出功能
        return userService.logout(request);
    }

    @GetMapping("/me")
    public Result me(){
        UserDTO user = UserHolder.getUser();
        return Result.ok(user);
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }

    /**
     * 签到
     * @return
     */
    @PostMapping("/sign")
    public Result sign(){
        return userService.sign();
    }

    /**
     * 统计连续签到次数
     * @return
     */
    @GetMapping("/sign/days")
    public Result signDays(){
        return userService.signDays();
    }

    /**
     * 根据id查询用户
     * @param userId
     * @return
     */
    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id") Long userId){
        return userService.queryUserById(userId);
    }


    //===================================自己实现的对用户的增删改查===================================



    /**
     * 新增一个用户
     * @param user
     * @return
     */
    @PutMapping("/saveUser")
    public Result saveUser(@RequestBody User user){
        return userService.saveUser(user);
    }

    /**
     * 根据id删除用户
     * @param userId
     * @return
     */
    @DeleteMapping("/{id}")
    public Result deleteUserById(@PathVariable("id") Long userId){
        return userService.deleteUserById(userId);
    }

    /**
     * 根据id更新用户
     * @param user
     * @return
     */
    @PostMapping("/update")
    public Result updateUserById(@RequestBody User user){
        return userService.updateUserById(user);
    }

    /**
     * 分页查询用户
     * @return
     */
    @GetMapping("/page")
    public Result queryAll(Long page, Long pageSize){
        log.info("=============="+page+pageSize+"==============");
        return userService.page(page,pageSize);
    }

    /**
     * 查询抢到了某张秒杀劵的所有用户
     * @param seckill
     * @return
     */
    @GetMapping("/queryBuySeckillUserById")
    public Result queryBuySeckillUserById(Integer voucherId){
        return userService.queryBuySeckillUserById(voucherId);
    }

}

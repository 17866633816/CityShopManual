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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
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
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 周星星
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private UserMapper userMapper;

    /**
     * 发送手机验证码
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {

        //1.校验手机号格式是否正确
        if(RegexUtils.isPhoneInvalid(phone)){
            //2.不正确，返回错误信息
            return Result.fail("手机号格式错误，请重新输入");
        }

        //3.正确，生成验证码
        String code = RandomUtil.randomNumbers(6);

        //4.将验证码存入Redis，并设置过期时间
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //5.将验证码发送到该手机号
        log.debug("验证码发送成功，验证码为：{}", code);

        //返回ok
        return Result.ok();
    }

    /**
     * 登录功能
     * @param loginForm
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm) {

        //1.校验手机号格式是否正确
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            //2.不正确，返回错误信息
            return Result.fail("手机号格式错误，请重新输入");
        }

        //3.从redis中取出验证码并校验
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)){
            //不一致，返回不一致信息
            return Result.fail("验证码错误");
        }

        //4.一致，通过手机号查询该用户
        User user = query().eq("phone", phone).one();

        //5.判断该用户是否存在
        if(user == null){
            //6.不存在，创建新用户并保存到数据库
            user = addUser(phone);
        }

        //7.保存用户信息到redis中
        //7.1 随机生成token，作为登录令牌
        String token = UUID.randomUUID().toString(true);
        //7.2 将userDTO对象转为HashMap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(), CopyOptions.create()
                .setIgnoreNullValue(true)
                .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        //7.3 保存用户到redis中，并设置过期时间
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        //7.4 设置token有效期
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        //8.将token发送给客户端
        return Result.ok(token);
    }

    /**
     * 向数据库中添加用户并返回
     * @param phone
     * @return
     */
    private User addUser(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(6));
        save(user);
        return user;
    }

    /**
     * 根据id查询用户
     * @param userId
     * @return
     */
    @Override
    public Result queryUserById(Long userId) {
        // 查询详情
        User user = getById(userId);
        if (user == null) {
            return Result.fail("此用户不存在！");
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 返回
        return Result.ok(userDTO);
    }

    /**
     * 签到
     * @return
     */
    @Override
    public Result sign() {
        //1.获取用户id
        Long userId = UserHolder.getUser().getId();
        //2.获取日期
        LocalDateTime now = LocalDateTime.now();
        String date = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        //3.拼接key
        String key = USER_SIGN_KEY + userId + date;
        //4.获取当前天是这个月的第几天
        int dayOfMonth = now.getDayOfMonth();
        //5.存入Redis
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth-1, true);
        return Result.ok();
    }

    /**
     * 统计连续签到次数
     * @return
     */
    @Override
    public Result signDays() {
        //1.获取用户id
        Long userId = UserHolder.getUser().getId();
        //2.获取日期
        LocalDateTime now = LocalDateTime.now();
        String date = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        //3.拼接key
        String key = USER_SIGN_KEY + userId + date;
        //4.获取当前天是这个月的第几天
        int dayOfMonth = now.getDayOfMonth();
        //5.从Redis中取出到今天为止的签到信息
        List<Long> list = stringRedisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if (list == null || list.isEmpty()){
            // 没有任何签到结果
            return Result.ok(0);
        }
        Long signs = list.get(0);
        if(signs == null || signs == 0){
            return Result.ok(0);
        }
        //6.循环遍历十进制数对应的二进制数的最后一位
        int count = 0;
        while (true){
            //6.1 取出最后一位，并判断是否为0
            if ((signs & 1) == 0){
                //是0，结束死循环
                break;
            }
            //不是0，计数器+1，把数字右移一位
            count++;
            signs >>>= 1;
        }
        return Result.ok(count);
    }

    @Override
    public Result logout(HttpServletRequest request) {
        //1.获取请求头中的token
        String token = request.getHeader("authorization");

        //2.从redis中删除此token
        stringRedisTemplate.delete(LOGIN_USER_KEY + token);

        //3.返回成功
        return Result.ok("已退出登录");
    }



    //===================================自己实现的对用户的增删改查===================================



    /**
     * 添加一个用户
     * @param user
     * @return
     */
    @Override
    public Result saveUser(User user) {
        userMapper.saveUser(user);
        return Result.ok("添加成功");
    }

    /**
     * 根据id删除用户
     * @param id
     * @return
     */
    @Override
    public Result deleteUserById(Long id) {
        userMapper.deleteById(id);
        return Result.ok("删除成功");
    }

    /**
     * 根据id更新用户
     * @param user
     * @return
     */
    @Override
    public Result updateUserById(User user) {
        int count = userMapper.updateUserById(user);
        return Result.ok("更新成功");
    }

    /**
     * 分页查询
     * @return
     */
    @Override
    public Result page(Long page, Long pageSize) {
        List<User> users = userMapper.page(page, pageSize);
        return Result.ok(users);
    }

    /**
     * 查询出抢到某张秒杀劵的所有用户
     * @param seckillId
     * @return
     */
    @Override
    public Result queryBuySeckillUserById(Integer voucherId) {
        List<User> users = userMapper.queryBuySeckillUserById(voucherId);
        log.info("查询出抢到某张秒杀劵的所有用户:{}",users);
        return Result.ok(users);
    }

}

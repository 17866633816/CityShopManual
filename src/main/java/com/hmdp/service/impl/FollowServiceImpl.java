package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IUserService userService;

    /**
     * 关注和取关
     * @param id
     * @param isFollow
     * @return
     */
    @Override
    public Result follow(Long id, Boolean isFollow) {
        //1.取出当前用户id
        Long userId = UserHolder.getUser().getId();
        String key = "follow:" + userId;
        //2.判断此次请求是关注还是取关
        if (isFollow) {
            //3.关注
            //3.1 向follow表中插入一条数据
            Follow follow = new Follow();
            follow.setFollowUserId(id);
            follow.setUserId(userId);
            boolean isSuccess = save(follow);
            //3.2 向redis中存入一条数据
            if (isSuccess){
                stringRedisTemplate.opsForSet().add(key, id.toString());
            }
        }else {
            //4.取关
            //4.1 删除follow表的一条数据
            boolean isSuccess = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", id));
            //4.2 向redis中存入一条数据
            if (isSuccess){
                stringRedisTemplate.opsForSet().remove(key, id.toString());
            }
        }
        return Result.ok();
    }

    /**
     * 对当前博客作者是否关注
     * @param followId
     * @return
     */
    @Override
    public Result isFollow(Long followId) {
        //1.取出当前用户id
        Long userId = UserHolder.getUser().getId();
        //2.判断当前用户是否关注此用户
        Long count = query().eq("user_id", userId).eq("follow_user_id", followId).count();
        //3.返回是否关注的结果
        return Result.ok(count > 0);
    }

    /**
     * 查看共同关注
     * @return
     */
    @Override
    public Result followCommons(Long id) {
        //获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        String key = "follow:" + userId;
        //求登录用户与博客作者的交集
        String key1 = "follow:" + id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key1);
        if (intersect == null || intersect.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //3.解析出用户id集合
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        //4.查询用户
        List<UserDTO> users = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        //转为userDTO，返回给前端
        return Result.ok(users);
    }
}

package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IFollowService followService;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            getUserById2Blog(blog);
            isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    /**
     * 根据id查询博文
     *
     * @param id
     * @return
     */
    @Override
    public Result queryBlogById(Long id) {

        //查询博文
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("该博文不存在！！");
        }

        //根据博文中的用户id查询出用户信息，并将用户的某些字段赋给Blog对象
        getUserById2Blog(blog);

        //查询blog是否被当前用户点赞
        isBlogLiked(blog);

        return Result.ok(blog);
    }

    //根据用户是否点赞为Blog对象中的islike字段赋不同值
    private void isBlogLiked(Blog blog) {
        //1.取出当前登录用户的id
        UserDTO user = UserHolder.getUser();

        if (user == null) {
            return;
        }

        //2.判断该用户是否在redis的Set集合中
        Long userId = user.getId();
        String key = BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());

        //3.为isLike字段赋值
        blog.setIsLike(score != null);
    }

    private void getUserById2Blog(Blog blog) {
        //从博文中取出用户id
        Long userId = blog.getUserId();
        //根据用户id查询用户
        User user = userService.getById(userId);
        //将查询出的用户信息的某些字段赋给Blog实体类
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
    }

    /**
     * 点赞功能
     *
     * @param id
     * @return
     */
    @Override
    public Result likeBlog(Long id) {
        //1.取出登录用户id
        Long userId = UserHolder.getUser().getId();

        //2.判断当前登录用户是否已经点赞
        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null) {
            //2.2 未点赞，可以点赞
            //将数据库中点赞数加一
            boolean isSuccess = update().setSql("liked = liked+1").eq("id", id).update();
            //向redis中的SortedSet集合里添加该用户
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            //2.1 存在，已经点过赞
            //将数据库中点赞数减一
            boolean isSuccess = update().setSql("liked = liked-1").eq("id", id).update();
            //将该用户从redis的Set集合中删除
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }

        return Result.ok();
    }

    /**
     * 点赞排行榜
     *
     * @param id
     * @return
     */
    @Override
    public Result queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY + id;
        // 1.查询top5的点赞用户 zrange key 0 4
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 2.解析出其中的用户id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
        // 3.根据用户id查询用户 WHERE id IN ( 5 , 1 ) ORDER BY FIELD(id, 5, 1)
        List<UserDTO> userDTOS = userService.query()
                .in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        // 4.返回
        return Result.ok(userDTOS);
    }

    /**
     * 根据用户id查询博客
     *
     * @param current
     * @param id
     * @return
     */
    @Override
    public Result queryBlogByUserId(Integer current, Long id) {
        // 根据用户查询
        Page<Blog> page = query()
                .eq("user_id", id).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    /**
     * 保存博客并将博客推送到所有粉丝的收件箱
     * @param blog
     * @return
     */
    @Override
    public Result saveBlog(Blog blog) {
        // 1.获取登录用户
        Long userId = UserHolder.getUser().getId();
        blog.setUserId(userId);
        // 2.保存探店博文
        boolean isSuccess = save(blog);
        if (!isSuccess) {
            return Result.fail("新增博客失败！");
        }

        // 3.推送自己的博客至所有的粉丝
        // 3.1 获取自己的粉丝列表
        List<Follow> follows = followService.query().eq("follow_user_id", userId).list();
        // 3.2 将自己的博客id推送至所有粉丝的收件箱
        for (Follow follow : follows) {
            //获取粉丝ID
            Long fanId = follow.getUserId();
            //推送
            String key = FEED_KEY + fanId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }

        // 4.返回博客id给前端
        return Result.ok(blog.getId());
    }

    /**
     * 查询关注的人推送过来的博客
     * @param max
     * @param offset
     * @return
     */
    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //1.获取当前登录用户id
        Long userId = UserHolder.getUser().getId();
        //2.查询收件箱
        String key =FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        //2.1 非空判断
        if (typedTuples == null || typedTuples.isEmpty()){
            return Result.ok();
        }
        //3.解析数据: blogId、minTime（时间戳）、offset
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int os = 1;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            //3.1 获取博客id
            ids.add(Long.valueOf(typedTuple.getValue()));
            //3.2 获取时间戳
            long time = typedTuple.getScore().longValue();
            if(time == minTime){
                os++;
            }else{
                minTime = time;
                os = 1;
            }
        }
        //4.根据博客id去数据库中查询博客信息
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();

        for (Blog blog : blogs) {
            //4.1 查询与blog有关的用户
            getUserById2Blog(blog);
            //4.2 查询blog是否被点赞
            isBlogLiked(blog);
        }

        //6.封装为Java对象并发送给前端
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setMinTime(minTime);
        scrollResult.setOffset(os);
        return Result.ok(scrollResult);
    }
}

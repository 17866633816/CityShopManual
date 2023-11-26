package com.hmdp.mapper;

import com.hmdp.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 周星星
 * @since 2021-12-22
 */
public interface UserMapper extends BaseMapper<User> {

    /**
     * 分页查询
     * @return
     */
    @Select("select * from tb_user limit #{page}, #{pageSize}")
    List<User> page(Long page,Long pageSize);

    /**
     * 插入一个用户
     * @param user
     */
    @Insert("insert into tb_user(phone,nick_name) values(#{phone}, #{nickName})")
    int saveUser(User user);

    /**
     * 根据id删除用户
     * @param id
     * @return
     */
    @Delete("delete from tb_user where id = #{id}")
    int deleteUserById(Long id);

    /**
     * 根据id更新用户
     * @param user
     * @return
     */
    @Update("update tb_user set phone = #{phone},nick_name = #{nickName} where id = #{id}")
    int updateUserById(User user);

    /**
     * 查询出抢到某张秒杀劵的所有用户
     * @param voucherId
     * @return
     */
    List<User> queryBuySeckillUserById(Integer voucherId);
}

package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOPTYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        //从redis中查询商店类型列表
        String shopTypeList = stringRedisTemplate.opsForValue().get(CACHE_SHOPTYPE_KEY);

        //验证redis中是否存在
        if (!StrUtil.isBlank(shopTypeList)){
            //存在，将数据返回给客户端
            List<ShopType> shopTypes = JSONUtil.toList(shopTypeList, ShopType.class);
            return Result.ok(shopTypes);
        }

        //不存在，从数据库中查询
        List<ShopType> typeList = query().orderByAsc("sort").list();

        //查看数据库中是否存在
        if (typeList.isEmpty()){
            //不存在，返回错误信息
            return Result.fail("商户类型在数据库中为空！");
        }

        //存在，将数据保存到redis中一份
        String typeListJSON = JSONUtil.toJsonStr(typeList);
        stringRedisTemplate.opsForValue().set(CACHE_SHOPTYPE_KEY, typeListJSON);

        //返回给客户端一份
        return Result.ok(typeList);
    }
}

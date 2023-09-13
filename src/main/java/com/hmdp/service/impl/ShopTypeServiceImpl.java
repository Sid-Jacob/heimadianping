package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_TTL;

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
    public Result queryList() {
        //1.从Redis中查询
        String key = CACHE_SHOP_TYPE_KEY;
        List<String> list = stringRedisTemplate.opsForList().range(key, 0, -1);
        if(!list.isEmpty()){
            //手动反序列化
            List<ShopType> typeList = new ArrayList<>();
            for (String s : list) {
                ShopType shopType = JSONUtil.toBean(s, ShopType.class);
                typeList.add(shopType);
            }
            log.debug("READ CACHE ShopType");
            return Result.ok(typeList);
        }

        //2.从数据库内查询
        List<ShopType> typeList = query().orderByAsc("sort").list();
        if(typeList.isEmpty()){
            return Result.fail("不存在该分类!");
        }
        //序列化
        for (ShopType shopType : typeList) {
            String s = JSONUtil.toJsonStr(shopType);
            list.add(s);
        }

        //3.存入缓存
        stringRedisTemplate.opsForList().rightPushAll(key,list);
        stringRedisTemplate.expire(key,CACHE_SHOP_TYPE_TTL,TimeUnit.MINUTES);
        log.debug("READ DATABASE ShopType");
        return Result.ok(typeList);
    }
}

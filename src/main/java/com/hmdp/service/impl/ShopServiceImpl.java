package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        // Shop shop = queryWithoutMutex(id);
        // Shop shop = queryWithMutex(id);
        Shop shop = queryWithLogical(id);
        if (shop == null){
            return Result.fail("商铺不存在");
        }
        return Result.ok(shop);
    }

    public Shop queryWithoutMutex(Long id) {
        // 1. retrieve data from redis
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        // 2. if exists, return
        if (!StrUtil.isBlank(shopJson)){
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            log.debug("READ CACHE Shop");
            return shop;
        }
        // 2.5. if caches null, return null
        if (shopJson != null){
            return null;
        }

        // 3. if not exists in cache, look up database
        Shop shop = getById(id);
        if(shop==null){
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "",CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 4. update redis
        // serialize
        String shopStr = JSONUtil.toJsonStr(shop);
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,shopStr,CACHE_SHOP_TTL, TimeUnit.MINUTES);
        log.debug("READ DATABASE Shop");
        return shop;
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        if(shop.getId()==null){
            return Result.fail("店铺id不能为空!");
        }
        // 双写一致
        //1.更新数据库
        updateById(shop);
        //2.删除缓存
        String key = CACHE_SHOP_KEY + shop.getId();
        stringRedisTemplate.delete(key);
        log.debug("更新shop成功");
        return Result.ok();
    }

    /**
     * 获取互斥锁
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放互斥锁
     */
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

    /**互斥锁实现解决缓存击穿**/
    public Shop queryWithMutex(Long id){
        //1.从Redis内查询商品缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        if(StrUtil.isNotBlank(shopJson)){
            //手动反序列化
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //如果上面的判断不对，那么就是我们设置的""(有缓存"",证明数据库内肯定是没有的)或者null(没有缓存)
        //判断命中的是否时空值
        if(shopJson!=null){//
            return null;
        }

        //a.实现缓存重建
        //a.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean hasLock = tryLock(lockKey);
            //a.2 判断是否获取到，获取到:根据id查数据库 获取不到:休眠
            if(!hasLock){
                Thread.sleep(50);
                return queryWithMutex(id);
            }

            //2.不存在就根据id查询数据库
            shop = getById(id);
            //模拟重建的延时
            Thread.sleep(200);
            if(shop==null){
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;
            }
            //3.数据库数据写入Redis
            //手动序列化
            String shopStr = JSONUtil.toJsonStr(shop);
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,shopStr,CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //释放互斥锁
            unLock(lockKey);
        }

        return shop;
    }

    /**缓存重建方法**/
    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        //1.查询店铺信息
        Shop shop = getById(id);
        Thread.sleep(200);
        //2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //3.写入Redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);//开启10个线程

    /**逻辑过期实现解决缓存击穿**/
    public Shop queryWithLogical(Long id){
        //1.从Redis内查询商品缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //2.判断是否存在
        if(StrUtil.isBlank(shopJson)){
            return null;
        }
        //3.命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);

        //4.判断是否过期
        LocalDateTime expireTime = redisData.getExpireTime();
        if(expireTime.isAfter(LocalDateTime.now())){
            //未过期直接返回
            return shop;
        }
        //5.过期的话需要缓存重建
        //5.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean hasLock = tryLock(lockKey);
        //5.2判断是否获取到，获取到:根据id查数据库 获取不到:休眠
        if(hasLock){
            //获取锁成功之后，还要再次检查数据是否过期，如果仍然过期，再开启线程
            //成功就开启独立线程，实现缓存重建, 这里的话用线程池
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    //重建缓存
                    this.saveShop2Redis(id,20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    //释放锁
                    unLock(lockKey);
                }

            });

        }

        return shop;
    }


}

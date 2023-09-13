package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
public class HmDianPingApplicationTests {

    @Resource
    private CacheClient cacheClient;
    @Resource
    ShopServiceImpl shopService;

    // 这里@Test要引入org.junit.jupiter.api.Test包下的，否则会报错
    @Test
    public void testSaveShop() throws InterruptedException {
        // // 使用逻辑过期时间解决缓存击穿时，会提前将热点key写入缓存，此处模拟此过程
        // shopService.saveShop2Redis(1L,10L);

        Shop shop = shopService.getById(1l);
        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + 1l, shop, 10L, TimeUnit.SECONDS);
    }

    @Resource
    private RedisIdWorker redisIdWorker;

    private ExecutorService es = Executors.newFixedThreadPool(500);//线程池

    @Test
    void testRedisId() throws InterruptedException {
        //CountDownLatch大致的原理是将任务切分为N个，让N个子线程执行，并且有一个计数器也设置为N，哪个子线程完成了就N-1
        CountDownLatch latch = new CountDownLatch(300);

        Runnable task =()->{
            for(int i=0;i<100;i++){
                Long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            latch.countDown();
        };
        Long begin = System.currentTimeMillis();
        for(int i=0;i<300;i++){
            es.submit(task);
        }
        latch.await();
        Long end = System.currentTimeMillis();
        System.out.println("time = " + (end - begin));
    }
}

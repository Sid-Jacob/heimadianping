package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Service
public class VoucherOrderService extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Override
    public Result seckillVoucher(Long voucherId) {
        //1.查询优惠卷
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2.判断秒杀是否开始，是否结束
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始!");
        }
        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("秒杀已结束!");
        }
        //3.判断库存是否充足
        if(voucher.getStock()<=0){
            return Result.fail("优惠券库存不足!");
        }

        // //查询订单看看是否存在
        // Long userId = UserHolder.getUser().getId();
        //
        // // 按用户关键字加锁，而不是整个方法
        // // 锁必须加载事务外面，否则不能确保线程安全
        // synchronized (userId.toString().intern()) {//userId一样的持有同一把锁，最好不要放在整个方法上,intern:去字符串常量池找相同字符串
        //     // 处理Springboot里@Transaction不生效的解决方法，代理对象
        //     IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();//获得代理对象
        //     return proxy.createVoucherOrder(voucherId);//默认是this,我们要实现事务需要proxy
        // }//先获取锁，然后再进入方法，确保我的前一个订单会添加上,能先提交事务再释放锁

        Long userId = UserHolder.getUser().getId();
        //创建锁对象
        SimpleRedisLock lock = new SimpleRedisLock("order" + userId,stringRedisTemplate);
        //获取锁
        boolean hasLock = lock.tryLock(1200);
        if(!hasLock){
            //获取锁失败: return fail 或者 retry 这里业务要求是返回失败
            return Result.fail("请勿重复下单!");
        }

        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();//获得代理对象
            return proxy.createVoucherOrder(voucherId);//默认是this,我们要实现事务需要proxy
        } catch (IllegalStateException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }

    }

    @Transactional
    public Result createVoucherOrder(Long voucherId){
        //查询订单看看是否存在
        Long userId = UserHolder.getUser().getId();

        if (query().eq("user_id",userId).eq("voucher_id",voucherId).count()>0) {
            return Result.fail("用户已经购买过一次!");
        }

        //4.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock -1")
                .eq("voucher_id", voucherId).gt("stock",0)//where id = ? and stock >0 添加了乐观锁
                .update();
        //5.创建订单
        if(!success){
            return Result.fail("优惠券库存不足!");
        }

        //6.返回订单id
        VoucherOrder voucherOrder = new VoucherOrder();
        //6.1订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //6.2用户id
        //Long userId = UserHolder.getUser().getId();
        voucherOrder.setUserId(userId);
        //6.3代金券id
        voucherOrder.setVoucherId(voucherId);

        //7.订单写入数据库
        save(voucherOrder);
        //8.返回订单Id
        return Result.ok(orderId);
    }
}

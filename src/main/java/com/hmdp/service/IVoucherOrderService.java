package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;


public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result createOrder(Long voucherId);

    void createVoucherOrder(VoucherOrder voucherOrder);

    Result createVoucherOrder(Long voucherId);

    Result seckillVoucher(Long voucherId);


}

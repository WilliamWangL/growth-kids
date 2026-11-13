package com.growthkids.api.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.growthkids.api.payment.entity.Payment;
import org.apache.ibatis.annotations.Mapper;

/** payments 读写（TASK-016 订阅 / TASK-017 捐赠共用，§19）。 */
@Mapper
public interface PaymentMapper extends BaseMapper<Payment> {}


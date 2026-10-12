package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 *  Flash-sale voucher table, one-to-one with voucher
 * </p>
 *
 * @author 虎哥
 * @since 2022-01-04
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_seckill_voucher")
public class SeckillVoucher implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Associated voucher ID
     */
    @TableId(value = "voucher_id", type = IdType.INPUT)
    private Long voucherId;

    /**
     * Stock
     */
    private Integer stock;

    /**
     * Created at
     */
    private LocalDateTime createTime;

    /**
     * Valid from
     */
    private LocalDateTime beginTime;

    /**
     * Valid until
     */
    private LocalDateTime endTime;

    /**
     * Updated at
     */
    private LocalDateTime updateTime;


}

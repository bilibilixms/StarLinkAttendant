package com.starlink.cashier.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.starlink.cashier.entity.ProductInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 收银模块专用 — 查询商品信息（商品名称、零售价、会员价）。
 */
@Mapper
public interface ProductInfoMapper extends BaseMapper<ProductInfo> {
}

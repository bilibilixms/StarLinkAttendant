package com.starlink.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.starlink.product.dto.resp.HotProductResponse;
import com.starlink.product.dto.resp.MemberProductDetailResponse;
import com.starlink.product.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 小程序「热门商品」：取真实 product 表中按累计销量倒序的前 N 个上架商品。
     * 销量来自已支付订单明细（item_type=1 商品）的 quantity 汇总。
     */
    @Select("SELECT p.id, p.product_name AS name, p.category_id AS categoryId, p.unit, " +
            "p.product_type AS type, p.retail_price AS price, p.member_price AS memberPrice, " +
            "p.image_url AS cover, p.is_active AS status, " +
            "COALESCE(SUM(oi.quantity), 0) AS sales " +
            "FROM product p " +
            "LEFT JOIN order_item oi ON oi.product_id = p.id " +
            "  AND oi.item_type = 1 AND oi.deleted_at IS NULL " +
            "LEFT JOIN orders o ON o.id = oi.order_id " +
            "  AND o.deleted_at IS NULL AND o.paid_at IS NOT NULL " +
            "WHERE p.deleted_at IS NULL AND p.is_active = 1 " +
            "GROUP BY p.id, p.product_name, p.category_id, p.unit, p.product_type, " +
            "p.retail_price, p.member_price, p.image_url, p.is_active " +
            "ORDER BY sales DESC, p.id ASC " +
            "LIMIT #{limit}")
    List<HotProductResponse> selectHotProducts(@Param("limit") int limit);

    /**
     * 小程序自助点餐「商品列表」：取真实 product 表全部上架商品（可含销量）。
     * categoryId 为可选分类过滤，0/null 表示全部。
     */
    @Select("<script>SELECT p.id, p.product_name AS name, p.product_type AS type, " +
            "p.category_id AS categoryId, p.unit, p.retail_price AS price, " +
            "p.member_price AS memberPrice, p.image_url AS cover, p.is_active AS status, " +
            "COALESCE(SUM(oi.quantity), 0) AS sales " +
            "FROM product p " +
            "LEFT JOIN order_item oi ON oi.product_id = p.id " +
            "  AND oi.item_type = 1 AND oi.deleted_at IS NULL " +
            "LEFT JOIN orders o ON o.id = oi.order_id " +
            "  AND o.deleted_at IS NULL AND o.paid_at IS NOT NULL " +
            "WHERE p.deleted_at IS NULL AND p.is_active = 1 " +
            "<if test='categoryId != null and categoryId gt 0'> AND p.category_id = #{categoryId} </if>" +
            "GROUP BY p.id, p.product_name, p.product_type, p.category_id, p.unit, " +
            "p.retail_price, p.member_price, p.image_url, p.is_active " +
            "ORDER BY p.id ASC" +
            "</script>")
    List<MemberProductDetailResponse> selectMemberProducts(@Param("categoryId") Long categoryId);

    /**
     * 小程序热门商品「详情」：按 id 取上架商品及累计销量。
     */
    @Select("SELECT p.id, p.product_name AS name, p.product_type AS type, " +
            "p.category_id AS categoryId, p.unit, p.retail_price AS price, " +
            "p.member_price AS memberPrice, p.image_url AS cover, p.is_active AS status, " +
            "COALESCE(SUM(oi.quantity), 0) AS sales " +
            "FROM product p " +
            "LEFT JOIN order_item oi ON oi.product_id = p.id " +
            "  AND oi.item_type = 1 AND oi.deleted_at IS NULL " +
            "LEFT JOIN orders o ON o.id = oi.order_id " +
            "  AND o.deleted_at IS NULL AND o.paid_at IS NOT NULL " +
            "WHERE p.id = #{id} AND p.deleted_at IS NULL AND p.is_active = 1 " +
            "GROUP BY p.id, p.product_name, p.product_type, p.category_id, p.unit, " +
            "p.retail_price, p.member_price, p.image_url, p.is_active")
    MemberProductDetailResponse selectMemberProductDetail(@Param("id") Long id);
}

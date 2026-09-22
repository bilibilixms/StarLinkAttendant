package com.starlink.product.service;

import com.starlink.common.util.PageQuery;
import com.starlink.common.util.PageResult;
import com.starlink.product.dto.resp.HotProductResponse;
import com.starlink.product.dto.resp.MemberCategoryResponse;
import com.starlink.product.dto.resp.MemberProductDetailResponse;
import com.starlink.product.entity.Product;

import java.util.List;

public interface ProductService {

    /**
     * 小程序「热门商品」：按累计销量倒序取前 limit 个上架商品。
     */
    List<HotProductResponse> listHotProducts(int limit);

    /**
     * 小程序自助点餐「商品列表」：取真实商品表全部上架商品。
     */
    List<MemberProductDetailResponse> listMemberProducts(Long categoryId);

    /**
     * 小程序自助点餐「商品分类」。
     */
    List<MemberCategoryResponse> listMemberCategories();

    /**
     * 小程序热门商品「详情」：按 id 取上架商品详情。
     */
    MemberProductDetailResponse getMemberProductDetail(Long id);

    PageResult<Product> getProductPage(PageQuery pageQuery, String productName, Long categoryId, Integer productType, Integer isActive);

    Product getProductById(Long id);

    Product createProduct(Product product);

    Product updateProduct(Long id, Product product);

    void deleteProduct(Long id);

    void updateProductStatus(Long id, Integer isActive);
}

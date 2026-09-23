package com.starlink.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.starlink.product.dto.resp.HotProductResponse;
import com.starlink.product.dto.resp.MemberCategoryResponse;
import com.starlink.product.dto.resp.MemberProductDetailResponse;
import com.starlink.product.entity.Product;
import com.starlink.product.entity.ProductCategory;
import com.starlink.product.mapper.ProductCategoryMapper;
import com.starlink.product.mapper.ProductMapper;
import com.starlink.product.service.ProductService;
import com.starlink.common.exception.BusinessException;
import com.starlink.common.result.ErrorCode;
import com.starlink.common.util.PageQuery;
import com.starlink.common.util.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Collections;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private final ProductCategoryMapper productCategoryMapper;

    /** 商品表无库存字段，详情/下单 mock 阶段以充足值占位，避免被误判售罄 */
    private static final int DEFAULT_STOCK = 999;

    @Override
    public List<HotProductResponse> listHotProducts(int limit) {
        List<HotProductResponse> list = productMapper.selectHotProducts(limit);
        // 数据库 product_type: 1=食品,2=饮料,3=虚拟商品,4=日用品,5=网游点卡
        // 前端 ProductType: 1=实物, 2=虚拟 → 食品/饮料/日用品→1, 虚拟/点卡→2
        list.forEach(item -> item.setType(mapTypeToFrontend(item.getType())));
        return list;
    }

    @Override
    public List<MemberProductDetailResponse> listMemberProducts(Long categoryId) {
        return productMapper.selectMemberProducts(categoryId).stream()
                .map(this::enrichMemberProduct)
                .collect(Collectors.toList());
    }

    @Override
    public List<MemberCategoryResponse> listMemberCategories() {
        return productCategoryMapper.selectList(
                        new LambdaQueryWrapper<ProductCategory>()
                                .eq(ProductCategory::getIsActive, 1)
                                .orderByAsc(ProductCategory::getSortOrder))
                .stream()
                .map(c -> {
                    MemberCategoryResponse r = new MemberCategoryResponse();
                    r.setId(c.getId());
                    r.setName(c.getCategoryName());
                    r.setSort(c.getSortOrder());
                    return r;
                })
                .collect(Collectors.toList());
    }

    @Override
    public MemberProductDetailResponse getMemberProductDetail(Long id) {
        MemberProductDetailResponse detail = productMapper.selectMemberProductDetail(id);
        if (detail == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return enrichMemberProduct(detail);
    }

    /** 补齐列表/详情共用的展示字段（类型映射、库存占位、单图、兜底描述/规格/标签） */
    private MemberProductDetailResponse enrichMemberProduct(MemberProductDetailResponse detail) {
        // 类型映射同 listHotProducts
        detail.setType(mapTypeToFrontend(detail.getType()));
        // 表无库存字段 → 占位充足值，保证「下单 mock」流程不误判售罄
        detail.setStock(DEFAULT_STOCK);
        // 无多图：单图填 cover
        detail.setImages(detail.getCover() == null ? Collections.emptyList() : Collections.singletonList(detail.getCover()));
        // 无描述/规格/标签字段：给可读兜底
        detail.setSpec(null);
        detail.setTags(Collections.emptyList());
        detail.setDescription(detail.getName() + "，下单后由吧台配送至您的机位，也可到吧台自取。");
        return detail;
    }

    /** 数据库 product_type → 前端 ProductType：3(虚拟)/5(点卡)→2虚拟，其余→1实物 */
    private Integer mapTypeToFrontend(Integer dbType) {
        if (dbType == null) return 1;
        return (dbType == 3 || dbType == 5) ? 2 : 1;
    }

    @Override
    public PageResult<Product> getProductPage(PageQuery pageQuery, String productName, Long categoryId, Integer productType, Integer isActive) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(productName)) {
            wrapper.like(Product::getProductName, productName);
        }
        if (categoryId != null) {
            wrapper.eq(Product::getCategoryId, categoryId);
        }
        if (productType != null) {
            wrapper.eq(Product::getProductType, productType);
        }
        if (isActive != null) {
            wrapper.eq(Product::getIsActive, isActive);
        }

        wrapper.orderByDesc(Product::getCreatedAt);

        Page<Product> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        IPage<Product> mpPage = productMapper.selectPage(page, wrapper);
        return PageResult.from(mpPage);
    }

    @Override
    public Product getProductById(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return product;
    }

    @Override
    @Transactional
    public Product createProduct(Product product) {
        if (StringUtils.hasText(product.getProductCode())) {
            LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Product::getProductCode, product.getProductCode());
            if (productMapper.selectCount(wrapper) > 0) {
                throw new BusinessException(ErrorCode.PRODUCT_CODE_DUPLICATE);
            }
        }

        product.setIsActive(product.getIsActive() == null ? 1 : product.getIsActive());
        product.setIsVipOnly(product.getIsVipOnly() == null ? 0 : product.getIsVipOnly());
        product.setUnit(product.getUnit() == null ? "份" : product.getUnit());

        productMapper.insert(product);
        log.info("创建商品: id={}, name={}", product.getId(), product.getProductName());
        return product;
    }

    @Override
    @Transactional
    public Product updateProduct(Long id, Product product) {
        Product existing = productMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        if (StringUtils.hasText(product.getProductCode()) && !product.getProductCode().equals(existing.getProductCode())) {
            LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Product::getProductCode, product.getProductCode());
            wrapper.ne(Product::getId, id);
            if (productMapper.selectCount(wrapper) > 0) {
                throw new BusinessException(ErrorCode.PRODUCT_CODE_DUPLICATE);
            }
        }

        product.setId(id);
        productMapper.updateById(product);
        log.info("更新商品: id={}", id);
        return productMapper.selectById(id);
    }

    @Override
    @Transactional
    public void deleteProduct(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        productMapper.deleteById(id);
        log.info("删除商品: id={}, name={}", id, product.getProductName());
    }

    @Override
    @Transactional
    public void updateProductStatus(Long id, Integer isActive) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        product.setIsActive(isActive);
        productMapper.updateById(product);
        log.info("更新商品状态: id={}, status={}", id, isActive);
    }
}

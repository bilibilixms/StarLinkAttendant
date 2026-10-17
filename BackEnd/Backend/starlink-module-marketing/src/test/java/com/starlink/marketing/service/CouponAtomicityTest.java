package com.starlink.marketing.service;

import com.starlink.common.exception.BusinessException;
import com.starlink.marketing.dto.request.CouponIssueRequest;
import com.starlink.marketing.entity.Coupon;
import com.starlink.marketing.entity.CouponTemplate;
import com.starlink.marketing.mapper.CampaignMapper;
import com.starlink.marketing.mapper.CouponMapper;
import com.starlink.marketing.mapper.CouponTemplateMapper;
import com.starlink.marketing.service.impl.CouponServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CouponAtomicityTest {
    @Mock private CouponTemplateMapper templates;
    @Mock private CouponMapper coupons;
    @Mock private CampaignMapper campaigns;
    @InjectMocks private CouponServiceImpl service;

    private CouponTemplate template;

    @BeforeEach
    void setup() {
        template = new CouponTemplate();
        template.setId(1L);
        template.setIsActive(1);
        template.setTotalQuantity(5);
    }

    @Test
    void issueChecksQuotaWhileTemplateRowIsLocked() {
        when(templates.selectByIdForUpdate(1L)).thenReturn(template);
        when(coupons.selectCount(any())).thenReturn(4L);
        CouponIssueRequest request = new CouponIssueRequest();
        request.setTemplateId(1L);
        request.setMemberId(2L);
        request.setQuantity(2);

        assertThrows(BusinessException.class, () -> service.issueCoupon(request));
        verify(coupons, never()).insert(any());
    }

    @Test
    void issueRejectsWrongCampaignAndInvalidQuantity() {
        when(templates.selectByIdForUpdate(1L)).thenReturn(template);
        CouponIssueRequest request = new CouponIssueRequest();
        request.setTemplateId(1L);
        request.setMemberId(2L);
        request.setQuantity(0);
        assertThrows(BusinessException.class, () -> service.issueCoupon(request));

        request.setQuantity(1);
        request.setCampaignId(9L);
        assertThrows(BusinessException.class, () -> service.issueCoupon(request));
        verify(coupons, never()).insert(any());
    }

    @Test
    void redeemRequiresOrderOwnedByCouponMember() {
        Coupon coupon = new Coupon();
        coupon.setId(1L);
        coupon.setMemberId(2L);
        coupon.setStatus(0);
        when(coupons.selectByIdForUpdate(1L)).thenReturn(coupon);
        when(coupons.selectOrderMember(3L)).thenReturn(Map.of("memberId", 4L));

        assertThrows(BusinessException.class, () -> service.redeemCoupon(1L, 3L));
        verify(coupons, never()).updateById(any());
    }
}

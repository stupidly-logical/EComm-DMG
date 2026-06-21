package com.ecomm.oms.pricing;

import com.ecomm.oms.common.error.BusinessRuleException;
import com.ecomm.oms.common.error.ConflictException;
import com.ecomm.oms.common.error.NotFoundException;
import com.ecomm.oms.pricing.dto.CouponRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class CouponService {

    private final CouponRepository couponRepository;

    public CouponService(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    @Transactional(readOnly = true)
    public List<Coupon> list() {
        return couponRepository.findAll(Sort.by("code"));
    }

    @Transactional(readOnly = true)
    public Coupon getByCode(String code) {
        return couponRepository.findByCode(code.trim())
                .orElseThrow(() -> NotFoundException.of("Coupon", code));
    }

    @Transactional
    public Coupon create(CouponRequest request) {
        if (couponRepository.existsByCode(request.code())) {
            throw new ConflictException("Coupon code already exists", "COUPON_CODE_TAKEN");
        }
        return couponRepository.save(new Coupon(
                request.code().trim(),
                request.type(),
                request.value(),
                request.minOrderAmount(),
                request.validFrom(),
                request.validTo(),
                request.maxRedemptions(),
                request.activeOrDefault()));
    }

    /**
     * Validate a coupon for an order subtotal, throwing if it cannot apply. Used by both the
     * apply-coupon endpoint and checkout so the rules cannot diverge.
     *
     * @throws NotFoundException     unknown code
     * @throws BusinessRuleException inactive/expired/cap-reached or below minimum order
     */
    @Transactional(readOnly = true)
    public Coupon validateForOrder(String code, BigDecimal subtotal) {
        Coupon coupon = getByCode(code);
        if (!coupon.isRedeemable(Instant.now())) {
            throw new BusinessRuleException("Coupon is not currently redeemable", "COUPON_NOT_REDEEMABLE");
        }
        if (!coupon.meetsMinimum(subtotal)) {
            throw new BusinessRuleException(
                    "Order subtotal is below the coupon minimum of " + coupon.getMinOrderAmount(),
                    "COUPON_MIN_NOT_MET");
        }
        return coupon;
    }
}

package vn.edu.hcmute.uteexpress.service;

import vn.edu.hcmute.uteexpress.entity.PromoCode;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Ap ma giam cuoc phia nguoi gui hang - TV1 phu trach (viec 5).
 * Man hinh tao/sua ma la phan cua TV3 (Manager - quan ly khuyen mai).
 */
public interface PromoCodeService {

    /**
     * Kiem tra ma co dung duoc cho don co cuoc la orderFee hay khong.
     * Nem IllegalStateException kem ly do cu the (sai ma, het han, het luot, chua du cuoc toi thieu)
     * de nguoi dung biet duong sua, thay vi chi bao chung chung "ma khong hop le".
     */
    PromoCode requireUsableCode(String code, BigDecimal orderFee);

    /**
     * So tien duoc giam khi ap ma vao mot don co cuoc orderFee.
     * Khong bao gio vuot qua chinh so cuoc - tranh ra cuoc am.
     */
    BigDecimal calculateDiscount(PromoCode promoCode, BigDecimal orderFee);

    /** Tang so luot da dung sau khi don duoc tao thanh cong. */
    void markUsed(PromoCode promoCode);

    /** Tra ma neu ton tai, khong nem loi - dung cho cho chi can hien thi. */
    Optional<PromoCode> findByCode(String code);
}

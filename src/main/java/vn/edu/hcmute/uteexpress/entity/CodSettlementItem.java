package vn.edu.hcmute.uteexpress.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** Một vận đơn trong phiếu nộp tiền; order_id duy nhất nên một khoản COD không bị nộp hai lần. */
@Entity
@Table(name = "cod_settlement_item", indexes = @Index(name = "idx_cod_item_settlement", columnList = "settlement_id"))
public class CodSettlementItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settlement_id", nullable = false)
    private CodSettlement settlement;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;
    @Column(nullable = false, precision = 12, scale = 0)
    private BigDecimal amount;

    protected CodSettlementItem() { }
    public CodSettlementItem(CodSettlement settlement, Order order, BigDecimal amount) {
        this.settlement = settlement; this.order = order; this.amount = amount;
    }
    public Long getId() { return id; }
    public CodSettlement getSettlement() { return settlement; }
    public Order getOrder() { return order; }
    public BigDecimal getAmount() { return amount; }
}

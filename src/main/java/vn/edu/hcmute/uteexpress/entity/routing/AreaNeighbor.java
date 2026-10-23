package vn.edu.hcmute.uteexpress.entity.routing;

import jakarta.persistence.*;
import org.hibernate.annotations.Check;

/** Hai khu vực giao nhận lân cận cùng chi nhánh; không sử dụng địa chỉ nhà của shipper. */
@Entity
@Table(name = "area_neighbor", indexes = @Index(name = "idx_neighbor_reverse", columnList = "neighbor_code"))
@Check(constraints = "area_code <> neighbor_code")
public class AreaNeighbor {
    @EmbeddedId private AreaNeighborId id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_code", referencedColumnName = "area_code", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_neighbor_area")) private ProvinceArea area;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "neighbor_code", referencedColumnName = "area_code", insertable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_neighbor_other")) private ProvinceArea neighbor;
    protected AreaNeighbor() { }
    public AreaNeighbor(ProvinceArea area, ProvinceArea neighbor) {
        this.area = area; this.neighbor = neighbor;
        id = new AreaNeighborId(area.getAreaCode(), neighbor.getAreaCode());
    }
    public AreaNeighborId getId() { return id; }
    public ProvinceArea getArea() { return area; }
    public ProvinceArea getNeighbor() { return neighbor; }
}

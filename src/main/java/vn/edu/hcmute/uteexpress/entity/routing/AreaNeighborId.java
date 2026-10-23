package vn.edu.hcmute.uteexpress.entity.routing;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;
import org.hibernate.annotations.Nationalized;

/** Khóa ghép của một chiều quan hệ lân cận; dữ liệu luôn nạp cả chiều ngược lại. */
@Embeddable
public class AreaNeighborId implements Serializable {
    @Nationalized @Column(name = "area_code", nullable = false, length = 40) private String areaCode;
    @Nationalized @Column(name = "neighbor_code", nullable = false, length = 40) private String neighborCode;
    protected AreaNeighborId() { }
    public AreaNeighborId(String areaCode, String neighborCode) { this.areaCode = areaCode; this.neighborCode = neighborCode; }
    public String getAreaCode() { return areaCode; }
    public String getNeighborCode() { return neighborCode; }
    @Override public boolean equals(Object other) {
        return this == other || other instanceof AreaNeighborId key
                && Objects.equals(areaCode, key.areaCode) && Objects.equals(neighborCode, key.neighborCode);
    }
    @Override public int hashCode() { return Objects.hash(areaCode, neighborCode); }
}

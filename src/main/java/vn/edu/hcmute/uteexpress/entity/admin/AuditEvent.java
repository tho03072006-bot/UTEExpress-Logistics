package vn.edu.hcmute.uteexpress.entity.admin;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity @Table(name = "audit_event", indexes = @Index(name = "idx_audit_event_created", columnList = "created_at,id"))
public class AuditEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50, updatable = false)
    private String actor;

    @Column(nullable = false, length = 50, updatable = false)
    private String action;

    @Column(nullable = false, length = 80, updatable = false)
    private String target;

    @Column(nullable = false, length = 500, updatable = false)
    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

}

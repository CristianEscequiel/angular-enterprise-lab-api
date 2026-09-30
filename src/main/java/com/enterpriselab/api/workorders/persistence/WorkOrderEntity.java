package com.enterpriselab.api.workorders.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Mapea la tabla {@code work_orders} con columnas planas y los ids sueltos: no hay
 * relaciones hacia {@code machines}, {@code parts} ni {@code users} (la orden
 * conserva su historia, REQ-30). {@code type}, {@code priority} y {@code status}
 * se guardan como el mismo string kebab-case que los enums de dominio; el mapeo lo
 * hace {@link WorkOrderMapper}. El único mutador es {@link #updateDetails}: las
 * transiciones de estado son de la spec 04.
 */
@Entity
@Table(name = "work_orders")
class WorkOrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(name = "machine_id", nullable = false, updatable = false)
    private Long machineId;

    @Column(name = "part_id", updatable = false)
    private Long partId;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String breadcrumb;

    @Column(name = "machine_comment", nullable = false, updatable = false, length = 200)
    private String machineComment;

    @Column(nullable = false, updatable = false, length = 20)
    private String type;

    @Column(nullable = false, length = 10)
    private String priority;

    @Column(nullable = false, updatable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "taken_by_id", updatable = false)
    private Long takenById;

    @Column(name = "taken_by_name", updatable = false, length = 100)
    private String takenByName;

    @Column(name = "taken_at", updatable = false)
    private Instant takenAt;

    @Column(name = "closing_comment", updatable = false, length = 500)
    private String closingComment;

    @Column(name = "closing_author_id", updatable = false)
    private Long closingAuthorId;

    @Column(name = "closing_author_name", updatable = false, length = 100)
    private String closingAuthorName;

    @Column(name = "closed_at", updatable = false)
    private Instant closedAt;

    protected WorkOrderEntity() {
        // JPA
    }

    WorkOrderEntity(String title, String description, Long machineId, Long partId, String breadcrumb,
            String machineComment, String type, String priority, String status, Instant createdAt,
            Long takenById, String takenByName, Instant takenAt,
            String closingComment, Long closingAuthorId, String closingAuthorName, Instant closedAt) {
        this.title = title;
        this.description = description;
        this.machineId = machineId;
        this.partId = partId;
        this.breadcrumb = breadcrumb;
        this.machineComment = machineComment;
        this.type = type;
        this.priority = priority;
        this.status = status;
        this.createdAt = createdAt;
        this.takenById = takenById;
        this.takenByName = takenByName;
        this.takenAt = takenAt;
        this.closingComment = closingComment;
        this.closingAuthorId = closingAuthorId;
        this.closingAuthorName = closingAuthorName;
        this.closedAt = closedAt;
    }

    /** La edición cambia estos tres campos; tipo, máquina, parte, estado, dueño y cierre no se tocan (REQ-33 a REQ-36). */
    void updateDetails(String title, String description, String priority) {
        this.title = title;
        this.description = description;
        this.priority = priority;
    }

    Long getId() {
        return id;
    }

    String getTitle() {
        return title;
    }

    String getDescription() {
        return description;
    }

    Long getMachineId() {
        return machineId;
    }

    Long getPartId() {
        return partId;
    }

    String getBreadcrumb() {
        return breadcrumb;
    }

    String getMachineComment() {
        return machineComment;
    }

    String getType() {
        return type;
    }

    String getPriority() {
        return priority;
    }

    String getStatus() {
        return status;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Long getTakenById() {
        return takenById;
    }

    String getTakenByName() {
        return takenByName;
    }

    Instant getTakenAt() {
        return takenAt;
    }

    String getClosingComment() {
        return closingComment;
    }

    Long getClosingAuthorId() {
        return closingAuthorId;
    }

    String getClosingAuthorName() {
        return closingAuthorName;
    }

    Instant getClosedAt() {
        return closedAt;
    }
}

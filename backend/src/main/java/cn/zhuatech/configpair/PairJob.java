// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.configpair;

import jakarta.persistence.*;
import java.time.Instant;

/** pair_job持久化业务事实。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "pair_job")
public class PairJob {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "reference", nullable = false, length = 60)
  public String reference;

  @Column(name = "name", nullable = false, length = 160)
  public String name;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "category", nullable = false, length = 60)
  public String category;

  @Column(name = "system_name", nullable = false, length = 160)
  public String systemName;

  @Column(name = "build_label", nullable = false, length = 160)
  public String buildLabel;

  @Column(name = "environment", nullable = false, length = 1000)
  public String environment;

  @Column(name = "instructions", nullable = false, length = 1000)
  public String instructions;

  @Column(name = "reviewer_id", nullable = false)
  public Long reviewerId;

  @Column(name = "operator_id", nullable = false)
  public Long operatorId;

  @Column(name = "created_by", nullable = false)
  public Long createdBy;

  @Column(name = "status", nullable = false, length = 30)
  public String status;

  @Column(name = "active_revision_id", nullable = true)
  public Long activeRevisionId;

  @Column(name = "approved_hash", nullable = true, length = 64)
  public String approvedHash;

  @Column(name = "report_hash", nullable = true, length = 64)
  public String reportHash;

  @Column(name = "closed_hash", nullable = true, length = 64)
  public String closedHash;

  @Column(name = "requested_outcome", nullable = true, length = 30)
  public String requestedOutcome;

  @Column(name = "outcome", nullable = true, length = 30)
  public String outcome;

  @Column(name = "acknowledged_at", nullable = true)
  public Instant acknowledgedAt;

  @Column(name = "approved_at", nullable = true)
  public Instant approvedAt;

  @Column(name = "closed_at", nullable = true)
  public Instant closedAt;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "version", nullable = false)
  public long version;
}

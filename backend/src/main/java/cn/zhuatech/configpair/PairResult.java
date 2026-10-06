// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.configpair;

import jakarta.persistence.*;
import java.time.Instant;

/** pair_result持久化业务事实。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "pair_result")
public class PairResult {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "job_id", nullable = false)
  public Long jobId;

  @Column(name = "revision_id", nullable = false)
  public Long revisionId;

  @Column(name = "case_number", nullable = false)
  public Integer caseNumber;

  @Column(name = "status", nullable = false, length = 30)
  public String status;

  @Column(name = "note", nullable = false, length = 1000)
  public String note;

  @Column(name = "evidence", nullable = false, length = 1000)
  public String evidence;

  @Column(name = "actor_id", nullable = false)
  public Long actorId;

  @Column(name = "recorded_at", nullable = false)
  public Instant recordedAt;
}

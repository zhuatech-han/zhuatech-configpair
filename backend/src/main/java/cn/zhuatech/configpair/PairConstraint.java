// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.configpair;

import jakarta.persistence.*;

/** pair_constraint持久化业务事实。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "pair_constraint")
public class PairConstraint {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "job_id", nullable = false)
  public Long jobId;

  @Column(name = "left_factor_id", nullable = false)
  public Long leftFactorId;

  @Column(name = "left_value", nullable = false, length = 60)
  public String leftValue;

  @Column(name = "right_factor_id", nullable = false)
  public Long rightFactorId;

  @Column(name = "right_value", nullable = false, length = 60)
  public String rightValue;

  @Column(name = "reason", nullable = false, length = 1000)
  public String reason;
}

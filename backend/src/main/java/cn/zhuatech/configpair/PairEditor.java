// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.configpair;

import jakarta.persistence.*;

/** pair_editor持久化业务事实。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "pair_editor")
public class PairEditor {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "job_id", nullable = false)
  public Long jobId;

  @Column(name = "actor_id", nullable = false)
  public Long actorId;
}

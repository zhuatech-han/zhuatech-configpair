// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.configpair;

import jakarta.persistence.*;

/** pair_factor持久化业务事实。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "pair_factor")
public class PairFactor {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "job_id", nullable = false)
  public Long jobId;

  @Column(name = "code", nullable = false, length = 30)
  public String code;

  @Column(name = "name", nullable = false, length = 160)
  public String name;

  @com.fasterxml.jackson.annotation.JsonIgnore
  @Column(name = "values_json", nullable = false, columnDefinition = "longtext")
  public String valuesJson;
}

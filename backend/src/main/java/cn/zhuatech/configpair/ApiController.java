// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.configpair;

import java.util.*;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** 参数模型与覆盖业务接口，所有写入经版本及实时权限检查。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class ApiController {
  final PairService pair;
  final AdminService admin;
  final AccessService access;
  final Store db;

  public ApiController(PairService pair, AdminService admin, AccessService access, Store db) {
    this.pair = pair;
    this.admin = admin;
    this.access = access;
    this.db = db;
  }

  /** 授权表单目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/options")
  public Object options() {
    return pair.options();
  }

  /** 计划列表。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/jobs")
  public Object list(
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "newest") String sort) {
    return pair.list(search, status, page, size, sort);
  }

  /** 计划详情。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/jobs/{id}")
  public Object detail(@PathVariable Long id) {
    return pair.detail(id);
  }

  /** 不可变生成历史。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/revisions/{id}")
  public Object revision(@PathVariable Long id) {
    return pair.revision(id);
  }

  /** 新建计划。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/jobs")
  public Object create(@RequestBody PairService.JobInput v) {
    return pair.saveJob(null, v);
  }

  /** 编辑草稿计划。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/jobs/{id}")
  public Object edit(@PathVariable Long id, @RequestBody PairService.JobInput v) {
    return pair.saveJob(id, v);
  }

  /** 新建参数。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/factors")
  public Object factor(@RequestBody PairService.FactorInput v) {
    return pair.saveFactor(null, v);
  }

  /** 编辑参数。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/factors/{id}")
  public Object factorEdit(@PathVariable Long id, @RequestBody PairService.FactorInput v) {
    return pair.saveFactor(id, v);
  }

  /** 删除草稿参数。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/factors/{id}/delete")
  public Object factorDelete(@PathVariable Long id, @RequestBody PairService.Command v) {
    return pair.deleteFactor(id, v);
  }

  /** 新建禁配。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/constraints")
  public Object constraint(@RequestBody PairService.ConstraintInput v) {
    return pair.saveConstraint(null, v);
  }

  /** 编辑禁配。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/constraints/{id}")
  public Object constraintEdit(@PathVariable Long id, @RequestBody PairService.ConstraintInput v) {
    return pair.saveConstraint(id, v);
  }

  /** 删除草稿禁配。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/constraints/{id}/delete")
  public Object constraintDelete(@PathVariable Long id, @RequestBody PairService.Command v) {
    return pair.deleteConstraint(id, v);
  }

  /** 登记实际结果。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/results")
  public Object result(@RequestBody PairService.ResultInput v) {
    return pair.saveResult(v);
  }

  /** 版本化状态命令。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/jobs/{id}/commands/{action}")
  public Object command(
      @PathVariable Long id, @PathVariable String action, @RequestBody PairService.Command v) {
    return pair.command(id, action, v);
  }

  /** 范围内实际统计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/dashboard")
  public Object dashboard() {
    return pair.dashboard();
  }

  /** 完整计划报告。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/jobs/{id}/report.json")
  public ResponseEntity<Object> report(@PathVariable Long id) {
    access.require("export");
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=configpair-" + id + ".json")
        .body(pair.detail(id));
  }

  /** 用例和人工结果CSV。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/jobs/{id}/cases.csv")
  public ResponseEntity<String> csv(@PathVariable Long id) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=configpair-" + id + "-cases.csv")
        .body(pair.csv(id));
  }

  /** 审计目录限制到授权部门及本人范围。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/audit")
  @Transactional(readOnly = true)
  public Object audit() {
    access.require("audit");
    return db
        .jpql(
            AuditEvent.class,
            "from AuditEvent where (?1=true or departmentId=?2) and (?3=false or actor=?4) order by id desc")
        .setParameter(1, access.role().scope.equals("ALL"))
        .setParameter(2, access.current().departmentId)
        .setParameter(3, access.role().scope.equals("SELF"))
        .setParameter(4, access.current().username)
        .setMaxResults(500)
        .getResultList()
        .stream()
        .filter(
            e ->
                access.visible(e.departmentId)
                    && (!access.role().scope.equals("SELF")
                        || e.actor.equals(access.current().username)))
        .sorted(Comparator.comparing((AuditEvent e) -> e.id).reversed())
        .toList();
  }

  /** 管理资源真实读取。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin/{type}")
  public Object adminList(@PathVariable String type) {
    return admin.list(type);
  }

  /** 管理资源创建。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{type}")
  public Object adminCreate(@PathVariable String type, @RequestBody AdminService.Input v) {
    return admin.save(type, null, v);
  }

  /** 管理资源编辑。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{type}/{id}")
  public Object adminEdit(
      @PathVariable String type, @PathVariable Long id, @RequestBody AdminService.Input v) {
    return admin.save(type, id, v);
  }

  /** 未引用的管理资源删除，外键保护业务历史。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/admin/{type}/{id}")
  public Object adminDelete(@PathVariable String type, @PathVariable Long id) {
    admin.delete(type, id);
    return Map.of("ok", true);
  }
}

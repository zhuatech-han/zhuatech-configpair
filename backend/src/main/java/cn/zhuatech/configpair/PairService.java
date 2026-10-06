// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.configpair;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

/** 参数模型、生成快照、独立审签与人工执行覆盖的事务边界。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class PairService {
  final Store db;
  final AccessService access;
  final Clock clock;
  final ObjectMapper json;

  public PairService(Store db, AccessService access, Clock clock, ObjectMapper json) {
    this.db = db;
    this.access = access;
    this.clock = clock;
    this.json = json;
  }

  /** 计划有限字段，不接受状态或系统摘要。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record JobInput(
      String requestKey,
      Long version,
      String reference,
      String name,
      Long departmentId,
      String category,
      String systemName,
      String buildLabel,
      String environment,
      String instructions,
      Long reviewerId,
      Long operatorId) {}

  /** 参数有2至8个明确值。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record FactorInput(
      String requestKey, Long version, Long jobId, String code, String name, List<String> values) {}

  /** 两个不同参数值的显式禁配。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record ConstraintInput(
      String requestKey,
      Long version,
      Long jobId,
      Long leftFactorId,
      String leftValue,
      Long rightFactorId,
      String rightValue,
      String reason) {}

  /** 指定人员人工结果，FAIL／BLOCKED必须说明，证据仅保存参考文本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record ResultInput(
      String requestKey,
      Long version,
      Long jobId,
      Integer caseNumber,
      String status,
      String note,
      String evidence) {}

  /** 明确计划版本与状态命令，报告申报FINISHED或PARTIAL。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Command(String requestKey, Long version, Long jobId, String note, String outcome) {}

  private static final Set<String> EDITABLE = Set.of("DRAFT", "GENERATED");

  private Long who() {
    return access.current().id;
  }

  private Instant now() {
    return BusinessTime.now(clock);
  }

  private void check(boolean ok, String code) {
    if (!ok) throw new Problem(409, code);
  }

  private void input(boolean ok, String code) {
    if (!ok) throw new Problem(400, code);
  }

  private String text(String s, int n) {
    return AdminService.text(s, n);
  }

  private String optional(String s, int n) {
    if (s == null) return "";
    input(s.length() <= n, "INVALID_INPUT");
    return s.trim();
  }

  private void lock() {
    db.lock(Department.class, 1L);
    var a = access.current();
    db.refresh(a);
    db.refresh(db.get(AccessRole.class, a.roleId));
    access.current();
  }

  private void version(PairJob j, Long expected) {
    check(expected != null && expected.longValue() == j.version, "STALE_VERSION");
  }

  private List<PairFactor> factors(Long id) {
    return db.query(PairFactor.class, "from PairFactor where jobId=?1 order by id", id);
  }

  private List<PairConstraint> rules(Long id) {
    return db.query(PairConstraint.class, "from PairConstraint where jobId=?1 order by id", id);
  }

  private List<PairResult> results(PairJob j) {
    return j.activeRevisionId == null
        ? List.of()
        : db.query(
            PairResult.class,
            "from PairResult where jobId=?1 and revisionId=?2 order by caseNumber",
            j.id,
            j.activeRevisionId);
  }

  private boolean editor(PairJob j, Long actor) {
    return !db.query(PairEditor.class, "from PairEditor where jobId=?1 and actorId=?2", j.id, actor)
        .isEmpty();
  }

  private boolean operatorOnly() {
    var ps = access.role().permissions;
    return ps.contains("test.write")
        && !ps.contains("job.write")
        && !ps.contains("job.review")
        && !ps.contains("admin");
  }

  private boolean scope(PairJob j) {
    if (!access.visible(j.departmentId)) return false;
    if (operatorOnly())
      return Objects.equals(j.operatorId, who())
          && Objects.equals(j.departmentId, access.current().departmentId);
    return !access.role().scope.equals("SELF")
        || Objects.equals(j.createdBy, who())
        || Objects.equals(j.operatorId, who())
        || Objects.equals(j.reviewerId, who())
        || editor(j, who());
  }

  private PairJob read(Long id) {
    access.require("job.read");
    var j = db.get(PairJob.class, id);
    if (!scope(j)) throw new Problem(403, "OUT_OF_SCOPE");
    return j;
  }

  private void writer(PairJob j) {
    read(j.id);
    access.require("job.write");
  }

  private void reviewer(PairJob j) {
    read(j.id);
    access.require("job.review");
    if (!eligible(access.current(), "job.review", j.departmentId)
        || !Objects.equals(j.reviewerId, who())
        || Objects.equals(j.operatorId, who())
        || editor(j, who())) throw new Problem(403, "INDEPENDENT_REVIEW_REQUIRED");
  }

  private void operator(PairJob j) {
    read(j.id);
    access.require("test.write");
    if (!eligible(access.current(), "test.write", j.departmentId)
        || !Objects.equals(j.operatorId, who())
        || editor(j, who())) throw new Problem(403, "ASSIGNED_OPERATOR_ONLY");
  }

  private void markEditor(PairJob j) {
    if (!editor(j, who())) {
      var e = new PairEditor();
      e.jobId = j.id;
      e.actorId = who();
      db.save(e);
    }
  }

  private void invalidate(PairJob j) {
    j.status = "DRAFT";
    j.activeRevisionId = null;
    j.approvedHash = null;
  }

  private Map<String, Object> map(Object... values) {
    var m = new LinkedHashMap<String, Object>();
    for (int i = 0; i < values.length; i += 2) m.put((String) values[i], values[i + 1]);
    return m;
  }

  private String encode(Object v) {
    return json.writeValueAsString(v);
  }

  private Object decode(String s) {
    return json.readValue(s, Object.class);
  }

  private String hash(Object v) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(encode(v).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private Object memo(String key, Object payload, Supplier<Object> work) {
    input(
        key != null
            && key.matches(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"),
        "INVALID_REQUEST_KEY");
    String fingerprint = hash(List.of(who(), payload));
    var rows = db.query(CommandRecord.class, "from CommandRecord where requestKey=?1", key);
    if (!rows.isEmpty()) {
      check(rows.getFirst().fingerprint.equals(fingerprint), "REQUEST_KEY_REUSED");
      return decode(rows.getFirst().responseJson);
    }
    check(db.all(CommandRecord.class).size() < 10000, "COMMAND_LIMIT");
    var response = work.get();
    db.flush();
    var c = new CommandRecord();
    c.requestKey = key;
    c.fingerprint = fingerprint;
    c.responseJson = encode(response);
    db.save(c);
    return response;
  }

  private BusinessEvent event(PairJob j, String action, String note) {
    var e = new BusinessEvent();
    e.objectType = "JOB";
    e.objectId = j.id;
    e.actorId = who();
    e.action = action;
    e.note = optional(note, 1000);
    e.snapshot =
        encode(
            map(
                "status",
                j.status,
                "version",
                j.version,
                "revisionId",
                j.activeRevisionId,
                "approvedHash",
                j.approvedHash,
                "reportHash",
                j.reportHash,
                "outcome",
                j.outcome));
    e.createdAt = now();
    db.save(e);
    access.audit(action, j.id, j.departmentId);
    return e;
  }

  private boolean eligible(Account a, String permission, Long department) {
    return a.enabled
        && Objects.equals(a.departmentId, department)
        && db.get(AccessRole.class, a.roleId)
            .permissions
            .containsAll(Set.of("job.read", permission));
  }

  private void ready(PairJob j) {
    check(
        !Objects.equals(j.reviewerId, j.operatorId)
            && !editor(j, j.reviewerId)
            && !editor(j, j.operatorId),
        "INDEPENDENT_REVIEW_REQUIRED");
    check(
        eligible(db.get(Account.class, j.reviewerId), "job.review", j.departmentId)
            && eligible(db.get(Account.class, j.operatorId), "test.write", j.departmentId),
        "ASSIGNED_ACCOUNT_UNAVAILABLE");
  }

  private List<String> values(PairFactor f) {
    return Arrays.asList(json.readValue(f.valuesJson, String[].class));
  }

  private Map<String, Object> factorDto(PairFactor f) {
    return map("id", f.id, "jobId", f.jobId, "code", f.code, "name", f.name, "values", values(f));
  }

  private Map<String, Object> jobDto(PairJob j) {
    return map(
        "id",
        j.id,
        "reference",
        j.reference,
        "name",
        j.name,
        "departmentId",
        j.departmentId,
        "category",
        j.category,
        "systemName",
        j.systemName,
        "buildLabel",
        j.buildLabel,
        "environment",
        j.environment,
        "instructions",
        j.instructions,
        "reviewerId",
        j.reviewerId,
        "operatorId",
        j.operatorId,
        "createdBy",
        j.createdBy,
        "status",
        j.status,
        "version",
        j.version,
        "activeRevisionId",
        j.activeRevisionId,
        "approvedHash",
        j.approvedHash,
        "reportHash",
        j.reportHash,
        "closedHash",
        j.closedHash,
        "requestedOutcome",
        j.requestedOutcome,
        "outcome",
        j.outcome,
        "acknowledgedAt",
        j.acknowledgedAt,
        "approvedAt",
        j.approvedAt,
        "closedAt",
        j.closedAt,
        "createdAt",
        j.createdAt);
  }

  private Object inputSnapshot(PairJob j) {
    return map(
        "reference",
        j.reference,
        "name",
        j.name,
        "departmentId",
        j.departmentId,
        "category",
        j.category,
        "systemName",
        j.systemName,
        "buildLabel",
        j.buildLabel,
        "environment",
        j.environment,
        "instructions",
        j.instructions,
        "reviewerId",
        j.reviewerId,
        "operatorId",
        j.operatorId,
        "factors",
        factors(j.id).stream().map(this::factorDto).toList(),
        "constraints",
        rules(j.id));
  }

  private PairRevision active(PairJob j) {
    check(j.activeRevisionId != null, "GENERATION_REQUIRED");
    var r = db.get(PairRevision.class, j.activeRevisionId);
    check(
        Objects.equals(r.jobId, j.id)
            && r.inputHash.equals(hash(inputSnapshot(j)))
            && r.planHash.equals(hash(decode(r.planJson))),
        "SNAPSHOT_CHANGED");
    return r;
  }

  private PairEngine.Plan plan(PairJob j) {
    return json.readValue(active(j).planJson, PairEngine.Plan.class);
  }

  private String planSeal(PairJob j) {
    var r = active(j);
    return hash(List.of(r.inputHash, r.planHash));
  }

  private String reportSeal(PairJob j) {
    return hash(map("outcome", j.requestedOutcome, "results", results(j)));
  }

  private PairEngine.Coverage coverage(PairJob j) {
    return PairEngine.measure(
        plan(j),
        results(j).stream().map(r -> new PairEngine.Execution(r.caseNumber, r.status)).toList());
  }

  /** 返回授权表单目录，账号无口令等内部字段。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object options() {
    access.require("job.read");
    return map(
        "departments",
        db.all(Department.class).stream().filter(d -> access.visible(d.id)).toList(),
        "categories",
        db.all(DictionaryEntry.class).stream().filter(d -> d.type.equals("category")).toList(),
        "accounts",
        db.all(Account.class).stream()
            .filter(a -> a.enabled && access.visible(a.departmentId))
            .map(
                a ->
                    map(
                        "id",
                        a.id,
                        "displayName",
                        a.displayName,
                        "departmentId",
                        a.departmentId,
                        "permissions",
                        db.get(AccessRole.class, a.roleId).permissions))
            .toList(),
        "companyName",
        db.query(SystemSetting.class, "from SystemSetting where code='companyName'")
            .getFirst()
            .value);
  }

  /** 范围内搜索、分页和稳定排序，最多50条。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object list(String search, String status, int page, int size, String sort) {
    access.require("job.read");
    input(
        search != null
            && search.length() <= 160
            && status != null
            && status.length() <= 30
            && page >= 0
            && page <= 1000
            && size >= 1
            && size <= 50
            && Set.of("newest", "oldest", "reference").contains(sort),
        "INVALID_INPUT");
    var all =
        db.all(PairJob.class).stream()
            .filter(this::scope)
            .filter(j -> status.isEmpty() || j.status.equals(status))
            .filter(
                j ->
                    (j.reference + " " + j.name + " " + j.systemName + " " + j.buildLabel)
                        .toLowerCase(Locale.ROOT)
                        .contains(search.toLowerCase(Locale.ROOT)))
            .toList();
    Comparator<PairJob> comparator =
        sort.equals("reference")
            ? Comparator.comparing(j -> j.reference)
            : sort.equals("oldest")
                ? Comparator.comparing(j -> j.id)
                : Comparator.comparing((PairJob j) -> j.id).reversed();
    return map(
        "total",
        all.size(),
        "rows",
        all.stream()
            .sorted(comparator)
            .skip((long) page * size)
            .limit(size)
            .map(this::jobDto)
            .toList());
  }

  /** 当前计划、生成快照、人工结果和覆盖按父计划授权。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object detail(Long id) {
    var j = read(id);
    var m = jobDto(j);
    m.put("factors", factors(id).stream().map(this::factorDto).toList());
    m.put("constraints", rules(id));
    m.put("results", results(j));
    m.put("plan", j.activeRevisionId == null ? null : plan(j));
    m.put("coverage", j.activeRevisionId == null ? null : coverage(j));
    m.put(
        "revisions",
        db.query(PairRevision.class, "from PairRevision where jobId=?1 order by id desc", id));
    m.put(
        "history",
        db.jpql(
                BusinessEvent.class,
                "from BusinessEvent where objectType='JOB' and objectId=?1 order by id desc")
            .setParameter(1, id)
            .setMaxResults(200)
            .getResultList());
    var ps = access.role().permissions;
    m.put("canWrite", ps.contains("job.write"));
    m.put(
        "canReview",
        ps.contains("job.review")
            && Objects.equals(j.reviewerId, who())
            && !editor(j, who())
            && !Objects.equals(j.operatorId, who()));
    m.put(
        "canOperate",
        ps.contains("test.write") && Objects.equals(j.operatorId, who()) && !editor(j, who()));
    return m;
  }

  /** 不可变历史版本按父计划读取，不推断已执行。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object revision(Long id) {
    var r = db.get(PairRevision.class, id);
    read(r.jobId);
    return map("revision", r, "input", decode(r.inputJson), "plan", decode(r.planJson));
  }

  /** 新建或修改草稿计划，环境与判定说明都是冻结输入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object saveJob(Long id, JobInput v) {
    input(v != null, "INVALID_INPUT");
    lock();
    access.require("job.read");
    access.require("job.write");
    access.department(v.departmentId);
    PairJob existing = id == null ? null : read(id);
    if (existing != null) writer(existing);
    var prior =
        db.query(CommandRecord.class, "from CommandRecord where requestKey=?1", v.requestKey);
    if (id == null && !prior.isEmpty()) {
      check(
          prior.getFirst().fingerprint.equals(hash(List.of(who(), List.of("job", "new", v)))),
          "REQUEST_KEY_REUSED");
      read(json.readTree(prior.getFirst().responseJson).get("id").asLong());
    }
    return memo(
        v.requestKey,
        List.of("job", id == null ? "new" : id, v),
        () -> {
          PairJob j = existing == null ? new PairJob() : existing;
          if (existing != null) {
            version(j, v.version);
            check(EDITABLE.contains(j.status), "FROZEN");
            check(
                Objects.equals(j.departmentId, v.departmentId) && j.reference.equals(v.reference),
                "IDENTITY_IMMUTABLE");
          } else {
            int maximum =
                Integer.parseInt(
                    db.query(SystemSetting.class, "from SystemSetting where code='maxRecords'")
                        .getFirst()
                        .value);
            check(db.all(PairJob.class).size() < maximum, "RECORD_LIMIT");
            j.reference = text(v.reference, 60);
            j.departmentId = db.get(Department.class, v.departmentId).id;
            j.createdBy = who();
            j.createdAt = now();
            j.version = 0;
          }
          j.name = text(v.name, 160);
          j.category = text(v.category, 60);
          input(
              !db.query(
                      DictionaryEntry.class,
                      "from DictionaryEntry where type='category' and code=?1",
                      j.category)
                  .isEmpty(),
              "INVALID_CATEGORY");
          j.systemName = text(v.systemName, 160);
          j.buildLabel = text(v.buildLabel, 160);
          j.environment = text(v.environment, 1000);
          j.instructions = text(v.instructions, 1000);
          j.reviewerId = db.get(Account.class, v.reviewerId).id;
          j.operatorId = db.get(Account.class, v.operatorId).id;
          check(
              !Objects.equals(j.reviewerId, j.operatorId)
                  && !Objects.equals(j.reviewerId, who())
                  && !Objects.equals(j.operatorId, who()),
              "INDEPENDENT_REVIEW_REQUIRED");
          check(
              eligible(db.get(Account.class, j.reviewerId), "job.review", j.departmentId)
                  && eligible(db.get(Account.class, j.operatorId), "test.write", j.departmentId),
              "ASSIGNED_ACCOUNT_UNAVAILABLE");
          invalidate(j);
          if (existing == null) db.save(j);
          markEditor(j);
          ready(j);
          j.version++;
          event(j, existing == null ? "CREATE_JOB" : "UPDATE_JOB", j.instructions);
          return jobDto(j);
        });
  }

  /** 参数编辑使生成失效；约束引用值不能被静默移除。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object saveFactor(Long id, FactorInput v) {
    input(v != null, "INVALID_INPUT");
    lock();
    var j = read(v.jobId);
    writer(j);
    return memo(
        v.requestKey,
        List.of("factor", id == null ? "new" : id, v),
        () -> {
          version(j, v.version);
          check(EDITABLE.contains(j.status), "FROZEN");
          var fs = factors(j.id);
          check(id != null || fs.size() < 8, "FACTOR_LIMIT");
          var f = id == null ? new PairFactor() : db.get(PairFactor.class, id);
          check(id == null || Objects.equals(f.jobId, j.id), "PARENT_MISMATCH");
          String code = text(v.code, 30);
          input(code.matches("[A-Z][A-Z0-9_-]{0,29}"), "INVALID_FACTOR");
          input(v.values != null && v.values.size() >= 2 && v.values.size() <= 8, "INVALID_VALUES");
          List<String> vs = v.values.stream().map(x -> text(x, 60)).toList();
          input(
              new HashSet<>(vs).size() == vs.size()
                  && vs.stream().allMatch(x -> x.chars().noneMatch(Character::isISOControl)),
              "INVALID_VALUES");
          check(
              fs.stream().noneMatch(x -> !Objects.equals(x.id, id) && x.code.equals(code)),
              "DUPLICATE_FACTOR");
          for (var r : rules(j.id))
            check(
                (!Objects.equals(r.leftFactorId, id) || vs.contains(r.leftValue))
                    && (!Objects.equals(r.rightFactorId, id) || vs.contains(r.rightValue)),
                "VALUE_IN_USE");
          int product = vs.size();
          for (var x : fs)
            if (!Objects.equals(x.id, id)) {
              product *= values(x).size();
              input(product <= 20000, "SPACE_LIMIT");
            }
          f.jobId = j.id;
          f.code = code;
          f.name = text(v.name, 160);
          f.valuesJson = encode(vs);
          if (id == null) db.save(f);
          markEditor(j);
          ready(j);
          invalidate(j);
          j.version++;
          event(j, id == null ? "CREATE_FACTOR" : "UPDATE_FACTOR", f.code);
          return map("factor", factorDto(f), "job", jobDto(j));
        });
  }

  /** 删除未引用草稿参数，历史快照保留原模型。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object deleteFactor(Long id, Command v) {
    input(v != null, "INVALID_INPUT");
    lock();
    var j = read(v.jobId);
    writer(j);
    return memo(
        v.requestKey,
        List.of("delete-factor", id, v),
        () -> {
          var f = db.get(PairFactor.class, id);
          check(Objects.equals(f.jobId, j.id), "PARENT_MISMATCH");
          version(j, v.version);
          check(EDITABLE.contains(j.status), "FROZEN");
          check(
              rules(j.id).stream()
                  .noneMatch(r -> r.leftFactorId.equals(id) || r.rightFactorId.equals(id)),
              "FACTOR_IN_USE");
          markEditor(j);
          ready(j);
          db.delete(f);
          invalidate(j);
          j.version++;
          event(j, "DELETE_FACTOR", text(v.note, 1000));
          return jobDto(j);
        });
  }

  /** 禁配仅连接同计划两个参数，规范顺序避免反向重复。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object saveConstraint(Long id, ConstraintInput v) {
    input(v != null, "INVALID_INPUT");
    lock();
    var j = read(v.jobId);
    writer(j);
    return memo(
        v.requestKey,
        List.of("constraint", id == null ? "new" : id, v),
        () -> {
          version(j, v.version);
          check(EDITABLE.contains(j.status), "FROZEN");
          var rs = rules(j.id);
          check(id != null || rs.size() < 80, "CONSTRAINT_LIMIT");
          var r = id == null ? new PairConstraint() : db.get(PairConstraint.class, id);
          check(id == null || Objects.equals(r.jobId, j.id), "PARENT_MISMATCH");
          var a = db.get(PairFactor.class, v.leftFactorId);
          var b = db.get(PairFactor.class, v.rightFactorId);
          input(
              !a.id.equals(b.id) && a.jobId.equals(j.id) && b.jobId.equals(j.id),
              "INVALID_CONSTRAINT");
          String av = text(v.leftValue, 60), bv = text(v.rightValue, 60);
          input(values(a).contains(av) && values(b).contains(bv), "INVALID_CONSTRAINT");
          r.jobId = j.id;
          r.leftFactorId = a.id < b.id ? a.id : b.id;
          r.rightFactorId = a.id < b.id ? b.id : a.id;
          r.leftValue = a.id < b.id ? av : bv;
          r.rightValue = a.id < b.id ? bv : av;
          r.reason = text(v.reason, 1000);
          check(
              rs.stream()
                  .noneMatch(
                      x ->
                          !Objects.equals(x.id, id)
                              && x.leftFactorId.equals(r.leftFactorId)
                              && x.rightFactorId.equals(r.rightFactorId)
                              && x.leftValue.equals(r.leftValue)
                              && x.rightValue.equals(r.rightValue)),
              "DUPLICATE_CONSTRAINT");
          if (id == null) db.save(r);
          markEditor(j);
          ready(j);
          invalidate(j);
          j.version++;
          event(j, id == null ? "CREATE_CONSTRAINT" : "UPDATE_CONSTRAINT", r.reason);
          return map("constraint", r, "job", jobDto(j));
        });
  }

  /** 删除草稿禁配并作废活动生成。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object deleteConstraint(Long id, Command v) {
    input(v != null, "INVALID_INPUT");
    lock();
    var j = read(v.jobId);
    writer(j);
    return memo(
        v.requestKey,
        List.of("delete-constraint", id, v),
        () -> {
          var r = db.get(PairConstraint.class, id);
          check(Objects.equals(r.jobId, j.id), "PARENT_MISMATCH");
          version(j, v.version);
          check(EDITABLE.contains(j.status), "FROZEN");
          markEditor(j);
          ready(j);
          db.delete(r);
          invalidate(j);
          j.version++;
          event(j, "DELETE_CONSTRAINT", text(v.note, 1000));
          return jobDto(j);
        });
  }

  /** 收悉冻结计划后，指定执行人逐例登记，不自动生成通过结果。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object saveResult(ResultInput v) {
    input(v != null, "INVALID_INPUT");
    lock();
    var j = read(v.jobId);
    operator(j);
    return memo(
        v.requestKey,
        List.of("result", v),
        () -> {
          version(j, v.version);
          check(j.status.equals("RUNNING") && j.acknowledgedAt != null, "ACKNOWLEDGEMENT_REQUIRED");
          check(j.approvedHash.equals(planSeal(j)), "SNAPSHOT_CHANGED");
          var p = plan(j);
          input(
              v.caseNumber != null
                  && v.caseNumber >= 1
                  && v.caseNumber <= p.cases().size()
                  && v.status != null
                  && Set.of("PASS", "FAIL", "BLOCKED").contains(v.status),
              "INVALID_RESULT");
          var rows =
              db.query(
                  PairResult.class,
                  "from PairResult where jobId=?1 and revisionId=?2 and caseNumber=?3",
                  j.id,
                  j.activeRevisionId,
                  v.caseNumber);
          var r = rows.isEmpty() ? new PairResult() : rows.getFirst();
          String previous = r.status;
          Object previousFacts = rows.isEmpty() ? null : decode(encode(r));
          r.jobId = j.id;
          r.revisionId = j.activeRevisionId;
          r.caseNumber = v.caseNumber;
          r.status = v.status;
          r.note = v.status.equals("PASS") ? optional(v.note, 1000) : text(v.note, 1000);
          r.evidence = optional(v.evidence, 1000);
          r.actorId = who();
          r.recordedAt = now();
          if (rows.isEmpty()) db.save(r);
          j.version++;
          var journal =
              event(
                  j,
                  "SAVE_RESULT",
                  "#"
                      + r.caseNumber
                      + " "
                      + (previous == null ? "PENDING" : previous)
                      + " -> "
                      + r.status);
          journal.snapshot =
              encode(
                  map(
                      "job",
                      decode(journal.snapshot),
                      "previousResult",
                      previousFacts,
                      "result",
                      r));
          db.flush();
          return map("result", r, "job", jobDto(j), "coverage", coverage(j));
        });
  }

  /** 生成、指定独立批准、人工报告与最终封存的状态命令。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object command(Long id, String action, Command v) {
    input(v != null && Objects.equals(v.jobId, id), "INVALID_INPUT");
    lock();
    var j = read(id);
    switch (action) {
      case "generate", "submit", "start", "cancel" -> writer(j);
      case "approve", "return-plan", "return-result", "close" -> reviewer(j);
      case "acknowledge", "submit-report" -> operator(j);
      default -> throw new Problem(404, "NOT_FOUND");
    }
    return memo(
        v.requestKey,
        List.of("command", id, action, v),
        () -> {
          version(j, v.version);
          String note = text(v.note, 1000);
          switch (action) {
            case "generate" -> {
              check(EDITABLE.contains(j.status), "FROZEN");
              check(
                  db.query(PairRevision.class, "from PairRevision where jobId=?1", j.id).size()
                      < 50,
                  "REVISION_LIMIT");
              var fs = factors(j.id);
              var engineFactors =
                  fs.stream().map(f -> new PairEngine.Factor(f.code, f.name, values(f))).toList();
              var engineRules =
                  rules(j.id).stream()
                      .map(
                          r ->
                              new PairEngine.Forbidden(
                                  db.get(PairFactor.class, r.leftFactorId).code,
                                  r.leftValue,
                                  db.get(PairFactor.class, r.rightFactorId).code,
                                  r.rightValue))
                      .toList();
              var layout = PairEngine.generate(engineFactors, engineRules);
              markEditor(j);
              ready(j);
              var r = new PairRevision();
              r.jobId = j.id;
              r.algorithm = PairEngine.VERSION;
              r.inputJson = encode(inputSnapshot(j));
              r.planJson = encode(layout);
              r.inputHash = hash(decode(r.inputJson));
              r.planHash = hash(decode(r.planJson));
              r.createdBy = who();
              r.createdAt = now();
              db.save(r);
              j.activeRevisionId = r.id;
              j.status = "GENERATED";
            }
            case "submit" -> {
              check(j.status.equals("GENERATED"), "INVALID_STATE");
              ready(j);
              active(j);
              j.status = "SUBMITTED";
            }
            case "return-plan" -> {
              check(j.status.equals("SUBMITTED"), "INVALID_STATE");
              invalidate(j);
            }
            case "approve" -> {
              check(j.status.equals("SUBMITTED"), "INVALID_STATE");
              ready(j);
              j.approvedHash = planSeal(j);
              j.approvedAt = now();
              j.status = "APPROVED";
            }
            case "start" -> {
              check(j.status.equals("APPROVED"), "INVALID_STATE");
              ready(j);
              check(j.approvedHash.equals(planSeal(j)), "SNAPSHOT_CHANGED");
              j.status = "RUNNING";
            }
            case "acknowledge" -> {
              check(j.status.equals("RUNNING") && j.acknowledgedAt == null, "INVALID_STATE");
              check(j.approvedHash.equals(planSeal(j)), "SNAPSHOT_CHANGED");
              j.acknowledgedAt = now();
            }
            case "submit-report" -> {
              check(
                  j.status.equals("RUNNING") && j.acknowledgedAt != null,
                  "ACKNOWLEDGEMENT_REQUIRED");
              input(
                  v.outcome != null && Set.of("FINISHED", "PARTIAL").contains(v.outcome),
                  "INVALID_OUTCOME");
              var c = coverage(j);
              check(c.pending() == 0, "INCOMPLETE_RESULT");
              check(v.outcome.equals("PARTIAL") == (c.blocked() > 0), "OUTCOME_MISMATCH");
              check(j.approvedHash.equals(planSeal(j)), "SNAPSHOT_CHANGED");
              j.requestedOutcome = v.outcome;
              j.reportHash = reportSeal(j);
              j.status = "REVIEW";
            }
            case "return-result" -> {
              check(j.status.equals("REVIEW"), "INVALID_STATE");
              j.reportHash = null;
              j.requestedOutcome = null;
              j.status = "RUNNING";
            }
            case "close" -> {
              check(j.status.equals("REVIEW"), "INVALID_STATE");
              check(
                  j.approvedHash.equals(planSeal(j)) && j.reportHash.equals(reportSeal(j)),
                  "SNAPSHOT_CHANGED");
              var c = coverage(j);
              j.outcome = c.blocked() > 0 ? "PARTIAL" : c.failed() > 0 ? "ISSUES" : "PASSED";
              j.closedHash = hash(List.of(j.approvedHash, j.reportHash, j.outcome));
              j.closedAt = now();
              j.status = "CLOSED";
            }
            case "cancel" -> {
              check(
                  Set.of("DRAFT", "GENERATED", "SUBMITTED", "APPROVED").contains(j.status),
                  "INVALID_STATE");
              j.status = "CANCELLED";
            }
            default -> throw new Problem(404, "NOT_FOUND");
          }
          j.version++;
          event(j, action.toUpperCase(Locale.ROOT).replace('-', '_'), note);
          return jobDto(j);
        });
  }

  /** 授权范围内实际覆盖统计，分母为各计划可达值对之和，不跨模型合并。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object dashboard() {
    access.require("dashboard");
    access.require("job.read");
    var jobs = db.all(PairJob.class).stream().filter(this::scope).toList();
    var statuses = new TreeMap<String, Long>();
    var outcomes = new TreeMap<String, Long>();
    long cases = 0,
        passed = 0,
        failed = 0,
        blocked = 0,
        pending = 0,
        feasible = 0,
        executed = 0,
        passing = 0;
    for (var j : jobs) {
      statuses.merge(j.status, 1L, Long::sum);
      if (j.outcome != null) outcomes.merge(j.outcome, 1L, Long::sum);
      if (j.activeRevisionId != null && !j.status.equals("CANCELLED")) {
        var c = coverage(j);
        cases += c.cases();
        passed += c.passed();
        failed += c.failed();
        blocked += c.blocked();
        pending += c.pending();
        feasible += c.feasiblePairs();
        executed += c.executedPairs();
        passing += c.passedPairs();
      }
    }
    return map(
        "jobs",
        jobs.size(),
        "statuses",
        statuses,
        "outcomes",
        outcomes,
        "cases",
        cases,
        "passed",
        passed,
        "failed",
        failed,
        "blocked",
        blocked,
        "pending",
        pending,
        "feasiblePairs",
        feasible,
        "executedPairs",
        executed,
        "passedPairs",
        passing);
  }

  private String cell(Object v) {
    String s = v == null ? "" : v.toString();
    String first = s.stripLeading();
    if ((!first.isEmpty() && "=+@-".indexOf(first.charAt(0)) >= 0)
        || s.startsWith("\t")
        || s.startsWith("\r")) s = "'" + s;
    return "\"" + s.replace("\"", "\"\"") + "\"";
  }

  /** 冻结用例与人工结果导出，PENDING明确代表未登记，不加入宣传。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public String csv(Long id) {
    access.require("export");
    var j = read(id);
    var p = plan(j);
    var rs = results(j);
    var out = new StringBuilder("\uFEFFcase_number,");
    out.append(String.join(",", p.factors().stream().map(f -> cell(f.code())).toList()))
        .append(",status,note,evidence\r\n");
    for (var c : p.cases()) {
      var r = rs.stream().filter(x -> x.caseNumber == c.number()).findFirst().orElse(null);
      List<Object> row = new ArrayList<>();
      row.add(c.number());
      row.addAll(c.selections().stream().map(PairEngine.Selection::value).toList());
      row.add(r == null ? "PENDING" : r.status);
      row.add(r == null ? null : r.note);
      row.add(r == null ? null : r.evidence);
      out.append(String.join(",", row.stream().map(this::cell).toList())).append("\r\n");
    }
    return out.toString();
  }
}

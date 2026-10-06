// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.configpair;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

/** HTTP/JPA验证参数模型、可达覆盖、岗位、冻结、幂等与人工执行结果闭环。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PairIntegrationTest {
  static final String PASSWORD = "Aa9" + UUID.randomUUID();

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("configpair.admin-password", () -> PASSWORD);
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate sql;
  final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
  MockHttpSession admin, review, operator, outside, viewer, second;
  long reviewerId, operatorId, secondId, operatorRole;

  String key() {
    return UUID.randomUUID().toString();
  }

  Map<String, Object> m(Object... args) {
    var m = new LinkedHashMap<String, Object>();
    for (int i = 0; i < args.length; i += 2) m.put((String) args[i], args[i + 1]);
    return m;
  }

  MvcResult req(MockHttpSession who, String method, String path, Object value) throws Exception {
    var b =
        switch (method) {
          case "POST" -> post("/api" + path);
          case "PUT" -> put("/api" + path);
          case "DELETE" -> delete("/api" + path);
          default -> get("/api" + path);
        };
    if (who != null) b.session(who);
    b.with(csrf());
    if (value != null) b.contentType("application/json").content(json.writeValueAsString(value));
    return mvc.perform(b).andReturn();
  }

  JsonNode ok(MockHttpSession who, String method, String path, Object v) throws Exception {
    var r = req(who, method, path, v);
    assertEquals(
        200, r.getResponse().getStatus(), path + " " + r.getResponse().getContentAsString());
    return json.readTree(r.getResponse().getContentAsString());
  }

  void fail(MockHttpSession who, String method, String path, Object v, int status, String code)
      throws Exception {
    var r = req(who, method, path, v);
    assertEquals(
        status, r.getResponse().getStatus(), path + " " + r.getResponse().getContentAsString());
    assertEquals(code, json.readTree(r.getResponse().getContentAsString()).path("code").asString());
  }

  MockHttpSession login(String name) throws Exception {
    var r = req(null, "POST", "/auth/login", Map.of("username", name, "password", PASSWORD));
    assertEquals(200, r.getResponse().getStatus());
    return (MockHttpSession) r.getRequest().getSession(false);
  }

  long role(String scope, Set<String> ps) throws Exception {
    return ok(
            admin,
            "POST",
            "/admin/roles",
            m("name", "TEST" + key(), "scope", scope, "permissions", ps))
        .path("id")
        .asLong();
  }

  JsonNode user(long role, long dept) throws Exception {
    return ok(
        admin,
        "POST",
        "/admin/users",
        m(
            "username",
            "u" + key().substring(0, 8),
            "displayName",
            "TEST岗位",
            "roleId",
            role,
            "departmentId",
            dept,
            "enabled",
            true,
            "password",
            PASSWORD));
  }

  @BeforeAll
  void setup() throws Exception {
    admin = login("admin");
    var r = user(role("ALL", Set.of("job.read", "job.review", "dashboard", "export")), 1);
    reviewerId = r.path("id").asLong();
    review = login(r.path("username").asString());
    operatorRole = role("ALL", Set.of("job.read", "test.write", "dashboard", "export"));
    var o = user(operatorRole, 1);
    operatorId = o.path("id").asLong();
    operator = login(o.path("username").asString());
    long d =
        ok(admin, "POST", "/admin/departments", m("name", "TEST外部" + key())).path("id").asLong();
    var x = user(role("DEPARTMENT", Set.of("job.read", "job.write", "dashboard", "export")), d);
    outside = login(x.path("username").asString());
    var v = user(role("SELF", Set.of("job.read", "dashboard", "export")), 1);
    viewer = login(v.path("username").asString());
    var s = user(1, 1);
    secondId = s.path("id").asLong();
    second = login(s.path("username").asString());
  }

  Map<String, Object> jobInput() {
    return m(
        "requestKey",
        key(),
        "reference",
        "CP" + key(),
        "name",
        "TEST组合测试",
        "departmentId",
        1,
        "category",
        "WEB",
        "systemName",
        "TEST浏览器应用",
        "buildLabel",
        "TEST-build-1",
        "environment",
        "TEST隔离环境",
        "instructions",
        "TEST逐例人工核对响应及权限",
        "reviewerId",
        reviewerId,
        "operatorId",
        operatorId);
  }

  JsonNode current(JsonNode j) throws Exception {
    return ok(admin, "GET", "/jobs/" + j.path("id").asLong(), null);
  }

  Map<String, Object> factor(JsonNode j, String code) {
    return m(
        "requestKey",
        key(),
        "version",
        j.path("version").asLong(),
        "jobId",
        j.path("id").asLong(),
        "code",
        code,
        "name",
        "TEST参数" + code,
        "values",
        List.of("0", "1"));
  }

  JsonNode draft() throws Exception {
    var j = ok(admin, "POST", "/jobs", jobInput());
    for (String code : List.of("A", "B", "C"))
      j = ok(admin, "POST", "/factors", factor(j, code)).path("job");
    return current(j);
  }

  Map<String, Object> cmd(JsonNode j) {
    return m(
        "requestKey",
        key(),
        "version",
        j.path("version").asLong(),
        "jobId",
        j.path("id").asLong(),
        "note",
        "TEST核对模型和事实");
  }

  JsonNode command(MockHttpSession actor, JsonNode j, String action) throws Exception {
    return ok(actor, "POST", "/jobs/" + j.path("id").asLong() + "/commands/" + action, cmd(j));
  }

  String path(JsonNode j, String action) {
    return "/jobs/" + j.path("id").asLong() + "/commands/" + action;
  }

  JsonNode generated() throws Exception {
    return current(command(admin, draft(), "generate"));
  }

  JsonNode running() throws Exception {
    var j = generated();
    j = command(admin, j, "submit");
    j = command(review, j, "approve");
    return current(command(admin, j, "start"));
  }

  Map<String, Object> result(JsonNode j, int number, String status) {
    return m(
        "requestKey",
        key(),
        "version",
        j.path("version").asLong(),
        "jobId",
        j.path("id").asLong(),
        "caseNumber",
        number,
        "status",
        status,
        "note",
        "TEST实际核对",
        "evidence",
        "TEST记录号");
  }

  JsonNode recordAll(JsonNode j, String first) throws Exception {
    for (var c : current(j).path("plan").path("cases")) {
      j =
          ok(
                  operator,
                  "POST",
                  "/results",
                  result(
                      current(j),
                      c.path("number").asInt(),
                      c.path("number").asInt() == 1 ? first : "PASS"))
              .path("job");
    }
    return current(j);
  }

  JsonNode report(JsonNode j, String outcome) throws Exception {
    var b = cmd(j);
    b.put("outcome", outcome);
    return commandBody(operator, j, "submit-report", b);
  }

  JsonNode commandBody(MockHttpSession actor, JsonNode j, String a, Object b) throws Exception {
    return ok(actor, "POST", path(j, a), b);
  }

  Map<String, Object> rule(JsonNode j, long a, String av, long b, String bv) {
    return m(
        "requestKey",
        key(),
        "version",
        j.path("version").asLong(),
        "jobId",
        j.path("id").asLong(),
        "leftFactorId",
        a,
        "leftValue",
        av,
        "rightFactorId",
        b,
        "rightValue",
        bv,
        "reason",
        "TEST明确不兼容");
  }

  @Test
  void completedManualCycleAndFrozenSeal() throws Exception {
    var j = running();
    j = command(operator, j, "acknowledge");
    j = recordAll(j, "PASS");
    j = report(j, "FINISHED");
    j = command(review, j, "close");
    assertEquals("PASSED", j.path("outcome").asString());
    assertEquals(64, j.path("closedHash").asString().length());
    assertEquals(100, current(j).path("coverage").path("executedPercent").asInt());
    fail(operator, "POST", "/results", result(j, 1, "FAIL"), 409, "ACKNOWLEDGEMENT_REQUIRED");
    fail(admin, "POST", path(j, "generate"), cmd(j), 409, "FROZEN");
  }

  @Test
  void failuresNeverBecomeAllPassed() throws Exception {
    var j = command(operator, running(), "acknowledge");
    j = recordAll(j, "FAIL");
    assertEquals(1, j.path("coverage").path("failed").asInt());
    assertEquals(100, j.path("coverage").path("executedPercent").asInt());
    assertTrue(j.path("coverage").path("passedPercent").asDouble() < 100);
    j = report(j, "FINISHED");
    j = command(review, j, "close");
    assertEquals("ISSUES", j.path("outcome").asString());
  }

  @Test
  void blockedCaseIsExplicitPartialCoverage() throws Exception {
    var j = command(operator, running(), "acknowledge");
    j = recordAll(j, "BLOCKED");
    assertEquals(1, j.path("coverage").path("blocked").asInt());
    assertEquals(75, j.path("coverage").path("executedPercent").asInt());
    j = report(j, "PARTIAL");
    assertEquals("PARTIAL", command(review, j, "close").path("outcome").asString());
  }

  @Test
  void pendingCasesPreventReport() throws Exception {
    var j = command(operator, running(), "acknowledge");
    var b = cmd(j);
    b.put("outcome", "FINISHED");
    fail(operator, "POST", path(j, "submit-report"), b, 409, "INCOMPLETE_RESULT");
    assertEquals(4, current(j).path("coverage").path("pending").asInt());
    assertEquals(0, current(j).path("coverage").path("executedPairs").asInt());
  }

  @Test
  void reportOutcomeMustMatchExplicitBlockers() throws Exception {
    var j = recordAll(command(operator, running(), "acknowledge"), "BLOCKED");
    var b = cmd(j);
    b.put("outcome", "FINISHED");
    fail(operator, "POST", path(j, "submit-report"), b, 409, "OUTCOME_MISMATCH");
  }

  @Test
  void acknowledgementAndAssignedOperatorRequired() throws Exception {
    var j = running();
    fail(operator, "POST", "/results", result(j, 1, "PASS"), 409, "ACKNOWLEDGEMENT_REQUIRED");
    fail(second, "POST", path(j, "acknowledge"), cmd(j), 403, "ASSIGNED_OPERATOR_ONLY");
    j = command(operator, j, "acknowledge");
    fail(second, "POST", "/results", result(j, 1, "PASS"), 403, "ASSIGNED_OPERATOR_ONLY");
  }

  @Test
  void adminCannotApproveOwnPlan() throws Exception {
    var j = command(admin, generated(), "submit");
    fail(admin, "POST", path(j, "approve"), cmd(j), 403, "INDEPENDENT_REVIEW_REQUIRED");
  }

  @Test
  void crossDepartmentAndSelfScopeProtectAllViews() throws Exception {
    var j = generated();
    for (String p :
        List.of(
            "/jobs/" + j.path("id").asLong(),
            "/jobs/" + j.path("id").asLong() + "/report.json",
            "/jobs/" + j.path("id").asLong() + "/cases.csv",
            "/revisions/" + j.path("activeRevisionId").asLong())) {
      fail(outside, "GET", p, null, 403, "OUT_OF_SCOPE");
      fail(viewer, "GET", p, null, 403, "OUT_OF_SCOPE");
    }
    fail(outside, "POST", "/factors", factor(j, "Z"), 403, "OUT_OF_SCOPE");
  }

  @Test
  void historicalEditorCannotBecomeIndependentReviewer() throws Exception {
    var j = draft();
    j = ok(second, "POST", "/factors", factor(j, "D")).path("job");
    var b = jobInput();
    b.put("reference", j.path("reference").asString());
    b.put("version", j.path("version").asLong());
    b.put("reviewerId", secondId);
    fail(admin, "PUT", "/jobs/" + j.path("id").asLong(), b, 409, "INDEPENDENT_REVIEW_REQUIRED");
  }

  @Test
  void creationAndGenerationReplayAreIdentical() throws Exception {
    var b = jobInput();
    var j = ok(admin, "POST", "/jobs", b);
    assertEquals(j, ok(admin, "POST", "/jobs", b));
    j = draft();
    var c = cmd(j);
    var a = ok(admin, "POST", path(j, "generate"), c);
    assertEquals(a, ok(admin, "POST", path(j, "generate"), c));
    assertEquals(1, current(j).path("revisions").size());
    c.put("note", "TEST更改内容");
    fail(admin, "POST", path(j, "generate"), c, 409, "REQUEST_KEY_REUSED");
  }

  @Test
  void staleVersionsCannotOverwriteOrRegenerate() throws Exception {
    var j = draft();
    var b = factor(j, "D");
    ok(admin, "POST", "/factors", b);
    fail(admin, "POST", path(j, "generate"), cmd(j), 409, "STALE_VERSION");
  }

  @Test
  void concurrentGenerateHasOneWinner() throws Exception {
    var j = draft();
    var executor = Executors.newFixedThreadPool(2);
    try {
      var futures =
          executor.invokeAll(
              List.<Callable<Integer>>of(
                  () -> req(admin, "POST", path(j, "generate"), cmd(j)).getResponse().getStatus(),
                  () -> req(admin, "POST", path(j, "generate"), cmd(j)).getResponse().getStatus()));
      var statuses = new ArrayList<Integer>();
      for (var f : futures) statuses.add(f.get());
      Collections.sort(statuses);
      assertEquals(List.of(200, 409), statuses);
      assertEquals(1, current(j).path("revisions").size());
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void duplicatesUnknownFieldsAndMissingValuesRejected() throws Exception {
    var j = draft();
    fail(admin, "POST", "/factors", factor(j, "A"), 409, "DUPLICATE_FACTOR");
    var b = factor(j, "D");
    b.put("values", List.of("1", "1"));
    fail(admin, "POST", "/factors", b, 400, "INVALID_VALUES");
    b = factor(j, "D");
    b.put("status", "APPROVED");
    fail(admin, "POST", "/factors", b, 400, "INVALID_INPUT");
    b = factor(j, "D");
    b.remove("values");
    fail(admin, "POST", "/factors", b, 400, "INVALID_VALUES");
  }

  @Test
  void modelEditsInvalidateActivePlanButKeepHistory() throws Exception {
    var j = generated();
    long revision = j.path("activeRevisionId").asLong();
    j = ok(admin, "POST", "/factors", factor(j, "D")).path("job");
    assertEquals("DRAFT", j.path("status").asString());
    assertTrue(j.path("activeRevisionId").isNull());
    j = current(command(admin, j, "generate"));
    assertEquals(2, j.path("revisions").size());
    assertEquals(
        3, ok(admin, "GET", "/revisions/" + revision, null).path("plan").path("factors").size());
    assertEquals(4, j.path("plan").path("factors").size());
  }

  @Test
  void constraintsCannotReferenceAnotherPlan() throws Exception {
    var j = draft();
    var other = draft();
    fail(
        admin,
        "POST",
        "/constraints",
        rule(
            j,
            j.path("factors").get(0).path("id").asLong(),
            "0",
            other.path("factors").get(0).path("id").asLong(),
            "0"),
        400,
        "INVALID_CONSTRAINT");
  }

  @Test
  void referencedValuesRequireExplicitConstraintRemoval() throws Exception {
    var j = draft();
    long a = j.path("factors").get(0).path("id").asLong(),
        b = j.path("factors").get(1).path("id").asLong();
    j = ok(admin, "POST", "/constraints", rule(j, a, "0", b, "1")).path("job");
    var edit = factor(j, "A");
    edit.put("values", List.of("1", "2"));
    fail(admin, "PUT", "/factors/" + a, edit, 409, "VALUE_IN_USE");
    fail(admin, "POST", "/factors/" + a + "/delete", cmd(j), 409, "FACTOR_IN_USE");
  }

  @Test
  void constraintDirectionDuplicateRejected() throws Exception {
    var j = draft();
    long a = j.path("factors").get(0).path("id").asLong(),
        b = j.path("factors").get(1).path("id").asLong();
    j = ok(admin, "POST", "/constraints", rule(j, a, "0", b, "1")).path("job");
    fail(admin, "POST", "/constraints", rule(j, b, "1", a, "0"), 409, "DUPLICATE_CONSTRAINT");
    j = current(command(admin, j, "generate"));
    assertEquals(6, j.path("plan").path("validConfigurations").asInt());
    assertEquals(11, j.path("plan").path("pairs").size());
  }

  @Test
  void deletionReplayDoesNotRequireDeletedEntity() throws Exception {
    var j = draft();
    long a = j.path("factors").get(0).path("id").asLong();
    var b = cmd(j);
    var first = ok(admin, "POST", "/factors/" + a + "/delete", b);
    assertEquals(first, ok(admin, "POST", "/factors/" + a + "/delete", b));
    fail(outside, "POST", "/factors/" + a + "/delete", b, 403, "OUT_OF_SCOPE");
  }

  @Test
  void returnPlanAndReturnResultsKeepExplicitHistory() throws Exception {
    var j = command(admin, generated(), "submit");
    j = command(review, j, "return-plan");
    assertEquals("DRAFT", j.path("status").asString());
    assertEquals(1, current(j).path("revisions").size());
    j = running();
    j = recordAll(command(operator, j, "acknowledge"), "FAIL");
    j = report(j, "FINISHED");
    j = command(review, j, "return-result");
    assertTrue(j.path("reportHash").isNull());
    j = ok(operator, "POST", "/results", result(j, 1, "PASS")).path("job");
    j = report(j, "FINISHED");
    assertEquals("PASSED", command(review, j, "close").path("outcome").asString());
  }

  @Test
  void allScopeOperatorStillSeesOnlyAssignedPlans() throws Exception {
    var other = user(operatorRole, 1);
    var b = jobInput();
    b.put("operatorId", other.path("id").asLong());
    var j = ok(admin, "POST", "/jobs", b);
    fail(operator, "GET", "/jobs/" + j.path("id").asLong(), null, 403, "OUT_OF_SCOPE");
  }

  @Test
  void invalidCaseStatusAndMissingExceptionNoteRejected() throws Exception {
    var j = command(operator, running(), "acknowledge");
    var b = result(j, 99, "PASS");
    fail(operator, "POST", "/results", b, 400, "INVALID_RESULT");
    b = result(j, 1, "UNKNOWN");
    fail(operator, "POST", "/results", b, 400, "INVALID_RESULT");
    b = result(j, 1, "FAIL");
    b.put("note", "");
    fail(operator, "POST", "/results", b, 400, "INVALID_INPUT");
  }

  @Test
  void csvEscapesLeadingMultilineFormulaAndKeepsPending() throws Exception {
    var j = draft();
    var f = j.path("factors").get(0);
    var b = factor(j, "A");
    b.put("values", List.of("=SUM(1,2)", "safe"));
    j = ok(admin, "PUT", "/factors/" + f.path("id").asLong(), b).path("job");
    j = command(admin, j, "generate");
    var csv =
        req(admin, "GET", "/jobs/" + j.path("id").asLong() + "/cases.csv", null)
            .getResponse()
            .getContentAsString();
    assertTrue(csv.startsWith("\uFEFFcase_number"));
    assertTrue(csv.contains("'="));
    assertTrue(csv.contains("PENDING"));
    j = command(admin, j, "submit");
    j = command(review, j, "approve");
    j = command(admin, j, "start");
    j = command(operator, j, "acknowledge");
    b = result(j, 1, "FAIL");
    b.put("note", "\u2003\n=HYPERLINK(\"unsafe\")");
    j = ok(operator, "POST", "/results", b).path("job");
    csv =
        req(admin, "GET", "/jobs/" + j.path("id").asLong() + "/cases.csv", null)
            .getResponse()
            .getContentAsString();
    assertTrue(csv.contains("\"'\u2003\n=HYPERLINK"));
  }

  @Test
  void cancelAllowedBeforeExecutionAndNotAfter() throws Exception {
    var j = generated();
    assertEquals("CANCELLED", command(admin, j, "cancel").path("status").asString());
    j = running();
    fail(admin, "POST", path(j, "cancel"), cmd(j), 409, "INVALID_STATE");
  }

  @Test
  void lastAdministratorAndPaginationBoundsEnforced() throws Exception {
    fail(
        admin,
        "PUT",
        "/admin/roles/1",
        m(
            "name",
            "管理员",
            "scope",
            "DEPARTMENT",
            "permissions",
            List.of(
                "job.read",
                "job.write",
                "job.review",
                "test.write",
                "dashboard",
                "export",
                "audit",
                "admin")),
        409,
        "LAST_ADMIN");
    fail(admin, "GET", "/jobs?size=51", null, 400, "INVALID_INPUT");
    var j = draft();
    assertTrue(
        ok(admin, "GET", "/jobs?search=" + j.path("reference").asString(), null)
                .path("total")
                .asInt()
            >= 1);
  }

  @Test
  void liveRevocationPrecedesCachedResult() throws Exception {
    var j = command(operator, running(), "acknowledge");
    var b = result(j, 1, "PASS");
    ok(operator, "POST", "/results", b);
    String path = "/admin/roles/" + operatorRole;
    String name =
        sql.queryForObject("select name from access_role where id=?", String.class, operatorRole);
    try {
      ok(
          admin,
          "PUT",
          path,
          m(
              "name",
              name,
              "scope",
              "ALL",
              "permissions",
              List.of("job.read", "dashboard", "export")));
      fail(operator, "POST", "/results", b, 403, "FORBIDDEN");
    } finally {
      ok(
          admin,
          "PUT",
          path,
          m(
              "name",
              name,
              "scope",
              "ALL",
              "permissions",
              List.of("job.read", "test.write", "dashboard", "export")));
    }
  }

  @Test
  void actualRevisionPreservesPreviousEvidence() throws Exception {
    var j = command(operator, running(), "acknowledge");
    var b = result(j, 1, "FAIL");
    b.put("note", "TEST原始失败现象");
    b.put("evidence", "TEST证据A");
    j = ok(operator, "POST", "/results", b).path("job");
    b = result(j, 1, "PASS");
    b.put("note", "TEST复测通过");
    j = ok(operator, "POST", "/results", b).path("job");
    var d = current(j);
    assertEquals(1, d.path("results").size());
    var journal = json.readTree(d.path("history").get(0).path("snapshot").asString());
    assertEquals("FAIL", journal.path("previousResult").path("status").asString());
    assertEquals("TEST证据A", journal.path("previousResult").path("evidence").asString());
    assertEquals("PASS", journal.path("result").path("status").asString());
  }
}

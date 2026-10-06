// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.configpair;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

/** 独立完整枚举检查合法性、可达覆盖和实际覆盖；不按实现返回值自证。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class PairEngineTest {
  private PairEngine.Factor f(String code, String... values) {
    return new PairEngine.Factor(code, code, List.of(values));
  }

  private List<PairEngine.Factor> three() {
    return List.of(f("A", "0", "1"), f("B", "0", "1"), f("C", "0", "1"));
  }

  private PairEngine.Forbidden ban(String a, String av, String b, String bv) {
    return new PairEngine.Forbidden(a, av, b, bv);
  }

  private Set<List<String>> oraclePairs(Map<String, String> cfg, List<PairEngine.Factor> fs) {
    Set<List<String>> out = new HashSet<>();
    for (int i = 0; i < fs.size(); i++)
      for (int j = i + 1; j < fs.size(); j++)
        out.add(
            List.of(
                fs.get(i).code(),
                cfg.get(fs.get(i).code()),
                fs.get(j).code(),
                cfg.get(fs.get(j).code())));
    return out;
  }

  private List<Map<String, String>> exhaustive(
      List<PairEngine.Factor> fs, List<PairEngine.Forbidden> bans) {
    List<Map<String, String>> all = new ArrayList<>();
    int count = fs.stream().mapToInt(x -> x.values().size()).reduce(1, (a, b) -> a * b);
    for (int n = 0; n < count; n++) {
      int cursor = n;
      Map<String, String> row = new LinkedHashMap<>();
      for (var f : fs) {
        row.put(f.code(), f.values().get(cursor % f.values().size()));
        cursor /= f.values().size();
      }
      if (bans.stream()
          .noneMatch(
              b ->
                  row.get(b.leftFactor()).equals(b.leftValue())
                      && row.get(b.rightFactor()).equals(b.rightValue()))) all.add(row);
    }
    return all;
  }

  private void verify(List<PairEngine.Factor> fs, List<PairEngine.Forbidden> bans) {
    var valid = exhaustive(fs, bans);
    if (valid.isEmpty()) {
      assertThrows(Problem.class, () -> PairEngine.generate(fs, bans));
      return;
    }
    var p = PairEngine.generate(fs, bans);
    assertEquals(valid.size(), p.validConfigurations());
    assertEquals(p.totalConfigurations(), p.validConfigurations() + p.rejectedConfigurations());
    Set<List<String>> universe = new HashSet<>(), selected = new HashSet<>();
    for (var cfg : valid) universe.addAll(oraclePairs(cfg, fs));
    assertEquals(
        universe,
        new HashSet<>(
            p.pairs().stream()
                .map(x -> List.of(x.leftFactor(), x.leftValue(), x.rightFactor(), x.rightValue()))
                .toList()));
    Set<Map<String, String>> distinct = new HashSet<>();
    int number = 0;
    for (var c : p.cases()) {
      assertEquals(++number, c.number());
      Map<String, String> cfg = new LinkedHashMap<>();
      for (var s : c.selections()) assertNull(cfg.put(s.factor(), s.value()));
      assertEquals(fs.size(), cfg.size());
      assertTrue(valid.contains(cfg));
      assertTrue(distinct.add(cfg));
      Set<List<String>> actual = oraclePairs(cfg, fs);
      selected.addAll(actual);
      var referenced =
          new HashSet<>(
              c.pairIndexes().stream()
                  .map(
                      i -> {
                        var x = p.pairs().get(i);
                        return List.of(
                            x.leftFactor(), x.leftValue(), x.rightFactor(), x.rightValue());
                      })
                  .toList());
      assertEquals(actual, referenced);
    }
    assertEquals(universe, selected);
    assertEquals(p.possiblePairs(), p.pairs().size() + p.excludedPairs());
    Set<List<String>> unreachable = new HashSet<>();
    for (var f : fs)
      for (String v : f.values())
        if (valid.stream().noneMatch(row -> v.equals(row.get(f.code()))))
          unreachable.add(List.of(f.code(), v));
    assertEquals(
        unreachable,
        new HashSet<>(
            p.unreachableValues().stream().map(x -> List.of(x.factor(), x.value())).toList()));
  }

  @Test
  void threeBinaryCoverAllTwelvePairs() {
    verify(three(), List.of());
    var p = PairEngine.generate(three(), List.of());
    assertEquals(8, p.validConfigurations());
    assertEquals(12, p.pairs().size());
    assertEquals(4, p.cases().size());
  }

  @Test
  void twoFactorsNeedEachLegalConfiguration() {
    var fs = List.of(f("A", "a", "b", "c"), f("B", "x", "y"));
    verify(fs, List.of(ban("A", "c", "B", "y")));
    assertEquals(5, PairEngine.generate(fs, List.of(ban("A", "c", "B", "y"))).cases().size());
  }

  @Test
  void constraintsRemoveOnlyUnreachablePairs() {
    verify(three(), List.of(ban("A", "0", "B", "1"), ban("B", "0", "C", "1")));
  }

  @Test
  void unreachableValuesReportedExplicitly() {
    var p = PairEngine.generate(three(), List.of(ban("A", "1", "B", "0"), ban("A", "1", "B", "1")));
    assertEquals(List.of(new PairEngine.Selection("A", "1")), p.unreachableValues());
    verify(three(), p.constraints());
  }

  @Test
  void unsatisfiableModelIsRejected() {
    var fs = List.of(f("A", "0", "1"), f("B", "0", "1"));
    List<PairEngine.Forbidden> rules = new ArrayList<>();
    for (String a : List.of("0", "1"))
      for (String b : List.of("0", "1")) rules.add(ban("A", a, "B", b));
    verify(fs, rules);
  }

  @Test
  void deterministicForIdenticalModel() {
    assertEquals(PairEngine.generate(three(), List.of()), PairEngine.generate(three(), List.of()));
  }

  @Test
  void reversedRuleHasSameValidity() {
    var a = PairEngine.generate(three(), List.of(ban("A", "0", "B", "1")));
    var b = PairEngine.generate(three(), List.of(ban("B", "1", "A", "0")));
    assertEquals(a.cases(), b.cases());
  }

  @Test
  void duplicateAndUnknownRulesRejected() {
    assertThrows(
        Problem.class,
        () ->
            PairEngine.generate(
                three(), List.of(ban("A", "0", "B", "0"), ban("B", "0", "A", "0"))));
    assertThrows(
        Problem.class, () -> PairEngine.generate(three(), List.of(ban("A", "0", "Z", "0"))));
    assertThrows(
        Problem.class, () -> PairEngine.generate(three(), List.of(ban("A", "0", "A", "1"))));
    assertThrows(
        Problem.class, () -> PairEngine.generate(three(), List.of(ban("A", "missing", "B", "0"))));
  }

  @Test
  void missingDuplicateAndOversizedValuesRejected() {
    assertThrows(
        Problem.class,
        () -> PairEngine.generate(List.of(f("A", "0", "0"), f("B", "0", "1")), List.of()));
    assertThrows(
        Problem.class,
        () -> PairEngine.generate(List.of(f("A", "0"), f("B", "0", "1")), List.of()));
    assertThrows(
        Problem.class,
        () -> PairEngine.generate(List.of(f("A", "0", "1"), f("A", "0", "1")), List.of()));
  }

  @Test
  void rawSpaceLimitAppliesBeforeConstraints() {
    var fs = new ArrayList<PairEngine.Factor>();
    for (int i = 0; i < 5; i++) fs.add(f("F" + i, "0", "1", "2", "3", "4", "5", "6", "7"));
    assertThrows(Problem.class, () -> PairEngine.generate(fs, List.of()));
  }

  @Test
  void valuesWithSeparatorsDoNotCollide() {
    verify(List.of(f("A", "a|b", "a"), f("B", "c", "b|c"), f("C", "=formula", "普通值")), List.of());
  }

  @Test
  void pendingAndBlockedNeverCountAsExecuted() {
    var p = PairEngine.generate(three(), List.of());
    var c = PairEngine.measure(p, List.of(new PairEngine.Execution(1, "BLOCKED")));
    assertEquals(1, c.blocked());
    assertEquals(3, c.pending());
    assertEquals(0, c.executedPairs());
    assertEquals(12, c.unexecutedPairs().size());
  }

  @Test
  void passAndFailureHaveSeparateCoverage() {
    var p = PairEngine.generate(three(), List.of());
    var c =
        PairEngine.measure(
            p, List.of(new PairEngine.Execution(1, "PASS"), new PairEngine.Execution(2, "FAIL")));
    assertEquals(1, c.passed());
    assertEquals(1, c.failed());
    assertEquals(3, c.passedPairs());
    assertEquals(3, c.failedPairs());
    assertTrue(c.executedPairs() <= 6);
    assertEquals(p.pairs().size() - c.executedPairs(), c.unexecutedPairs().size());
  }

  @Test
  void fullExecutionCoversFeasibleUniverse() {
    var p = PairEngine.generate(three(), List.of());
    var c =
        PairEngine.measure(
            p, p.cases().stream().map(x -> new PairEngine.Execution(x.number(), "PASS")).toList());
    assertEquals(12, c.executedPairs());
    assertEquals(12, c.passedPairs());
    assertEquals("100.00", c.executedPercent().toPlainString());
    assertEquals(0, c.pending());
  }

  @Test
  void invalidOrDuplicateExecutionRejected() {
    var p = PairEngine.generate(three(), List.of());
    assertThrows(
        Problem.class, () -> PairEngine.measure(p, List.of(new PairEngine.Execution(0, "PASS"))));
    assertThrows(
        Problem.class, () -> PairEngine.measure(p, List.of(new PairEngine.Execution(1, null))));
    assertThrows(
        Problem.class,
        () ->
            PairEngine.measure(
                p,
                List.of(new PairEngine.Execution(1, "PASS"), new PairEngine.Execution(1, "FAIL"))));
  }

  @Test
  void eightyIndependentSeededModels() {
    Random r = new Random(20261006);
    for (int n = 0; n < 80; n++) {
      List<PairEngine.Factor> fs = new ArrayList<>();
      int count = 2 + r.nextInt(4);
      for (int i = 0; i < count; i++)
        fs.add(r.nextBoolean() ? f("F" + i, "0", "1") : f("F" + i, "0", "1", "2"));
      Set<PairEngine.Forbidden> bans = new LinkedHashSet<>();
      for (int k = 0; k < 8; k++) {
        int a = r.nextInt(count - 1), b = a + 1 + r.nextInt(count - a - 1);
        bans.add(
            ban(
                fs.get(a).code(),
                fs.get(a).values().get(r.nextInt(fs.get(a).values().size())),
                fs.get(b).code(),
                fs.get(b).values().get(r.nextInt(fs.get(b).values().size()))));
      }
      verify(fs, List.copyOf(bans));
    }
  }

  @Test
  void exactTwentyThousandBoundProducesCompleteCoverage() {
    var fs =
        List.of(
            f("A", "0", "1", "2", "3", "4"),
            f("B", "0", "1", "2", "3", "4"),
            f("C", "0", "1", "2", "3", "4"),
            f("D", "0", "1", "2", "3", "4"),
            f("E", "0", "1", "2", "3"),
            f("F", "0", "1", "2", "3", "4", "5", "6", "7"));
    var p = PairEngine.generate(fs, List.of());
    assertEquals(20000, p.validConfigurations());
    assertTrue(p.cases().size() <= 256);
    assertEquals(
        p.pairs().size(),
        PairEngine.measure(
                p,
                p.cases().stream().map(c -> new PairEngine.Execution(c.number(), "PASS")).toList())
            .executedPairs());
  }
}

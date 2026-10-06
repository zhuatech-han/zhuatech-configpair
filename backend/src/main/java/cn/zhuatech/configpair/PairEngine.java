// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.configpair;

import java.math.*;
import java.util.*;

/** 有界有效配置枚举与可达值对贪心覆盖；不承诺最优或缺陷检出。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class PairEngine {
  public static final String VERSION = "FEASIBLE-PAIR-GREEDY-1";

  private PairEngine() {}

  /** 明确的离散参数值；顺序决定稳定决胜。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Factor(String code, String name, List<String> values) {}

  /** 不允许同时出现的两个不同参数值。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Forbidden(
      String leftFactor, String leftValue, String rightFactor, String rightValue) {}

  /** 可达值对，不把不可能配置算作待测覆盖。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Pair(String leftFactor, String leftValue, String rightFactor, String rightValue) {}

  /** 一项参数及其明确值。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Selection(String factor, String value) {}

  /** 生成用例及其覆盖的可达值对索引。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record TestCase(int number, List<Selection> selections, List<Integer> pairIndexes) {}

  /** 可复算输入、合法空间与生成覆盖事实。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Plan(
      String algorithm,
      List<Factor> factors,
      List<Forbidden> constraints,
      int totalConfigurations,
      int validConfigurations,
      int rejectedConfigurations,
      int possiblePairs,
      int excludedPairs,
      List<Pair> pairs,
      List<Selection> unreachableValues,
      List<TestCase> cases) {}

  /** 人工执行结果，不接受默认通过。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Execution(int caseNumber, String status) {}

  /** 计划、实际执行、通过覆盖分别计算，BLOCKED不算执行。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Coverage(
      int cases,
      int passed,
      int failed,
      int blocked,
      int pending,
      int feasiblePairs,
      int executedPairs,
      int passedPairs,
      int failedPairs,
      BigDecimal executedPercent,
      BigDecimal passedPercent,
      List<Pair> unexecutedPairs) {}

  private record Rule(int left, int leftValue, int right, int rightValue) {}

  private static void valid(boolean ok, String code) {
    if (!ok) throw new Problem(400, code);
  }

  private static boolean literal(String s, int max) {
    return s != null && !s.isBlank() && s.length() <= max && s.equals(s.trim());
  }

  /** 先穷举受限空间，再贪心选出每次新增覆盖最多的合法配置，等值保留原顺序。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static Plan generate(List<Factor> input, List<Forbidden> banned) {
    valid(
        input != null
            && input.size() >= 2
            && input.size() <= 8
            && banned != null
            && banned.size() <= 80,
        "INVALID_MODEL");
    List<Factor> factors = new ArrayList<>();
    Map<String, Integer> positions = new LinkedHashMap<>();
    int product = 1;
    for (Factor f : input) {
      valid(
          f != null
              && f.code() != null
              && f.code().matches("[A-Z][A-Z0-9_-]{0,29}")
              && literal(f.name(), 160)
              && f.values() != null
              && f.values().size() >= 2
              && f.values().size() <= 8,
          "INVALID_FACTOR");
      valid(!positions.containsKey(f.code()), "DUPLICATE_FACTOR");
      valid(
          f.values().stream()
                  .allMatch(v -> literal(v, 60) && v.chars().noneMatch(Character::isISOControl))
              && new HashSet<>(f.values()).size() == f.values().size(),
          "INVALID_VALUES");
      positions.put(f.code(), factors.size());
      factors.add(new Factor(f.code(), f.name(), List.copyOf(f.values())));
      product *= f.values().size();
      valid(product <= 20000, "SPACE_LIMIT");
    }
    List<Rule> rules = new ArrayList<>();
    Set<Rule> unique = new HashSet<>();
    for (Forbidden r : banned) {
      valid(
          r != null
              && positions.containsKey(r.leftFactor())
              && positions.containsKey(r.rightFactor()),
          "INVALID_CONSTRAINT");
      int a = positions.get(r.leftFactor()), b = positions.get(r.rightFactor());
      int va = factors.get(a).values().indexOf(r.leftValue()),
          vb = factors.get(b).values().indexOf(r.rightValue());
      valid(a != b && va >= 0 && vb >= 0, "INVALID_CONSTRAINT");
      Rule rule = a < b ? new Rule(a, va, b, vb) : new Rule(b, vb, a, va);
      valid(unique.add(rule), "DUPLICATE_CONSTRAINT");
      rules.add(rule);
    }
    List<int[]> configs = new ArrayList<>();
    enumerate(factors, rules, new int[factors.size()], 0, configs);
    valid(!configs.isEmpty(), "NO_VALID_CONFIGURATION");
    Map<Pair, Integer> universe = new LinkedHashMap<>();
    boolean[][] reached = new boolean[factors.size()][8];
    for (int[] cfg : configs) {
      for (int i = 0; i < cfg.length; i++) reached[i][cfg[i]] = true;
      for (Pair p : pairs(factors, cfg)) universe.computeIfAbsent(p, k -> universe.size());
    }
    List<Pair> pairList = List.copyOf(universe.keySet());
    List<BitSet> sets = new ArrayList<>();
    for (int[] cfg : configs) {
      BitSet bits = new BitSet(pairList.size());
      for (Pair p : pairs(factors, cfg)) bits.set(universe.get(p));
      sets.add(bits);
    }
    BitSet uncovered = new BitSet(pairList.size());
    uncovered.set(0, pairList.size());
    List<TestCase> cases = new ArrayList<>();
    while (!uncovered.isEmpty()) {
      int best = -1, gain = 0;
      for (int i = 0; i < sets.size(); i++) {
        BitSet fresh = (BitSet) sets.get(i).clone();
        fresh.and(uncovered);
        int n = fresh.cardinality();
        if (n > gain) {
          gain = n;
          best = i;
        }
      }
      if (best < 0) throw new IllegalStateException("Unreachable coverage invariant");
      valid(cases.size() < 256, "CASE_LIMIT");
      int[] cfg = configs.get(best);
      List<Selection> choices = new ArrayList<>();
      for (int i = 0; i < cfg.length; i++)
        choices.add(new Selection(factors.get(i).code(), factors.get(i).values().get(cfg[i])));
      cases.add(
          new TestCase(
              cases.size() + 1, List.copyOf(choices), sets.get(best).stream().boxed().toList()));
      uncovered.andNot(sets.get(best));
    }
    int possible = 0;
    List<Selection> unreachable = new ArrayList<>();
    for (int i = 0; i < factors.size(); i++) {
      for (int v = 0; v < factors.get(i).values().size(); v++)
        if (!reached[i][v])
          unreachable.add(new Selection(factors.get(i).code(), factors.get(i).values().get(v)));
      for (int j = i + 1; j < factors.size(); j++)
        possible += factors.get(i).values().size() * factors.get(j).values().size();
    }
    return new Plan(
        VERSION,
        List.copyOf(factors),
        List.copyOf(banned),
        product,
        configs.size(),
        product - configs.size(),
        possible,
        possible - pairList.size(),
        pairList,
        List.copyOf(unreachable),
        List.copyOf(cases));
  }

  private static void enumerate(
      List<Factor> factors, List<Rule> rules, int[] cfg, int at, List<int[]> out) {
    if (at == factors.size()) {
      out.add(cfg.clone());
      return;
    }
    for (int v = 0; v < factors.get(at).values().size(); v++) {
      cfg[at] = v;
      boolean forbidden = false;
      for (Rule r : rules)
        if (r.right() == at && cfg[r.left()] == r.leftValue() && v == r.rightValue()) {
          forbidden = true;
          break;
        }
      if (!forbidden) enumerate(factors, rules, cfg, at + 1, out);
    }
  }

  private static List<Pair> pairs(List<Factor> factors, int[] cfg) {
    List<Pair> out = new ArrayList<>();
    for (int a = 0; a < cfg.length; a++)
      for (int b = a + 1; b < cfg.length; b++)
        out.add(
            new Pair(
                factors.get(a).code(),
                factors.get(a).values().get(cfg[a]),
                factors.get(b).code(),
                factors.get(b).values().get(cfg[b])));
    return out;
  }

  private static BigDecimal percent(int numerator, int denominator) {
    return BigDecimal.valueOf(numerator)
        .multiply(BigDecimal.valueOf(100))
        .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
  }

  /**
   * 按当前人工最终结果测量配置值对覆盖；仅PASS／FAIL计入执行，阻塞与待测独立保留。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。
   */
  public static Coverage measure(Plan plan, List<Execution> executions) {
    valid(plan != null && !plan.pairs().isEmpty() && executions != null, "INVALID_PLAN");
    Set<Integer> seen = new HashSet<>();
    BitSet done = new BitSet(), passed = new BitSet(), failed = new BitSet();
    int pass = 0, fail = 0, block = 0;
    for (Execution e : executions) {
      valid(
          e != null
              && e.caseNumber() >= 1
              && e.caseNumber() <= plan.cases().size()
              && seen.add(e.caseNumber())
              && e.status() != null
              && Set.of("PASS", "FAIL", "BLOCKED").contains(e.status()),
          "INVALID_RESULT");
      TestCase c = plan.cases().get(e.caseNumber() - 1);
      if (e.status().equals("BLOCKED")) {
        block++;
        continue;
      }
      if (e.status().equals("PASS")) pass++;
      else fail++;
      for (int p : c.pairIndexes()) {
        done.set(p);
        if (e.status().equals("PASS")) passed.set(p);
        else failed.set(p);
      }
    }
    List<Pair> missed = new ArrayList<>();
    for (int i = 0; i < plan.pairs().size(); i++) if (!done.get(i)) missed.add(plan.pairs().get(i));
    return new Coverage(
        plan.cases().size(),
        pass,
        fail,
        block,
        plan.cases().size() - seen.size(),
        plan.pairs().size(),
        done.cardinality(),
        passed.cardinality(),
        failed.cardinality(),
        percent(done.cardinality(), plan.pairs().size()),
        percent(passed.cardinality(), plan.pairs().size()),
        List.copyOf(missed));
  }
}

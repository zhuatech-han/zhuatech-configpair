// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
export const states = {
  DRAFT: ["草稿", "Draft"],
  GENERATED: ["已生成", "Generated"],
  SUBMITTED: ["计划待复核", "Plan review"],
  APPROVED: ["计划已批准", "Approved"],
  RUNNING: ["执行登记中", "Recording tests"],
  REVIEW: ["报告待复核", "Report review"],
  CLOSED: ["已封存", "Closed"],
  CANCELLED: ["已取消", "Cancelled"],
  PASS: ["通过", "Passed"],
  FAIL: ["失败", "Failed"],
  BLOCKED: ["阻塞", "Blocked"],
  PENDING: ["未登记", "Pending"],
  PASSED: ["逐例通过", "All cases passed"],
  ISSUES: ["存在失败", "Failures reported"],
  PARTIAL: ["存在阻塞", "Blocked cases remain"],
};
export const actionNames = {
  generate: ["生成测试组合", "Generate combinations"],
  submit: ["提交计划复核", "Submit plan"],
  approve: ["批准并冻结计划", "Approve & freeze"],
  "return-plan": ["退回计划", "Return plan"],
  start: ["开启执行登记", "Open test recording"],
  cancel: ["取消计划", "Cancel campaign"],
  acknowledge: ["确认收悉计划", "Acknowledge plan"],
  "submit-report": ["提交执行报告", "Submit test report"],
  "return-result": ["退回报告修订", "Return report"],
  close: ["复核并封存", "Review & close"],
};
/** 可见操作遵循岗位与状态，后端重新执行权限检查。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function actions(j) {
  if (!j) return [];
  const a = [];
  if (j.canWrite) {
    if (["DRAFT", "GENERATED"].includes(j.status)) a.push("generate");
    if (j.status === "GENERATED") a.push("submit");
    if (j.status === "APPROVED") a.push("start");
    if (["DRAFT", "GENERATED", "SUBMITTED", "APPROVED"].includes(j.status))
      a.push("cancel");
  }
  if (j.canReview && j.status === "SUBMITTED") a.push("approve", "return-plan");
  if (j.canReview && j.status === "REVIEW") a.push("close", "return-result");
  if (j.canOperate && j.status === "RUNNING")
    a.push(j.acknowledgedAt ? "submit-report" : "acknowledge");
  return a;
}
/** 逐行显式参数值，拒绝重复、空行和超出界限。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function parseValues(raw) {
  const values = String(raw ?? "")
    .split(/\r?\n/)
    .map((s) => s.trim());
  if (
    values.length < 2 ||
    values.length > 8 ||
    values.some((s) => !s || s.length > 60) ||
    new Set(values).size !== values.length
  )
    throw new Error("INVALID_VALUES");
  return values;
}
/** 有限表单字段，不把系统摘要、状态或实体内部字段发回。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function payload(form, definitions) {
  return Object.fromEntries(
    definitions.map(([key, , , type]) => {
      let v = form[key];
      if (["id", "integer"].includes(type)) {
        const s = String(v ?? "").trim();
        if (!/^\d+$/.test(s) || !Number.isSafeInteger(Number(s)))
          throw new Error("INVALID_INPUT");
        v = Number(s);
      } else if (type === "boolean") v = Boolean(v);
      else if (type === "values") v = parseValues(v);
      return [key, v];
    }),
  );
}
/** 选定两个参数下的值对索引，支持交换坐标轴，不拼接可能碰撞的字符串。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function pairIndex(plan, left, leftValue, right, rightValue) {
  if (!plan || left === right) return -1;
  return plan.pairs.findIndex(
    (p) =>
      (p.leftFactor === left &&
        p.leftValue === leftValue &&
        p.rightFactor === right &&
        p.rightValue === rightValue) ||
      (p.leftFactor === right &&
        p.leftValue === rightValue &&
        p.rightFactor === left &&
        p.rightValue === leftValue),
  );
}
/** 人工通过／失败用例才进入执行集合；历史快照不冒用当前结果。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function coveredPairs(plan, results, mode) {
  const out = new Set();
  if (!plan) return out;
  if (mode === "planned") return new Set(plan.pairs.map((_, i) => i));
  for (const r of results || []) {
    if (
      mode === "passed"
        ? r.status !== "PASS"
        : !["PASS", "FAIL"].includes(r.status)
    )
      continue;
    for (const i of plan.cases.find((c) => c.number === r.caseNumber)
      ?.pairIndexes || [])
      out.add(i);
  }
  return out;
}

// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import test from "node:test";
import assert from "node:assert/strict";
import {
  actions,
  parseValues,
  payload,
  pairIndex,
  coveredPairs,
} from "./domain.js";
test("writer actions follow draft and approved state", () => {
  assert.deepEqual(actions({ status: "DRAFT", canWrite: true }), [
    "generate",
    "cancel",
  ]);
  assert.deepEqual(actions({ status: "APPROVED", canWrite: true }), [
    "start",
    "cancel",
  ]);
});
test("independent reviewer and assigned runner actions", () => {
  assert.deepEqual(actions({ status: "SUBMITTED", canReview: true }), [
    "approve",
    "return-plan",
  ]);
  assert.deepEqual(
    actions({ status: "RUNNING", canOperate: true, acknowledgedAt: "now" }),
    ["submit-report"],
  );
});
test("closed records expose no mutation", () =>
  assert.deepEqual(
    actions({
      status: "CLOSED",
      canWrite: true,
      canReview: true,
      canOperate: true,
    }),
    [],
  ));
test("values preserve punctuation and trim boundaries", () =>
  assert.deepEqual(parseValues("a|b\r\n普通值"), ["a|b", "普通值"]));
test("empty duplicate and single values rejected", () => {
  for (const s of ["a", "a\n", "a\na", "\na\nb"])
    assert.throws(() => parseValues(s));
});
test("too many values rejected", () =>
  assert.throws(() =>
    parseValues(Array.from({ length: 9 }, (_, i) => String(i)).join("\n")),
  ));
test("finite fields omit status and hashes", () =>
  assert.deepEqual(
    payload({ name: "N", status: "CLOSED", closedHash: "private" }, [
      ["name", "", "", "text"],
    ]),
    { name: "N" },
  ));
test("integer fields reject missing and fractional identifiers", () => {
  for (const id of ["", null, 1.2, "9007199254740992"])
    assert.throws(() => payload({ id }, [["id", "", "", "id"]]));
});
test("zero order is preserved", () =>
  assert.equal(
    payload({ position: 0 }, [["position", "", "", "integer"]]).position,
    0,
  ));
const plan = {
  pairs: [
    { leftFactor: "A", leftValue: "a|b", rightFactor: "B", rightValue: "c" },
  ],
  cases: [{ number: 1, pairIndexes: [0] }],
};
test("pair axes can be reversed without string collision", () => {
  assert.equal(pairIndex(plan, "B", "c", "A", "a|b"), 0);
  assert.equal(pairIndex(plan, "A", "a", "B", "b|c"), -1);
});
test("blocked and pending do not contribute executed coverage", () => {
  assert.equal(
    coveredPairs(plan, [{ caseNumber: 1, status: "BLOCKED" }], "executed").size,
    0,
  );
  assert.equal(coveredPairs(plan, [], "executed").size, 0);
});
test("failed coverage contributes only to execution", () => {
  assert.equal(
    coveredPairs(plan, [{ caseNumber: 1, status: "FAIL" }], "executed").size,
    1,
  );
  assert.equal(
    coveredPairs(plan, [{ caseNumber: 1, status: "FAIL" }], "passed").size,
    0,
  );
  assert.equal(coveredPairs(plan, [], "planned").size, 1);
});

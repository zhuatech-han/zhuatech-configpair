<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted } from "vue";
import {
  Layers,
  BarChart3,
  Users,
  ShieldCheck,
  Settings,
  LogOut,
  Plus,
  Search,
  ArrowRight,
  ChevronLeft,
  ChevronRight,
  X,
  Download,
  RefreshCw,
  Clock3,
  ExternalLink,
} from "@lucide/vue";
import { api, resetCsrf } from "./api.js";
import {
  actions,
  states,
  actionNames,
  payload,
  pairIndex,
  coveredPairs,
} from "./domain.js";
import { fields } from "./forms.js";
const lang = ref(localStorage.getItem("configpair-language") || "zh"),
  me = ref(null),
  view = ref("jobs"),
  busy = ref(false),
  error = ref(""),
  notice = ref(""),
  loginForm = ref({ username: "", password: "" }),
  options = ref({}),
  directories = ref({}),
  rows = ref([]),
  total = ref(0),
  page = ref(0),
  search = ref(""),
  filter = ref(""),
  sort = ref("newest"),
  detail = ref(null),
  stats = ref({}),
  modal = ref(null),
  form = ref({}),
  contact = ref(false),
  tab = ref("coverage"),
  oldRevision = ref(null),
  casePage = ref(0),
  leftAxis = ref(0),
  rightAxis = ref(1),
  matrixMode = ref("executed");
const t = (zh, en) => (lang.value === "zh" ? zh : en);
const can = (p) => me.value?.permissions?.includes(p);
const adminTypes = [
  "users",
  "roles",
  "departments",
  "menus",
  "permissions",
  "dictionaries",
  "settings",
];
const labels = {
  jobs: ["组合测试计划", "Test campaigns"],
  dashboard: ["覆盖统计", "Coverage statistics"],
  audit: ["操作审计", "Audit"],
  users: ["账号管理", "Accounts"],
  roles: ["角色与权限", "Roles"],
  departments: ["部门管理", "Departments"],
  menus: ["导航管理", "Navigation"],
  permissions: ["权限目录", "Permissions"],
  dictionaries: ["系统类型", "System types"],
  settings: ["系统参数", "Settings"],
  factors: ["参数", "Factors"],
  constraints: ["禁配规则", "Forbidden pairs"],
  results: ["执行结果", "Test results"],
};
const icons = {
  jobs: Layers,
  dashboard: BarChart3,
  audit: Clock3,
  users: Users,
  roles: ShieldCheck,
};
const title = computed(() =>
  t(...(labels[view.value] || ["ConfigPair", "ConfigPair"])),
);
const state = (s) => t(...(states[s] || [s || "—", s || "—"]));
const actionName = (a) => t(...actionNames[a]);
const editable = computed(
  () =>
    !oldRevision.value &&
    detail.value?.canWrite &&
    ["DRAFT", "GENERATED"].includes(detail.value.status),
);
const currentActions = computed(() =>
  oldRevision.value ? [] : actions(detail.value),
);
const plan = computed(() => oldRevision.value?.plan || detail.value?.plan);
const activeResults = computed(() =>
  oldRevision.value ? [] : detail.value?.results || [],
);
const coverageSet = computed(() =>
  coveredPairs(
    plan.value,
    activeResults.value,
    oldRevision.value ? "planned" : matrixMode.value,
  ),
);
const modelFactors = computed(
  () => oldRevision.value?.input.factors || detail.value?.factors || [],
);
const modelRules = computed(
  () => oldRevision.value?.input.constraints || detail.value?.constraints || [],
);
const campaignData = computed(() => oldRevision.value?.input || detail.value);
const leftFactor = computed(() => plan.value?.factors[leftAxis.value]);
const rightFactor = computed(() => plan.value?.factors[rightAxis.value]);
const visibleCases = computed(
  () =>
    plan.value?.cases.slice(casePage.value * 12, casePage.value * 12 + 12) ||
    [],
);
const failures = {
  UNAUTHENTICATED: ["登录已失效，请重新登录", "Session expired"],
  FORBIDDEN: ["当前岗位无此权限", "Permission required"],
  OUT_OF_SCOPE: ["超出账号授权范围", "Outside your scope"],
  FROZEN: ["计划或结果已经冻结", "Record frozen"],
  STALE_VERSION: ["记录已变化，请刷新后重试", "Record changed; refresh first"],
  INDEPENDENT_REVIEW_REQUIRED: [
    "复核人和执行人须与所有计划编辑人相互独立",
    "Reviewer and tester must be independent of every editor",
  ],
  ASSIGNED_OPERATOR_ONLY: [
    "须由指定测试执行人操作",
    "Assigned tester required",
  ],
  ASSIGNED_ACCOUNT_UNAVAILABLE: [
    "指定账号已停用、部门不符或权限不足",
    "Assigned account unavailable",
  ],
  INVALID_MODEL: [
    "需要2至8个参数及明确候选值",
    "Use 2–8 factors with explicit values",
  ],
  INVALID_FACTOR: [
    "参数编码使用大写字母、数字、下划线或短横线",
    "Use an uppercase factor code",
  ],
  INVALID_VALUES: [
    "每个参数须有2至8个不重复非空值",
    "Use 2–8 distinct nonempty values",
  ],
  SPACE_LIMIT: [
    "原始参数组合超过20000，请缩小候选空间",
    "Raw configuration space exceeds 20,000",
  ],
  NO_VALID_CONFIGURATION: [
    "禁配规则排除了所有配置，请修订模型",
    "No valid configurations; revise constraints",
  ],
  CASE_LIMIT: [
    "生成超过256用例，请缩小模型；结果未截断",
    "More than 256 cases; simplify the model",
  ],
  DUPLICATE_FACTOR: ["参数编码已存在", "Duplicate factor code"],
  DUPLICATE_CONSTRAINT: [
    "该禁配规则已存在，反向也视为相同",
    "Forbidden pair already exists",
  ],
  INVALID_CONSTRAINT: [
    "禁配须连接同计划两个不同参数的已有值",
    "Choose values of two distinct factors in this campaign",
  ],
  VALUE_IN_USE: [
    "候选值被禁配引用，请先明确修改或移除规则",
    "Value is referenced by a constraint",
  ],
  FACTOR_IN_USE: ["参数仍被禁配引用", "Factor is referenced"],
  FACTOR_LIMIT: ["最多8个参数", "Maximum 8 factors"],
  CONSTRAINT_LIMIT: ["最多80条禁配规则", "Maximum 80 constraints"],
  INVALID_RESULT: [
    "请选择实际通过、失败或阻塞，并检查用例编号",
    "Choose an actual verdict and valid case number",
  ],
  INCOMPLETE_RESULT: [
    "每个用例都要明确登记实际结果",
    "Record every case explicitly",
  ],
  OUTCOME_MISMATCH: [
    "存在阻塞请选择部分完成，无阻塞选择全部执行",
    "Choose partial for blocked cases, finished otherwise",
  ],
  ACKNOWLEDGEMENT_REQUIRED: [
    "先确认收悉计划，在执行登记阶段记录结果",
    "Acknowledge the plan during recording",
  ],
  GENERATION_REQUIRED: ["请先生成测试组合", "Generate combinations first"],
  SNAPSHOT_CHANGED: [
    "冻结快照不一致，操作被阻止",
    "Snapshot changed; action blocked",
  ],
  INVALID_STATE: ["当前状态不允许操作", "Action unavailable in this state"],
  INVALID_INPUT: ["检查必填字段和输入范围", "Check required fields and bounds"],
  INVALID_OUTCOME: ["请选择全部执行或部分阻塞", "Choose finished or partial"],
  IDENTITY_IMMUTABLE: [
    "计划编号和负责部门不可修改",
    "Reference and department are immutable",
  ],
  CONFLICT: ["编号重复或记录被引用", "Duplicate or referenced record"],
  LOGIN_FAILED: ["账号或密码不正确", "Incorrect credentials"],
  LOGIN_THROTTLED: ["登录尝试过多，稍后重试", "Too many attempts"],
  LAST_ADMIN: [
    "至少保留一个启用的全范围管理员",
    "Keep an enabled full administrator",
  ],
  WEAK_PASSWORD: [
    "密码至少12位，包含大小写字母和数字",
    "Use 12+ characters, upper/lower case and digits",
  ],
  REQUEST_KEY_REUSED: [
    "请求键已用于其他内容，请重新打开表单",
    "Request key already used",
  ],
  REVISION_LIMIT: [
    "每计划最多50次生成，请新建后续计划",
    "Maximum 50 generations per campaign",
  ],
  RECORD_LIMIT: ["已达计划数量上限", "Campaign limit reached"],
  COMMAND_LIMIT: ["已达操作记录上限", "Command limit reached"],
};
function language() {
  lang.value = lang.value === "zh" ? "en" : "zh";
  localStorage.setItem("configpair-language", lang.value);
  document.documentElement.lang = lang.value === "zh" ? "zh-CN" : "en";
}
function clearSession() {
  me.value = null;
  detail.value = null;
  rows.value = [];
  options.value = {};
  directories.value = {};
  modal.value = null;
  oldRevision.value = null;
  resetCsrf();
}
async function run(fn) {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    return await fn();
  } catch (e) {
    error.value = failures[e.message]
      ? t(...failures[e.message])
      : t("操作失败：", "Action failed: ") + e.message;
    if (e.message === "UNAUTHENTICATED") clearSession();
    if (["FORBIDDEN", "OUT_OF_SCOPE"].includes(e.message)) {
      detail.value = null;
      oldRevision.value = null;
      rows.value = [];
    }
  } finally {
    busy.value = false;
  }
}
async function loadOptions() {
  if (can("job.read")) options.value = await api("/options");
  if (can("admin") && me.value.scope === "ALL")
    for (const key of ["roles", "permissions", "departments"])
      directories.value[key] = await api("/admin/" + key);
}
async function load() {
  detail.value = null;
  oldRevision.value = null;
  if (view.value === "dashboard") {
    stats.value = await api("/dashboard");
    return;
  }
  if (view.value === "jobs") {
    const r = await api(
      "/jobs?" +
        new URLSearchParams({
          search: search.value,
          status: filter.value,
          page: String(page.value),
          size: "12",
          sort: sort.value,
        }),
    );
    rows.value = r.rows;
    total.value = r.total;
  } else {
    const all = (
      await api(view.value === "audit" ? "/audit" : "/admin/" + view.value)
    ).filter((r) =>
      Object.values(r).some(
        (v) =>
          typeof v === "string" &&
          v.toLowerCase().includes(search.value.toLowerCase()),
      ),
    );
    all.sort((a, b) => (sort.value === "oldest" ? a.id - b.id : b.id - a.id));
    total.value = all.length;
    rows.value = all.slice(page.value * 12, page.value * 12 + 12);
  }
}
async function navigate(code) {
  if (busy.value) return;
  rows.value = [];
  total.value = 0;
  view.value = code;
  page.value = 0;
  search.value = "";
  filter.value = "";
  await run(load);
  window.scrollTo(0, 0);
}
async function signIn() {
  await run(async () => {
    resetCsrf();
    me.value = await api("/auth/login", "POST", loginForm.value);
    loginForm.value.password = "";
    await loadOptions();
    view.value = me.value.menus[0]?.code || "jobs";
    await load();
  });
}
async function signOut() {
  await run(async () => {
    await api("/auth/logout", "POST", {});
    clearSession();
  });
}
async function open(row) {
  await run(async () => {
    detail.value = await api("/jobs/" + row.id);
    oldRevision.value = null;
    tab.value = "coverage";
    casePage.value = 0;
    leftAxis.value = 0;
    rightAxis.value = 1;
    matrixMode.value = "executed";
    window.scrollTo(0, 0);
  });
}
async function refresh() {
  await run(async () => {
    me.value = await api("/auth/me");
    await loadOptions();
    oldRevision.value = null;
    if (detail.value) detail.value = await api("/jobs/" + detail.value.id);
    else await load();
  });
}
function edit(type, row = null) {
  error.value = "";
  modal.value = { kind: "save", type, row };
  form.value = {
    departmentId: me.value.departmentId,
    category: "WEB",
    enabled: true,
    type: "category",
    scope: "DEPARTMENT",
    permissions: [],
    ...row,
    requestKey: crypto.randomUUID(),
    version: detail.value?.version,
  };
  if (type === "users") form.value.password = "";
  if (type === "roles") form.value.permissions = [...(row?.permissions || [])];
  if (type === "factors") form.value.values = (row?.values || []).join("\n");
}
function command(action) {
  error.value = "";
  modal.value = { kind: "command", type: "command", action };
  form.value = {
    requestKey: crypto.randomUUID(),
    version: detail.value.version,
    note: "",
    outcome: detail.value.coverage?.blocked > 0 ? "PARTIAL" : "FINISHED",
  };
}
function actual(c) {
  error.value = "";
  const row = detail.value.results.find((r) => r.caseNumber === c.number);
  modal.value = { kind: "actual", type: "results", caseNumber: c.number };
  form.value = {
    status: "",
    note: "",
    evidence: "",
    ...row,
    requestKey: crypto.randomUUID(),
    version: detail.value.version,
  };
}
function remove(type, row) {
  error.value = "";
  modal.value = {
    kind: adminTypes.includes(type) ? "adminDelete" : "delete",
    type: "command",
    target: type,
    row,
  };
  form.value = {
    requestKey: crypto.randomUUID(),
    version: detail.value?.version,
    note: "",
  };
}
const modalFields = computed(() =>
  modal.value?.kind === "password"
    ? [
        ["oldPassword", "原密码", "Current password", "password"],
        ["newPassword", "新密码", "New password", "password"],
      ]
    : fields[modal.value?.type] || [],
);
function choices(key) {
  if (key === "scope")
    return ["ALL", "DEPARTMENT", "SELF"].map((code) => ({
      code,
      name: t(
        ...{
          ALL: ["全部", "All"],
          DEPARTMENT: ["本部门", "Department"],
          SELF: ["本人相关", "Own records"],
        }[code],
      ),
    }));
  if (key === "verdicts")
    return ["PASS", "FAIL", "BLOCKED"].map((code) => ({
      code,
      name: state(code),
    }));
  if (["leftFactors", "rightFactors"].includes(key))
    return detail.value?.factors || [];
  if (["leftValues", "rightValues"].includes(key)) {
    const id = Number(
      form.value[key === "leftValues" ? "leftFactorId" : "rightFactorId"],
    );
    return (modelFactors.value.find((f) => f.id === id)?.values || []).map(
      (code) => ({ code, name: code }),
    );
  }
  if (["reviewers", "operators"].includes(key))
    return (options.value.accounts || []).filter(
      (a) =>
        a.departmentId === Number(form.value.departmentId) &&
        a.id !== me.value.id &&
        a.permissions.includes(
          key === "reviewers" ? "job.review" : "test.write",
        ),
    );
  return directories.value[key] || options.value[key] || [];
}
async function save() {
  await run(async () => {
    const m = modal.value;
    let result;
    if (m.kind === "password") {
      await api(
        "/auth/password",
        "POST",
        payload(form.value, modalFields.value),
      );
      clearSession();
      notice.value = t(
        "密码已更改，请重新登录",
        "Password changed; sign in again",
      );
      return;
    }
    if (m.kind === "adminDelete")
      await api("/admin/" + m.target + "/" + m.row.id, "DELETE");
    else if (m.kind === "command" || m.kind === "delete") {
      const body = {
        requestKey: form.value.requestKey,
        version: form.value.version,
        jobId: detail.value.id,
        note: form.value.note,
      };
      if (m.action === "submit-report") body.outcome = form.value.outcome;
      result = await api(
        m.kind === "command"
          ? "/jobs/" + detail.value.id + "/commands/" + m.action
          : "/" + m.target + "/" + m.row.id + "/delete",
        "POST",
        body,
      );
    } else {
      const body = payload(form.value, modalFields.value),
        isAdmin = adminTypes.includes(m.type);
      if (!isAdmin) {
        body.requestKey = form.value.requestKey;
        body.version = form.value.version;
      }
      if (m.kind === "actual") {
        body.jobId = detail.value.id;
        body.caseNumber = m.caseNumber;
        result = await api("/results", "POST", body);
      } else {
        if (["factors", "constraints"].includes(m.type))
          body.jobId = detail.value.id;
        result = await api(
          (isAdmin ? "/admin" : "") +
            "/" +
            m.type +
            (m.row ? "/" + m.row.id : ""),
          m.row ? "PUT" : "POST",
          body,
        );
      }
    }
    modal.value = null;
    oldRevision.value = null;
    notice.value = t("已保存", "Saved");
    await loadOptions();
    if (detail.value) detail.value = await api("/jobs/" + detail.value.id);
    else if (m.type === "jobs" && result) {
      detail.value = await api("/jobs/" + result.id);
      tab.value = "model";
    } else await load();
  });
}
async function revision(row) {
  await run(async () => {
    oldRevision.value = await api("/revisions/" + row.id);
    tab.value = "coverage";
    casePage.value = 0;
    leftAxis.value = 0;
    rightAxis.value = 1;
  });
}
async function download(kind) {
  await run(async () => {
    const path =
      "/jobs/" +
      detail.value.id +
      "/" +
      (kind === "json" ? "report.json" : "cases.csv");
    let blob;
    if (kind === "json")
      blob = new Blob([JSON.stringify(await api(path), null, 2)], {
        type: "application/json",
      });
    else {
      const response = await fetch("/api" + path);
      if (!response.ok) throw new Error((await response.json()).code);
      blob = await response.blob();
    }
    const url = URL.createObjectURL(blob),
      a = document.createElement("a");
    a.href = url;
    a.download = "configpair-" + detail.value.id + "." + kind;
    a.click();
    URL.revokeObjectURL(url);
  });
}
function time(value) {
  return value
    ? new Intl.DateTimeFormat(lang.value === "zh" ? "zh-CN" : "en-GB", {
        timeZone: "Asia/Shanghai",
        dateStyle: "short",
        timeStyle: "short",
      }).format(new Date(value))
    : "—";
}
function person(id) {
  return (
    options.value.accounts?.find((a) => a.id === id)?.displayName || String(id)
  );
}
function factorName(id) {
  return modelFactors.value.find((f) => f.id === id)?.code || "#" + id;
}
function caseResult(number) {
  return activeResults.value.find((r) => r.caseNumber === number);
}
function matrixStatus(a, b) {
  const i = pairIndex(
    plan.value,
    leftFactor.value?.code,
    a,
    rightFactor.value?.code,
    b,
  );
  return i < 0 ? "excluded" : coverageSet.value.has(i) ? "covered" : "missing";
}
function value(row, key) {
  if (key.endsWith("At")) return time(row[key]);
  if (key === "status") return state(row[key]);
  if (key === "enabled") return row[key] ? t("是", "Yes") : t("否", "No");
  if (key === "permissions")
    return (row.permissions?.length || 0) + t("项权限", " permissions");
  const lookup =
    key === "roleId"
      ? directories.value.roles
      : key === "departmentId"
        ? directories.value.departments || options.value.departments
        : null;
  return lookup?.find((v) => v.id === row[key])?.name ?? row[key] ?? "—";
}
const columns = computed(
  () =>
    ({
      jobs: [
        ["reference", "计划编号", "Reference"],
        ["name", "计划名称", "Name"],
        ["systemName", "测试对象", "System"],
        ["buildLabel", "版本", "Build"],
        ["status", "状态", "Status"],
      ],
      settings: [
        ["code", "参数", "Setting"],
        ["value", "参数值", "Value"],
      ],
      audit: [
        ["createdAt", "时间", "Time"],
        ["actor", "账号", "Actor"],
        ["action", "动作", "Action"],
        ["objectId", "记录", "Record"],
      ],
    })[view.value] ||
    (fields[view.value] || []).filter((f) => f[0] !== "password").slice(0, 5),
);
const canCreate = computed(() =>
  view.value === "jobs"
    ? can("job.write")
    : can("admin") &&
      ["users", "roles", "departments", "dictionaries"].includes(view.value),
);
onMounted(async () => {
  document.documentElement.lang = lang.value === "zh" ? "zh-CN" : "en";
  try {
    me.value = await api("/auth/me");
    await loadOptions();
    view.value = me.value.menus[0]?.code || "jobs";
    await load();
  } catch (e) {
    if (e.message !== "UNAUTHENTICATED")
      error.value = t("连接失败，请刷新页面", "Connection failed; refresh");
  }
});
</script>
<template>
  <div v-if="!me" class="login-layout">
    <section class="login-art" aria-hidden="true">
      <div class="art-title">ConfigPair<span>01 / PAIRWISE COVERAGE</span></div>
      <div class="art-grid">
        <i
          v-for="n in 16"
          :key="n"
          :class="{ lit: [1, 4, 6, 7, 9, 12, 14, 15].includes(n) }"
          >{{ String(n).padStart(2, "0") }}</i
        >
      </div>
      <div class="art-note">
        {{
          t(
            "参数模型 · 组合覆盖 · 执行复核",
            "Input model · Pair coverage · Manual verification",
          )
        }}
      </div>
    </section>
    <section class="login-panel">
      <div class="brand">
        <img src="/brand/logo.jpg" alt="知华科技" /><button
          class="plain"
          @click="language"
        >
          {{ lang === "zh" ? "EN" : "中文" }}
        </button>
      </div>
      <form class="login-card" @submit.prevent="signIn">
        <div class="eyebrow">CONFIGPAIR / {{ t("工作空间", "WORKSPACE") }}</div>
        <h1>{{ t("登录工作空间", "Sign in to your workspace") }}</h1>
        <p>
          {{
            t(
              "参数组合测试与覆盖复核",
              "Configuration testing & coverage review",
            )
          }}
        </p>
        <label
          >{{ t("账号", "Username")
          }}<input
            v-model="loginForm.username"
            autocomplete="username"
            required
            maxlength="60"
        /></label>
        <label
          >{{ t("密码", "Password")
          }}<input
            v-model="loginForm.password"
            type="password"
            autocomplete="current-password"
            required
            maxlength="72"
        /></label>
        <p v-if="error" role="alert" class="error">{{ error }}</p>
        <p v-if="notice" role="status">{{ notice }}</p>
        <button class="primary login-button" :disabled="busy">
          {{ busy ? t("正在登录…", "Signing in…") : t("登录", "Sign in")
          }}<ArrowRight :size="17" />
        </button>
        <small>{{
          t(
            "公开源码学习版 · 非商业使用",
            "Public source learning edition · Noncommercial use",
          )
        }}</small>
      </form>
      <footer>
        {{
          t(
            "知华科技（上海如静知华信息科技有限公司）",
            "ZhuaTech · Shanghai Rujing Zhihua Information Technology Co., Ltd.",
          )
        }}<button class="plain" @click="contact = true">
          {{ t("联系知华科技", "Contact ZhuaTech") }}
        </button>
      </footer>
    </section>
  </div>
  <div v-else class="workspace">
    <aside class="sidebar">
      <div class="brand"><img src="/brand/logo.jpg" alt="知华科技" /></div>
      <div class="product-name">
        <Layers :size="22" /><span
          >ConfigPair<small>{{
            t("参数组合", "Configuration testing")
          }}</small></span
        >
      </div>
      <nav :aria-label="t('主导航', 'Main navigation')">
        <button
          v-for="m in me.menus"
          :key="m.id"
          :class="{ active: view === m.code }"
          :disabled="busy"
          @click="navigate(m.code)"
        >
          <component :is="icons[m.code] || Settings" :size="18" /><span>{{
            lang === "zh" ? m.name : m.nameEn
          }}</span
          ><span v-if="view === m.code" class="nav-dot"></span>
        </button>
      </nav>
      <div class="sidebar-end">
        <span>{{ t("公开源码学习版", "Source learning edition") }}</span
        ><button class="plain" @click="contact = true">
          {{ t("商业授权／定制咨询", "Licensing / custom development")
          }}<ExternalLink :size="12" />
        </button>
      </div>
    </aside>
    <div class="main-area">
      <header class="topbar">
        <span>{{ options.companyName || "ConfigPair" }}</span>
        <div>
          <button class="plain" @click="language">
            {{ lang === "zh" ? "EN" : "中文" }}</button
          ><span class="avatar">{{ me.displayName.slice(0, 1) }}</span
          ><span class="user-name"
            >{{ me.displayName }}<small>{{ me.role }}</small></span
          ><button
            class="plain"
            @click="
              modal = { kind: 'password', type: 'password' };
              form = { oldPassword: '', newPassword: '' };
            "
          >
            {{ t("改密", "Password") }}</button
          ><button
            class="icon-button"
            :aria-label="t('退出登录', 'Sign out')"
            :disabled="busy"
            @click="signOut"
          >
            <LogOut :size="17" />
          </button>
        </div>
      </header>
      <main>
        <div class="page-heading">
          <div>
            <div class="eyebrow">
              CONFIGPAIR / {{ t("工作空间", "WORKSPACE") }}
            </div>
            <h1>{{ detail ? detail.name : title }}</h1>
            <p v-if="detail">
              {{ detail.reference }} <span class="separator">/</span>
              <span class="status" :data-status="detail.status">{{
                state(detail.status)
              }}</span>
              <span class="version">v{{ detail.version }}</span>
            </p>
            <p v-else>
              {{
                t(
                  "当前授权范围内的记录",
                  "Records within your authorized scope",
                )
              }}
            </p>
          </div>
          <div class="heading-actions">
            <button
              v-if="detail"
              class="secondary"
              :disabled="busy"
              @click="run(load)"
            >
              <ChevronLeft :size="16" />{{ t("返回列表", "Back") }}</button
            ><button
              class="icon-button"
              :disabled="busy"
              :aria-label="t('刷新', 'Refresh')"
              @click="refresh"
            >
              <RefreshCw :size="18" /></button
            ><button
              v-if="!detail && canCreate"
              class="primary"
              :disabled="busy"
              @click="edit(view)"
            >
              <Plus :size="17" />{{ t("新建", "New") }}
            </button>
          </div>
        </div>
        <p v-if="error" role="alert" class="error banner">{{ error }}</p>
        <p v-if="notice && !modal" role="status" class="notice banner">
          {{ notice }}
        </p>
        <template v-if="detail">
          <section class="job-summary">
            <div>
              <small>{{ t("测试对象／版本", "System / build") }}</small
              ><strong>{{ campaignData.systemName }}</strong
              ><span>{{ campaignData.buildLabel }}</span>
            </div>
            <div>
              <small>{{ t("测试环境", "Environment") }}</small
              ><strong>{{ campaignData.environment }}</strong>
            </div>
            <div>
              <small>{{ t("独立复核／执行人", "Reviewer / tester") }}</small
              ><strong>{{ person(campaignData.reviewerId) }}</strong
              ><span>{{ person(campaignData.operatorId) }}</span>
            </div>
            <button
              v-if="editable"
              class="secondary"
              :disabled="busy"
              @click="edit('jobs', detail)"
            >
              {{ t("编辑计划", "Edit campaign") }}
            </button>
          </section>
          <div class="command-bar">
            <button
              v-for="a in currentActions"
              :key="a"
              :class="
                ['approve', 'close', 'generate'].includes(a)
                  ? 'primary'
                  : 'secondary'
              "
              :disabled="busy"
              @click="command(a)"
            >
              {{ actionName(a) }}
            </button>
          </div>
          <nav
            class="detail-tabs"
            :aria-label="t('计划详情', 'Campaign details')"
          >
            <button
              v-for="item in [
                ['coverage', '覆盖矩阵', 'Coverage matrix'],
                ['model', '参数与禁配', 'Factors & constraints'],
                ['cases', '用例与结果', 'Cases & results'],
                ['history', '版本与历史', 'Revisions & history'],
              ]"
              :key="item[0]"
              :class="{ active: tab === item[0] }"
              @click="tab = item[0]"
            >
              {{ t(item[1], item[2]) }}
            </button>
          </nav>
          <div v-if="oldRevision" class="history-alert">
            <span
              >{{
                t(
                  "历史生成快照，未套用当前执行结果",
                  "Historical generated plan; current test results are not applied",
                )
              }}
              · #{{ oldRevision.revision.id }}</span
            ><button
              class="plain"
              @click="
                oldRevision = null;
                leftAxis = 0;
                rightAxis = 1;
                casePage = 0;
              "
            >
              {{ t("返回当前", "Current plan") }}
            </button>
          </div>
          <template v-if="tab === 'coverage'">
            <template v-if="plan">
              <section class="metric-grid">
                <div class="metric">
                  <small>{{
                    t("合法／原始配置", "Valid / raw configurations")
                  }}</small
                  ><strong
                    >{{ plan.validConfigurations
                    }}<em>/ {{ plan.totalConfigurations }}</em></strong
                  ><span
                    >{{ t("禁配排除", "Rejected") }}
                    {{ plan.rejectedConfigurations }}</span
                  >
                </div>
                <div class="metric">
                  <small>{{ t("生成用例", "Generated cases") }}</small
                  ><strong>{{ plan.cases.length }}</strong
                  ><span
                    >{{ t("可达值对", "Feasible pairs") }}
                    {{ plan.pairs.length }}</span
                  >
                </div>
                <div class="metric accent">
                  <small>{{
                    t("实际执行覆盖", "Executed pair coverage")
                  }}</small
                  ><strong>{{
                    oldRevision ? "—" : detail.coverage.executedPercent + "%"
                  }}</strong
                  ><span>{{
                    oldRevision
                      ? t("只展示生成计划", "Generated plan only")
                      : detail.coverage.executedPairs +
                        " / " +
                        detail.coverage.feasiblePairs
                  }}</span>
                </div>
                <div class="metric">
                  <small>{{ t("通过值对覆盖", "Passing pair coverage") }}</small
                  ><strong>{{
                    oldRevision ? "—" : detail.coverage.passedPercent + "%"
                  }}</strong
                  ><span
                    >{{ t("生成覆盖", "Planned pair coverage") }} 100%</span
                  >
                </div>
              </section>
              <div class="coverage-grid">
                <section class="card matrix-card">
                  <div class="section-title">
                    <div>
                      <div class="eyebrow">PAIR MATRIX</div>
                      <h2>
                        {{ t("参数值对覆盖", "Factor value pair coverage") }}
                      </h2>
                    </div>
                    <select
                      v-if="!oldRevision"
                      v-model="matrixMode"
                      :aria-label="t('覆盖口径', 'Coverage mode')"
                    >
                      <option value="executed">
                        {{ t("实际执行", "Executed") }}
                      </option>
                      <option value="passed">
                        {{ t("实际通过", "Passed") }}
                      </option>
                      <option value="planned">
                        {{ t("生成计划", "Planned") }}
                      </option>
                    </select>
                  </div>
                  <div class="axis-controls">
                    <label
                      >{{ t("行参数", "Row factor")
                      }}<select v-model.number="leftAxis">
                        <option
                          v-for="(f, i) in plan.factors"
                          :key="f.code"
                          :value="i"
                        >
                          {{ f.name }} · {{ f.code }}
                        </option>
                      </select></label
                    ><span>×</span
                    ><label
                      >{{ t("列参数", "Column factor")
                      }}<select v-model.number="rightAxis">
                        <option
                          v-for="(f, i) in plan.factors"
                          :key="f.code"
                          :value="i"
                        >
                          {{ f.name }} · {{ f.code }}
                        </option>
                      </select></label
                    >
                  </div>
                  <div
                    v-if="leftFactor && rightFactor && leftAxis !== rightAxis"
                    class="table-scroll"
                  >
                    <table class="matrix">
                      <thead>
                        <tr>
                          <th>
                            {{ leftFactor.code }} / {{ rightFactor.code }}
                          </th>
                          <th v-for="v in rightFactor.values" :key="v">
                            {{ v }}
                          </th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr v-for="a in leftFactor.values" :key="a">
                          <th>{{ a }}</th>
                          <td
                            v-for="b in rightFactor.values"
                            :key="b"
                            :class="matrixStatus(a, b)"
                          >
                            <span>{{
                              matrixStatus(a, b) === "excluded"
                                ? t("不可达", "Excluded")
                                : matrixStatus(a, b) === "covered"
                                  ? t("已覆盖", "Covered")
                                  : t("未覆盖", "Missing")
                            }}</span>
                          </td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                  <p v-else class="empty">
                    {{
                      t("请选择两个不同参数", "Choose two different factors")
                    }}
                  </p>
                  <p class="legend">
                    <i class="covered"></i
                    >{{ t("该口径已覆盖", "Covered in this mode")
                    }}<i class="missing"></i
                    >{{ t("可达但未覆盖", "Feasible, not covered")
                    }}<i class="excluded"></i
                    >{{ t("禁配不可达", "Unreachable") }}
                  </p>
                </section>
                <section class="card execution-card">
                  <div class="eyebrow">MANUAL EVIDENCE</div>
                  <h2>{{ t("逐例人工事实", "Manual case results") }}</h2>
                  <dl v-if="!oldRevision">
                    <div
                      v-for="(label, key) in {
                        passed: t('通过', 'Passed'),
                        failed: t('失败', 'Failed'),
                        blocked: t('阻塞', 'Blocked'),
                        pending: t('未登记', 'Pending'),
                      }"
                      :key="key"
                    >
                      <dt>{{ label }}</dt>
                      <dd>{{ detail.coverage[key] }}</dd>
                    </div>
                  </dl>
                  <p v-else>
                    {{
                      t(
                        "历史版本保留模型与生成用例",
                        "Historical input and generated cases are preserved",
                      )
                    }}
                  </p>
                  <button class="secondary" @click="tab = 'cases'">
                    {{ t("查看用例", "View cases") }}<ArrowRight :size="16" />
                  </button>
                  <p class="form-note">
                    {{
                      t(
                        "通过和失败计入执行覆盖；阻塞与未登记不计入。成对覆盖不代表发现所有缺陷。",
                        "Passed and failed cases count as executed; blocked and pending do not. Pair coverage does not guarantee finding all defects.",
                      )
                    }}
                  </p>
                </section>
              </div>
              <section
                v-if="plan.unreachableValues.length"
                class="card unreachable"
              >
                <h2>{{ t("不可达候选值", "Unreachable values") }}</h2>
                <span
                  v-for="s in plan.unreachableValues"
                  :key="JSON.stringify(s)"
                  class="chip"
                  >{{ s.factor }} = {{ s.value }}</span
                >
                <p>
                  {{
                    t(
                      "这些值没有任何合法完整配置，不进入覆盖分母。",
                      "These values occur in no valid complete configuration and are excluded from coverage.",
                    )
                  }}
                </p>
              </section>
            </template>
            <section v-else class="card empty">
              <Layers :size="38" />
              <h2>{{ t("尚未生成组合", "No generated plan") }}</h2>
              <p>
                {{
                  t(
                    "添加参数和禁配后生成，人工执行结果将在指定岗位登记后显示。",
                    "Define factors and constraints, then generate. Actual results appear only after assigned manual recording.",
                  )
                }}
              </p>
              <button class="secondary" @click="tab = 'model'">
                {{ t("编辑参数模型", "Open input model") }}
              </button>
            </section>
          </template>
          <section v-else-if="tab === 'model'" class="model-grid">
            <section class="card">
              <div class="section-title">
                <div>
                  <div class="eyebrow">INPUT FACTORS</div>
                  <h2>{{ t("离散参数", "Discrete factors") }}</h2>
                </div>
                <button
                  v-if="editable"
                  class="primary"
                  :disabled="busy"
                  @click="edit('factors')"
                >
                  <Plus :size="16" />{{ t("添加参数", "Add factor") }}
                </button>
              </div>
              <div class="table-scroll">
                <table>
                  <thead>
                    <tr>
                      <th>{{ t("参数", "Factor") }}</th>
                      <th>{{ t("候选值", "Values") }}</th>
                      <th v-if="editable">{{ t("操作", "Actions") }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="f in modelFactors" :key="f.id">
                      <td>
                        <strong>{{ f.name }}</strong
                        ><small>{{ f.code }}</small>
                      </td>
                      <td>
                        <span v-for="v in f.values" :key="v" class="chip">{{
                          v
                        }}</span>
                      </td>
                      <td v-if="editable" class="row-actions">
                        <button class="plain" @click="edit('factors', f)">
                          {{ t("编辑", "Edit") }}</button
                        ><button
                          class="plain danger"
                          @click="remove('factors', f)"
                        >
                          {{ t("删除", "Delete") }}
                        </button>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <p v-if="!modelFactors.length" class="empty">
                {{ t("尚无参数", "No factors") }}
              </p>
            </section>
            <section class="card">
              <div class="section-title">
                <div>
                  <div class="eyebrow">FORBIDDEN PAIRS</div>
                  <h2>{{ t("显式禁配", "Explicit constraints") }}</h2>
                </div>
                <button
                  v-if="editable"
                  class="secondary"
                  :disabled="busy || detail.factors.length < 2"
                  @click="edit('constraints')"
                >
                  <Plus :size="16" />{{ t("添加禁配", "Add constraint") }}
                </button>
              </div>
              <div class="constraint-list">
                <article v-for="r in modelRules" :key="r.id">
                  <strong
                    >{{ factorName(r.leftFactorId) }} = {{ r.leftValue }}
                    <span>×</span> {{ factorName(r.rightFactorId) }} =
                    {{ r.rightValue }}</strong
                  >
                  <p>{{ r.reason }}</p>
                  <div v-if="editable">
                    <button class="plain" @click="edit('constraints', r)">
                      {{ t("编辑", "Edit") }}</button
                    ><button
                      class="plain danger"
                      @click="remove('constraints', r)"
                    >
                      {{ t("删除", "Delete") }}
                    </button>
                  </div>
                </article>
              </div>
              <p v-if="!modelRules.length" class="empty">
                {{
                  t(
                    "尚无禁配，所有候选组合允许",
                    "No constraints; all candidate combinations are allowed",
                  )
                }}
              </p>
            </section>
            <section class="card instructions">
              <h2>
                {{
                  t(
                    "预期行为与判定准则",
                    "Expected behavior and verdict criteria",
                  )
                }}
              </h2>
              <p>{{ campaignData.instructions }}</p>
            </section>
          </section>
          <section v-else-if="tab === 'cases'" class="card">
            <div class="section-title">
              <div>
                <div class="eyebrow">TEST CASES</div>
                <h2>
                  {{
                    t("组合用例与人工结果", "Configurations and manual results")
                  }}
                </h2>
              </div>
              <span v-if="plan"
                >{{ plan.cases.length }} {{ t("例", "cases") }}</span
              >
            </div>
            <div v-if="plan" class="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>#</th>
                    <th v-for="f in plan.factors" :key="f.code">
                      {{ f.code }}
                    </th>
                    <th>{{ t("实际结果", "Actual verdict") }}</th>
                    <th>{{ t("事实／证据", "Facts / evidence") }}</th>
                    <th>{{ t("操作", "Actions") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="c in visibleCases" :key="c.number">
                    <td>{{ String(c.number).padStart(3, "0") }}</td>
                    <td v-for="s in c.selections" :key="s.factor">
                      {{ s.value }}
                    </td>
                    <td>
                      <span
                        class="status"
                        :data-status="caseResult(c.number)?.status || 'PENDING'"
                        >{{
                          oldRevision
                            ? "—"
                            : state(caseResult(c.number)?.status || "PENDING")
                        }}</span
                      >
                    </td>
                    <td>
                      {{ caseResult(c.number)?.note || "—"
                      }}<small>{{ caseResult(c.number)?.evidence }}</small>
                    </td>
                    <td>
                      <button
                        v-if="
                          !oldRevision &&
                          detail.canOperate &&
                          detail.status === 'RUNNING' &&
                          detail.acknowledgedAt
                        "
                        class="plain"
                        :disabled="busy"
                        @click="actual(c)"
                      >
                        {{ t("登记结果", "Record verdict") }}</button
                      ><span v-else>—</span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div v-if="plan" class="pagination">
              <button
                class="icon-button"
                :disabled="casePage === 0"
                :aria-label="t('用例上一页', 'Previous cases')"
                @click="casePage--"
              >
                <ChevronLeft :size="17" /></button
              ><span
                >{{ casePage + 1 }} /
                {{ Math.max(1, Math.ceil(plan.cases.length / 12)) }}</span
              ><button
                class="icon-button"
                :disabled="(casePage + 1) * 12 >= plan.cases.length"
                :aria-label="t('用例下一页', 'Next cases')"
                @click="casePage++"
              >
                <ChevronRight :size="17" />
              </button>
            </div>
            <p v-else class="empty">
              {{ t("生成后展示完整用例", "Generate a plan to see cases") }}
            </p>
            <dl v-if="detail.closedHash && !oldRevision" class="seal">
              <dt>{{ t("封存结果", "Closed outcome") }}</dt>
              <dd>{{ state(detail.outcome) }}</dd>
              <dt>SHA-256</dt>
              <dd>{{ detail.closedHash }}</dd>
            </dl>
          </section>
          <section v-else class="history-grid">
            <section class="card">
              <h2>
                {{ t("不可变生成版本", "Immutable generated revisions") }}
              </h2>
              <article
                v-for="r in detail.revisions"
                :key="r.id"
                class="revision-row"
              >
                <div>
                  <strong>#{{ r.id }} · {{ r.algorithm }}</strong
                  ><small
                    >{{ time(r.createdAt) }} · {{ person(r.createdBy) }}</small
                  >
                </div>
                <button class="plain" :disabled="busy" @click="revision(r)">
                  {{ t("查看快照", "View snapshot") }}
                </button>
              </article>
              <p v-if="!detail.revisions.length" class="empty">
                {{ t("暂无生成版本", "No generated revisions") }}
              </p>
            </section>
            <section class="card">
              <h2>{{ t("计划操作历史", "Campaign activity") }}</h2>
              <article
                v-for="e in detail.history"
                :key="e.id"
                class="event-row"
              >
                <small>{{ time(e.createdAt) }} · {{ person(e.actorId) }}</small
                ><strong>{{ e.action }}</strong>
                <p>{{ e.note }}</p>
              </article>
            </section>
          </section>
          <div v-if="can('export') && !oldRevision" class="export-bar">
            <button
              class="secondary"
              :disabled="busy"
              @click="download('json')"
            >
              <Download :size="16" />JSON {{ t("报告", "report") }}</button
            ><button
              v-if="detail.plan"
              class="secondary"
              :disabled="busy"
              @click="download('csv')"
            >
              <Download :size="16" />CSV {{ t("用例", "cases") }}
            </button>
          </div>
        </template>
        <template v-else-if="view === 'dashboard'"
          ><section class="metric-grid">
            <div
              v-for="[key, zh, en] in [
                ['jobs', '测试计划', 'Campaigns'],
                ['cases', '生成用例', 'Generated cases'],
                ['executedPairs', '已执行值对', 'Executed pairs'],
                ['feasiblePairs', '可达值对', 'Feasible pairs'],
              ]"
              :key="key"
              class="metric"
            >
              <small>{{ t(zh, en) }}</small
              ><strong>{{ stats[key] || 0 }}</strong>
            </div>
          </section>
          <div class="dashboard-panels">
            <section class="card">
              <h2>{{ t("计划状态", "Campaign status") }}</h2>
              <div v-for="(n, s) in stats.statuses" :key="s" class="stat-row">
                <span class="status" :data-status="s">{{ state(s) }}</span
                ><strong>{{ n }}</strong>
              </div>
              <p v-if="!stats.jobs" class="empty">
                {{ t("暂无计划", "No campaigns") }}
              </p>
            </section>
            <section class="card">
              <h2>{{ t("人工用例结果", "Manual case results") }}</h2>
              <div
                v-for="[key, zh, en] in [
                  ['passed', '通过', 'Passed'],
                  ['failed', '失败', 'Failed'],
                  ['blocked', '阻塞', 'Blocked'],
                  ['pending', '未登记', 'Pending'],
                ]"
                :key="key"
                class="stat-row"
              >
                <span>{{ t(zh, en) }}</span
                ><strong>{{ stats[key] || 0 }}</strong>
              </div>
              <p class="form-note">
                {{
                  t(
                    "值对按各计划相加，不跨模型去重；已取消计划不进入用例和覆盖统计。",
                    "Pairs are summed per campaign, not deduplicated across models. Cancelled campaigns are excluded from case and coverage totals.",
                  )
                }}
              </p>
            </section>
          </div></template
        >
        <section v-else class="card list-card">
          <form
            class="filter-bar"
            @submit.prevent="
              page = 0;
              run(load);
            "
          >
            <label class="search"
              ><Search :size="17" /><input
                v-model="search"
                :aria-label="t('搜索记录', 'Search records')"
                :placeholder="t('搜索编号、名称、对象或版本', 'Search records')"
                maxlength="160" /></label
            ><select
              v-if="view === 'jobs'"
              v-model="filter"
              :aria-label="t('状态筛选', 'Filter status')"
            >
              <option value="">{{ t("全部状态", "All statuses") }}</option>
              <option
                v-for="s in [
                  'DRAFT',
                  'GENERATED',
                  'SUBMITTED',
                  'APPROVED',
                  'RUNNING',
                  'REVIEW',
                  'CLOSED',
                  'CANCELLED',
                ]"
                :key="s"
                :value="s"
              >
                {{ state(s) }}
              </option></select
            ><select v-model="sort" :aria-label="t('排序', 'Sort')">
              <option value="newest">
                {{ t("最新优先", "Newest first") }}
              </option>
              <option value="oldest">
                {{ t("最早优先", "Oldest first") }}
              </option>
              <option v-if="view === 'jobs'" value="reference">
                {{ t("编号排序", "By reference") }}
              </option></select
            ><button class="secondary" :disabled="busy">
              {{ t("查询", "Search") }}
            </button>
          </form>
          <div class="table-scroll">
            <table>
              <thead>
                <tr>
                  <th v-for="c in columns" :key="c[0]">{{ t(c[1], c[2]) }}</th>
                  <th v-if="view !== 'audit'">{{ t("操作", "Actions") }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="r in rows" :key="r.id">
                  <td v-for="c in columns" :key="c[0]">
                    <span
                      v-if="c[0] === 'status'"
                      class="status"
                      :data-status="r.status"
                      >{{ state(r.status) }}</span
                    ><template v-else>{{ value(r, c[0]) }}</template>
                  </td>
                  <td v-if="view !== 'audit'">
                    <button
                      v-if="view === 'jobs'"
                      class="plain"
                      @click="open(r)"
                    >
                      {{ t("打开计划", "Open campaign")
                      }}<ArrowRight :size="14" /></button
                    ><template v-else
                      ><button class="plain" @click="edit(view, r)">
                        {{ t("编辑", "Edit") }}</button
                      ><button
                        v-if="
                          !['menus', 'permissions', 'settings'].includes(view)
                        "
                        class="plain danger"
                        @click="remove(view, r)"
                      >
                        {{ t("删除", "Delete") }}
                      </button></template
                    >
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div v-if="!rows.length" class="empty">
            <Layers :size="32" />
            <h3>{{ t("暂无记录", "No records") }}</h3>
            <p>
              {{
                t(
                  "当前筛选和授权范围内没有记录",
                  "No records within this filter and scope",
                )
              }}
            </p>
          </div>
          <footer class="pagination">
            <span
              >{{ t("共", "Total") }} {{ total }}
              {{ t("条记录", "records") }}</span
            >
            <div>
              <button
                class="icon-button"
                :disabled="page === 0 || busy"
                :aria-label="t('上一页', 'Previous page')"
                @click="
                  page--;
                  run(load);
                "
              >
                <ChevronLeft :size="16" /></button
              ><span>{{ page + 1 }}</span
              ><button
                class="icon-button"
                :disabled="(page + 1) * 12 >= total || busy"
                :aria-label="t('下一页', 'Next page')"
                @click="
                  page++;
                  run(load);
                "
              >
                <ChevronRight :size="16" />
              </button>
            </div>
          </footer>
        </section>
        <footer class="workspace-footer">
          <span
            >ConfigPair ·
            {{
              t(
                "公开源码学习版／非商业源码版",
                "Public source learning / Noncommercial edition",
              )
            }}</span
          ><a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
            >{{ t("知华科技官网", "ZhuaTech website")
            }}<ExternalLink :size="12"
          /></a>
        </footer>
      </main>
    </div>
  </div>
  <div v-if="modal" class="modal-backdrop" @click.self="modal = null">
    <section
      class="modal"
      role="dialog"
      aria-modal="true"
      aria-labelledby="modal-title"
    >
      <header>
        <h2 id="modal-title">
          {{
            modal.kind === "command"
              ? actionName(modal.action)
              : modal.kind === "password"
                ? t("修改密码", "Change password")
                : ["delete", "adminDelete"].includes(modal.kind)
                  ? t("确认删除", "Confirm deletion")
                  : modal.kind === "actual"
                    ? t("登记结果", "Record results") + " · " + modal.caseNumber
                    : t("编辑", "Edit") +
                      " · " +
                      t(
                        ...(labels[modal.type] || [
                          modal.type === "factors" ? "参数" : "测试结果",
                          modal.type === "factors" ? "Factor" : "Results",
                        ]),
                      )
          }}
        </h2>
        <button
          class="icon-button"
          :aria-label="t('关闭', 'Close')"
          @click="modal = null"
        >
          <X :size="20" />
        </button>
      </header>
      <form @submit.prevent="save">
        <div v-if="modal.kind !== 'adminDelete'" class="form-grid">
          <label
            v-for="f in modalFields"
            :key="f[0]"
            :class="{
              wide: ['textarea', 'values', 'permissions'].includes(f[3]),
              checkbox: f[3] === 'boolean',
            }"
            ><template v-if="f[3] === 'boolean'"
              ><input v-model="form[f[0]]" type="checkbox" />{{
                t(f[1], f[2])
              }}</template
            ><template v-else
              >{{ t(f[1], f[2]) }}
              <div v-if="f[3] === 'permissions'" class="permission-grid">
                <label v-for="p in directories.permissions || []" :key="p.id"
                  ><input
                    v-model="form.permissions"
                    type="checkbox"
                    :value="p.code"
                  />{{ p.name }}<small>{{ p.code }}</small></label
                >
              </div>
              <select
                v-else-if="['id', 'select'].includes(f[3])"
                v-model="form[f[0]]"
                @change="
                  f[0] === 'leftFactorId'
                    ? (form.leftValue = '')
                    : f[0] === 'rightFactorId'
                      ? (form.rightValue = '')
                      : undefined
                "
                required
                :disabled="
                  modal.row && modal.type === 'jobs' && f[0] === 'departmentId'
                "
              >
                <option disabled value="">{{ t("请选择", "Select") }}</option>
                <option
                  v-for="o in choices(f[4])"
                  :key="o.id || o.code"
                  :value="f[3] === 'id' ? o.id : o.code"
                >
                  {{
                    o.displayName ||
                    (lang === "en" && o.nameEn ? o.nameEn : o.name)
                  }}
                </option>
              </select>
              <textarea
                v-else-if="['textarea', 'values'].includes(f[3])"
                v-model="form[f[0]]"
                maxlength="1000"
                :required="modal.type !== 'results'"
              ></textarea>
              <input
                v-else
                v-model="form[f[0]]"
                :type="
                  ['integer', 'decimal'].includes(f[3])
                    ? 'number'
                    : f[3] === 'password'
                      ? 'password'
                      : 'text'
                "
                :step="f[3] === 'decimal' ? '0.1' : '1'"
                :min="['integer', 'decimal'].includes(f[3]) ? 0 : undefined"
                :maxlength="f[3] === 'password' ? 72 : 200"
                :required="
                  !(modal.type === 'users' && modal.row && f[0] === 'password')
                "
                :disabled="
                  modal.row && modal.type === 'jobs' && f[0] === 'reference'
                "
                :autocomplete="
                  f[3] === 'password' ? 'new-password' : 'off'
                " /></template
          ></label>
          <label v-if="modal.action === 'submit-report'" class="wide"
            >{{ t("实际申报", "Actual outcome declaration")
            }}<select v-model="form.outcome" required>
              <option value="FINISHED">
                {{
                  t(
                    "所有用例已执行，无阻塞",
                    "All cases executed; none blocked",
                  )
                }}
              </option>
              <option value="PARTIAL">
                {{
                  t(
                    "部分用例阻塞，逐例说明原因",
                    "Blocked cases remain; reasons recorded",
                  )
                }}
              </option>
            </select></label
          >
        </div>
        <p v-else>
          {{
            t(
              "删除未被引用的记录；已被业务引用的记录会保留。",
              "Delete an unreferenced record; business references prevent deletion.",
            )
          }}
        </p>
        <p v-if="modal.kind === 'actual'" class="form-note">
          {{
            t(
              "按冻结准则登记实际结果。失败和阻塞必须说明；证据参考不会自动打开或执行。",
              "Record actual results against frozen criteria. Explain failures and blockers; evidence references are not opened or executed.",
            )
          }}
        </p>
        <p v-if="error" role="alert" class="error">{{ error }}</p>
        <footer>
          <button type="button" class="secondary" @click="modal = null">
            {{ t("取消", "Cancel") }}</button
          ><button class="primary" :disabled="busy">
            {{ busy ? t("保存中…", "Saving…") : t("确认保存", "Save") }}
          </button>
        </footer>
      </form>
    </section>
  </div>
  <div v-if="contact" class="modal-backdrop" @click.self="contact = false">
    <section
      class="modal contact-modal"
      role="dialog"
      aria-modal="true"
      aria-labelledby="contact-title"
    >
      <header>
        <h2 id="contact-title">{{ t("联系知华科技", "Contact ZhuaTech") }}</h2>
        <button
          class="icon-button"
          :aria-label="t('关闭', 'Close')"
          @click="contact = false"
        >
          <X :size="20" />
        </button>
      </header>
      <img class="contact-logo" src="/brand/logo.jpg" alt="知华科技" />
      <p>
        {{
          t(
            "上海如静知华信息科技有限公司",
            "Shanghai Rujing Zhihua Information Technology Co., Ltd.",
          )
        }}
      </p>
      <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
        >https://www.zhuatech.cn/</a
      >
      <div class="qr-grid">
        <figure>
          <img src="/brand/wechat-zhuatech.png" alt="知华科技微信 zhuatech" />
          <figcaption>{{ t("咨询微信", "WeChat") }}：zhuatech</figcaption>
        </figure>
        <figure>
          <img src="/brand/wechat-zhuatech2.png" alt="知华科技微信 zhuatech2" />
          <figcaption>{{ t("咨询微信", "WeChat") }}：zhuatech2</figcaption>
        </figure>
      </div>
      <p>
        {{
          t(
            "商业授权 · 定制开发 · 部署 · 系统集成",
            "Commercial licensing · Custom development · Deployment · System integration",
          )
        }}
      </p>
      <small>{{
        t(
          "公开源码学习版／非商业源码版；授权范围以仓库 LICENSE 为准。",
          "Source learning / Noncommercial edition; repository LICENSE defines the terms.",
        )
      }}</small>
    </section>
  </div>
</template>

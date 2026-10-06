// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
/** 参数组合与身份表单的有限字段。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export const fields = {
  jobs: [
    ["reference", "计划编号", "Reference"],
    ["name", "计划名称", "Name"],
    ["departmentId", "负责部门", "Department", "id", "departments"],
    ["category", "系统类型", "System type", "select", "categories"],
    ["systemName", "测试对象", "System under test"],
    ["buildLabel", "构建／版本标识", "Build / version"],
    ["reviewerId", "独立复核人", "Independent reviewer", "id", "reviewers"],
    ["operatorId", "指定测试执行人", "Assigned tester", "id", "operators"],
    ["environment", "测试环境说明", "Test environment", "textarea"],
    [
      "instructions",
      "预期行为与判定准则",
      "Expected behavior / verdict criteria",
      "textarea",
    ],
  ],
  factors: [
    ["code", "参数编码（大写）", "Factor code (uppercase)"],
    ["name", "参数名称", "Factor name"],
    [
      "values",
      "候选值（每行一个，2—8个）",
      "Values (one per line, 2–8)",
      "values",
    ],
  ],
  constraints: [
    ["leftFactorId", "参数一", "First factor", "id", "leftFactors"],
    ["leftValue", "值一", "First value", "select", "leftValues"],
    ["rightFactorId", "参数二", "Second factor", "id", "rightFactors"],
    ["rightValue", "值二", "Second value", "select", "rightValues"],
    ["reason", "禁配原因", "Forbidden combination reason", "textarea"],
  ],
  results: [
    ["status", "实际测试结果", "Actual test verdict", "select", "verdicts"],
    ["note", "实际现象／阻塞原因", "Actual behavior / blocker", "textarea"],
    ["evidence", "证据记录号／参考文本", "Evidence reference", "textarea"],
  ],
  command: [["note", "操作事实／原因", "Reason / facts", "textarea"]],
  users: [
    ["username", "登录名", "Username"],
    ["displayName", "姓名", "Name"],
    [
      "password",
      "新密码（编辑时留空保留）",
      "New password (optional when editing)",
      "password",
    ],
    ["roleId", "角色", "Role", "id", "roles"],
    ["departmentId", "部门", "Department", "id", "departments"],
    ["enabled", "启用", "Enabled", "boolean"],
  ],
  roles: [
    ["name", "角色名称", "Role name"],
    ["scope", "数据范围", "Data scope", "select", "scope"],
    ["permissions", "接口权限", "API permissions", "permissions"],
  ],
  departments: [["name", "部门名称", "Department name"]],
  menus: [
    ["name", "中文名称", "Chinese name"],
    ["nameEn", "英文名称", "English name"],
    [
      "permissionCode",
      "所需权限",
      "Required permission",
      "select",
      "permissions",
    ],
    ["position", "排序", "Order", "integer"],
    ["enabled", "启用", "Enabled", "boolean"],
  ],
  permissions: [["name", "权限说明", "Permission description"]],
  dictionaries: [
    ["type", "字典类型", "Dictionary type"],
    ["code", "编码", "Code"],
    ["name", "中文名称", "Chinese name"],
    ["nameEn", "英文名称", "English name"],
  ],
  settings: [["value", "参数值", "Value"]],
};

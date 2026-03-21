# 任务清单：自动签到/签退漏洞检查

- [x] T1 建立规格草案：定义时间窗口、状态字段语义、动作选择规则
    - 验收：形成 `openspec/specs/auto-sign.md` 初稿

- [x] T2 梳理实现路径：前台 `runSignInOrBack()` 与后台 `runSignInOrBackForWorker()` 对齐检查
    - 验收：输出逻辑对照表（输入/分支/输出）

- [x] T3 边界检查：时间窗口端点（07:50, 08:00, 08:50, 09:00, 17:50, 18:00, 18:50, 19:00）
    - 验收：列出每个端点的期望动作与当前行为

- [x] T4 空值与异常检查：登录失败、`signInTf` 为空、响应为空、code 非 10000
    - 验收：输出异常路径矩阵和是否可恢复

- [x] T5 漏洞评估：重复签到、漏签、误签、无任务误调用
    - 验收：输出 P0/P1/P2 问题清单

- [x] T6 修复建议：最小改动优先（不破坏现有 UI）
    - 验收：每个问题对应 1 个修复方案

- [x] T7 回归用例：构建最小测试案例集（至少 8 条）
    - 验收：用例包含前置条件、步骤、预期结果

# 回归测试用例：自动签到/签退（第七步）

## 说明
- 目标代码：`app/src/main/java/com/example/autorun/helper/App.java`
- 目标方法：
    - 前台：`runSignInOrBack()`
    - Worker：`runSignInOrBackForWorker()`
- 时间窗口规则（含边界）：
    - 签到：07:50-08:00、17:50-18:00
    - 签退：08:50-09:00、18:50-19:00

---

## TC-01 登录失败（前台）
- 前置条件：账号或密码错误
- 步骤：触发 `runSignInOrBack()`
- 期望结果：
    1. 输出“账号或密码错误，无法开启自动签到/签退”
    2. 不调用 `getSignInTf`
    3. 不调用 `signInOrSignBack`

## TC-02 登录失败（Worker）
- 前置条件：账号或密码错误
- 步骤：触发 `runSignInOrBackForWorker()`
- 期望结果：
    1. 返回 `AUTH_FAIL`
    2. 不调用 `getSignInTf`
    3. 不调用 `signInOrSignBack`

## TC-03 studentId 为空（前台）
- 前置条件：登录成功，但 `userInfo.studentId == null`
- 步骤：触发 `runSignInOrBack()`
- 期望结果：
    1. 输出“登录信息不完整（studentId为空）...”
    2. 不调用 `getSignInTf`
    3. 不调用 `signInOrSignBack`

## TC-04 studentId 为空（Worker）
- 前置条件：登录成功，但 `userInfo.studentId == null`
- 步骤：触发 `runSignInOrBackForWorker()`
- 期望结果：
    1. 返回 `AUTH_FAIL`
    2. 不调用 `getSignInTf`
    3. 不调用 `signInOrSignBack`

## TC-05 无待签到俱乐部（Worker）
- 前置条件：`getSignInTf` 返回 `null`
- 步骤：触发 `runSignInOrBackForWorker()`
- 期望结果：
    1. 返回 `NO_PENDING`
    2. 不调用 `signInOrSignBack`

## TC-06 非时间窗口（Worker）
- 前置条件：`getSignInTf` 非空；当前时间不在四个窗口内（如 10:00）
- 步骤：触发 `runSignInOrBackForWorker()`
- 期望结果：
    1. 返回 `OUT_OF_WINDOW`
    2. 不调用 `signInOrSignBack`

## TC-07 签到窗口成功（早间边界开始）
- 前置条件：
    - 当前时间 = 07:50
    - `signStatus = "1"`
    - `signInStatus != "1" || signBackStatus != "1"`
- 步骤：触发 `runSignInOrBackForWorker()`
- 期望结果：
    1. 调用 `signInOrSignBack`，`signType = "1"`
    2. 接口 `code = 10000` 时返回 `SUCCESS`

## TC-08 签退窗口成功（早间边界开始）
- 前置条件：
    - 当前时间 = 08:50
    - `signInStatus = "1"`
    - `signStatus = "2"`
- 步骤：触发 `runSignInOrBackForWorker()`
- 期望结果：
    1. 调用 `signInOrSignBack`，`signType = "2"`
    2. 接口 `code = 10000` 时返回 `SUCCESS`

## TC-09 已完成当日签到签退
- 前置条件：`signInStatus = "1"` 且 `signBackStatus = "1"`
- 步骤：触发 `runSignInOrBackForWorker()`
- 期望结果：
    1. 返回 `ALREADY_DONE`
    2. 不调用 `signInOrSignBack`

## TC-10 签到窗口但不可签到
- 前置条件：
    - 当前时间在签到窗口（如 17:55）
    - `signStatus != "1"`
- 步骤：触发 `runSignInOrBackForWorker()`
- 期望结果：
    1. 返回 `NO_PENDING`
    2. 不调用 `signInOrSignBack`

## TC-11 签退窗口但不可签退
- 前置条件：
    - 当前时间在签退窗口（如 18:55）
    - `signInStatus != "1"` 或 `signStatus != "2"`
- 步骤：触发 `runSignInOrBackForWorker()`
- 期望结果：
    1. 返回 `NO_PENDING`
    2. 不调用 `signInOrSignBack`

## TC-12 接口空响应与失败码映射
- 前置条件：通过前置判定后发起 `signInOrSignBack`
- 步骤：
    1. mock `signResp = null`
    2. mock `signResp.code = 40013`
- 期望结果：
    1. `signResp = null` -> 返回 `RETRY`
    2. `code = 40013` -> 返回 `REMOTE_FAIL_40013`

## TC-13 时间边界闭区间验证（关键）
- 前置条件：构造固定时间
- 步骤：分别验证 08:00、09:00、18:00、19:00
- 期望结果：
    1. 08:00 与 18:00 仍判定为签到窗口
    2. 09:00 与 19:00 仍判定为签退窗口
    3. 08:00:01 与 19:00:01 判定为窗口外

## TC-14 前台空响应不崩溃
- 前置条件：前台流程可走到 `signInOrSignBack`；mock 返回 `null`
- 步骤：触发 `runSignInOrBack()`
- 期望结果：
    1. 输出“签到签退结果：”与“请求响应为空，请稍后重试”
    2. 线程不抛 NPE，不崩溃

---

## 覆盖性结论
- 正常路径：已覆盖（签到/签退成功）
- 边界路径：已覆盖（窗口端点）
- 异常路径：已覆盖（登录失败、空数据、空响应、非10000）
- 语义路径：已覆盖（`ALREADY_DONE`、`NO_PENDING`、`OUT_OF_WINDOW`）

## 执行建议
- 优先自动化：先实现 Worker 侧单元测试（返回码断言最稳定）
- 前台侧使用 mock + 日志断言，验证“提示语 + 不崩溃”
- 若后续加入幂等锁，补充“同窗口重复触发”专项回归

# 验证记录

## 本次验证范围
- `runSignInOrBack()`
- `runSignInOrBackForWorker()`
- 时间窗口与返回码映射

## 验证结果
- 登录失败 -> `AUTH_FAIL`（Worker）/前台提示正确
- `studentId == null` -> 前后台均中止
- `signResp == null` -> 前台不崩溃，Worker返回 `RETRY`
- `code != 10000` -> Worker返回 `REMOTE_FAIL_<code>`

## 备注
- 幂等锁暂未实现，已在 findings 中标注为 P1 风险，后续版本跟进


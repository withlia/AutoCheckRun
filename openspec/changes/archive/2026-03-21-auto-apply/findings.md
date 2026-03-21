# 自动签到/签退漏洞检查结果

## [P0] 前台流程存在空指针崩溃风险（signResp 为空）
- 位置：`app/src/main/java/com/example/autorun/helper/App.java` -> `runSignInOrBack()`
- 触发条件：`request.signInOrSignBack(body)` 返回 `null`
- 实际行为：直接调用 `signInOrSignBack.getMsg()`，可能抛出 NPE
- 期望行为：先判空，输出可重试提示，不崩溃
- 影响范围：前台手动触发签到/签退场景
- 修复建议（最小改动）：增加 `null` 判断；为空时提示“请求响应为空，请稍后重试”
- 回归测试点：mock `signInOrSignBack` 返回 `null`，确认 UI 无崩溃且提示正确

## [P1] studentId 为空时未做防御，可能导致错误请求
- 位置：`app/src/main/java/com/example/autorun/helper/App.java` -> `runSignInOrBack()`、`runSignInOrBackForWorker()`
- 触发条件：登录成功但 `userInfo.getStudentId()` 为 `null`
- 实际行为：`String.valueOf(studentId)` 变成 `"null"`，继续请求 `getSignInTf`
- 期望行为：识别为鉴权数据不完整并中止流程
- 影响范围：前台与 Worker 全路径
- 修复建议（最小改动）：读取 `studentId` 后立即判空；前台提示失败，Worker 返回 `AUTH_FAIL`
- 回归测试点：mock `studentId = null`，确认前台中止、Worker 返回 `AUTH_FAIL`

## [P1] Worker 将所有业务失败统一为 RETRY，错误语义丢失
- 位置：`app/src/main/java/com/example/autorun/helper/App.java` -> `runSignInOrBackForWorker()`
- 触发条件：签到/签退接口返回 `code != 10000`
- 实际行为：统一返回 `RETRY`
- 期望行为：区分可重试与不可重试；至少保留服务端 code
- 影响范围：自动任务可观测性、告警准确性、重试效率
- 修复建议（最小改动）：返回 `REMOTE_FAIL_<code>`；仅空响应/网络异常返回 `RETRY`
- 回归测试点：mock `code=40013`，确认返回 `REMOTE_FAIL_40013` 而非 `RETRY`

## [P1] 时间窗口内缺少幂等保护，存在重复请求风险
- 位置：`app/src/main/java/com/example/autorun/helper/App.java` -> `runSignInOrBackForWorker()`（调度触发路径）
- 触发条件：同一签到/签退窗口内被多次调度
- 实际行为：可能多次发起同类请求
- 期望行为：同一日同一动作成功后不再重复请求
- 影响范围：服务端风控、失败噪音、任务稳定性
- 修复建议（最小改动）：增加“日期 + signType”本地成功标记；命中则直接返回 `ALREADY_DONE`
- 回归测试点：同窗口连续触发两次，第二次不再调用接口

## [P2] 日志格式不规范影响排障可读性
- 位置：`app/src/main/java/com/example/autorun/helper/App.java` -> `runSignInOrBack()`
- 触发条件：打印待签到俱乐部信息
- 实际行为：`"待签到俱乐部：{}" + signInTf`
- 期望行为：统一日志格式，无无效占位符
- 影响范围：调试与日志检索
- 修复建议（最小改动）：改为 `"待签到俱乐部：" + signInTf`
- 回归测试点：检查日志输出字符串格式

## [P2] signStatus 语义强依赖服务端约定，缺少显式约束记录
- 位置：`openspec/specs/auto-sign.md` 与 `App.java` 的动作判定分支
- 触发条件：服务端字段语义变更（例如 signStatus 编码变化）
- 实际行为：可能误签/漏签
- 期望行为：规范中明确字段语义来源与版本假设，运行时记录关键状态
- 影响范围：策略稳定性与长期维护
- 修复建议（最小改动）：在 spec 中补充字段语义说明；日志增加 `signStatus/signInStatus/signBackStatus`
- 回归测试点：模拟状态值变化，确认不会静默执行错误动作

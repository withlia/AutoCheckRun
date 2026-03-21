# 自动签到/签退规范（Auto Sign Spec）

## 1. 输入
- 当前时间 `now`
- 用户登录态（通过账号密码换取 token）
- 俱乐部任务状态 `signInTf`：
    - `signStatus`
    - `signInStatus`
    - `signBackStatus`
    - `activityId/latitude/longitude/studentId`

## 2. 时间窗口规则
- 签到窗口：
    - 07:50:00 - 08:00:00（含边界）
    - 17:50:00 - 18:00:00（含边界）
- 签退窗口：
    - 08:50:00 - 09:00:00（含边界）
    - 18:50:00 - 19:00:00（含边界）
- 非窗口时间：不执行签到/签退请求

## 3. 动作决策规则
1. 登录失败 -> `AUTH_FAIL`
2. `signInTf == null` -> `NO_PENDING`
3. 不在窗口 -> `OUT_OF_WINDOW`
4. 当 `signInStatus == "1" && signBackStatus == "1"` -> `ALREADY_DONE`
5. 在签到窗口：
    - `signStatus == "1"` -> 发起签到（`signType = "1"`）
    - 其他 -> `NO_PENDING`
6. 在签退窗口：
    - `signInStatus == "1" && signStatus == "2"` -> 发起签退（`signType = "2"`）
    - 其他 -> `NO_PENDING`

## 4. 返回结果映射
- 请求响应为空 -> `RETRY`
- 响应 `code == 10000` -> `SUCCESS`
- 其他 code -> `RETRY`（建议后续细化为 `REMOTE_FAIL_xxx`）

## 5. 一致性要求
- 前台与 Worker 逻辑必须一致（判定条件和返回语义一致）
- 每次成功登录后必须刷新 token
- 不允许在非窗口时段发起签到/签退请求

## 6. 漏洞检查点（最小集）
1. 时间边界是否误判（端点包含）
2. `signStatus` 语义变化是否导致误签
3. `studentId == null` 时是否保护
4. 响应 null 时是否可重试
5. `code != 10000` 是否丢失错误细节
6. 同一分钟内重复触发是否幂等
7. 前后台返回语义是否一致
8. 网络抖动时是否出现“假成功”

## 2. 字段语义定义 (Field Semantics)
- **signStatus** (服务端下发状态):
    - `"1"`: 可签到 (Available for Sign In)
    - `"2"`: 可签退 (Available for Sign Back), 前置条件是已签到
- **signType** (请求参数):
    - `"1"`: 执行签到
    - `"2"`: 执行签退

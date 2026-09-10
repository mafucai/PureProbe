# PureProbe 交付报告（v0.2.6 终版 · 2026-09-10）

> 状态：~~停止修复，归档~~ → **2026-09-10 重启：根因已定位并修复（见文末「七、重启补遗」）**
> 仓库：https://github.com/mafucai/PureProbe · 最新可用 APK：build-15（v0.2.6）

## 一、最终真机状态（v0.2.6, build-15）

| 指标 | 数值 |
|---|---|
| 订阅节点 | 72（52 vless + 17 hysteria2 + 3 ss，来自 dash.pqjc.site 订阅） |
| 已测 | 72/72（漏斗调度、并发、进度、缓存全部工作） |
| 干净 | 0 |
| 异常 | 72（全部判"不通"） |

**对照**：同一订阅、同一手机，用户自己的代理客户端翻墙正常——证明节点大部分是活的，App 判定链路仍有一环未打通。

## 二、已交付并验证的能力（真机确认）

1. ✅ 订阅管理：添加/删除/刷新（含"已存在自动重拉"）
2. ✅ mihomo 内核打包与进程管理（jniLibs + legacy packaging + destroy/waitFor）
3. ✅ 订阅下载（clash.meta UA，内核 provider 拉取，518KB YAML/72节点）
4. ✅ JS 桥异步化（重活后台线程，桥永不阻塞，按钮不死）
5. ✅ 漏斗调度引擎（并发8-16、抖动、增量缓存、可中途停止）
6. ✅ 结果可视化（实时进度+单节点结果合并+排行榜）
7. ✅ 回调 JSON 安全（quote 转义 + norm 兼容，无 [object Object]）
8. ✅ 明文白名单（回环 + ip-api）

## 三、未打通的关键链路（本次归档的核心未知）

**现象**：select 全部 204、探活请求全部发出、72 个节点全部判死；但用户直连代理正常。

**已实测排除的原因**（本机真内核 + 用户订阅复现）：
- ~~select 无效~~（mode:rule+MATCH,PROBE 修复后出口真实切换，沙盒实测 ✅）
- ~~本地 DNS 污染~~（HTTP CONNECT 远程解析，沙盒 ws 节点 204 通过 ✅）
- ~~结果不回显~~（进度实时合并 + onDone 重拉，沙盒 20 测试全过 ✅）
- ~~JSON 回调损坏~~（quote/norm 已修 ✅）

**剩余嫌疑（按可能性排序，未验证）**：
1. **Android WebView/Java HTTP 栈与内核混合端口的兼容差异**——沙盒是 curl/python 验证的，Java HttpURLConnection 的 CONNECT 实现在真机上有未观察到的差异（如 TLS 指纹、HTTP 版本）
2. **手机本地网络环境**——探活虽走内核，但 CONNECT 目标域名的解析/出网依赖手机 DNS；用户手机若开了其他 VPN/全局代理，127.0.0.1:7900 的流量可能被劫持
3. **内核在 Android proot/手机上的出站差异**——TUN 相关、UID 规则、蜂窝网络路由
4. **超时阈值**——手机网络 RTT 高，5s 超时 + 每节点 3 次请求可能不够宽容

**建议的下次排查顺序**（若重启项目）：
1. 真机上用 `adb logcat` 抓内核 warning 日志（沙盒里看到的是 REALITY auth failed / context deadline，真机的错误信息会直接告诉我们卡在哪层）
2. 在 App 里加"诊断模式"：对单个节点同时显示 select 结果、探活 code、内核日志行
3. 对比测试：探活 URL 换成 `http://www.gstatic.com/generate_204`（明文 http 排除 TLS 因素）

## 四、教训沉淀（写给下一个接手的人）

1. **任何内核 API 行为必须本机起真内核验证**——本次"mode:global 空操作"靠猜错了两次，起真内核 10 分钟实锤
2. **Java Proxy.Type.SOCKS 会本地解析 DNS**，翻墙探活必须 HTTP CONNECT
3. **Android 明文策略连回环也拦**，network_security_config 必须白名单 127.0.0.1
4. **AGP8 jniLibs 必须 useLegacyPackaging**，否则 .so 不解压、exec 失败
5. **JS 桥线程串行**——重活必须后台线程 + 锁不跨网络等待
6. **evaluateJavascript 传 JSON 必须引号包裹转义**，否则 eval 语义歧义
7. 环境会被重置：代码只在 GitHub 才安全

## 五、资产清单

| 资产 | 位置 |
|---|---|
| 仓库（代码+文档+workflow） | github.com/mafucai/PureProbe |
| APK（可安装，功能部分可用） | build-15 Release |
| 签名 | GitHub Secrets（KEYSTORE_* 三件套，与 RelayScope 同） |
| 图标生成器 | scripts/gen_icon.py（可复用于新项目） |
| 沙盒复现环境 | /tmp/mihomo-test/（真内核+订阅，若环境已清可按本文档重建） |
| 决策记录 | 本文档 + PROJECT_RULES.md 失败台账（16 条） |

---

## 七、重启补遗（2026-09-10 · 并发竞争根因实锤 + 修复）

### 7.1 取证方法（钩子式机械取证，全程零猜测）

用 code-guard/钩子工程的"机械验证"思路重启排查：沙盒起真内核 v1.19.30 + 用户订阅（providers/sub.yaml 72节点）+ App 精确配置（app-sim.yaml: MATCH,PROBE + provider + health-check off），逐环节实测。

### 7.2 实测数据（决定性）

| 实验 | 配置 | 结果 |
|---|---|---|
| 串行 select→探活 ×10 | GLOBAL 手动切 | **10/10 全部 204** |
| 并发 8 select→探活 ×20 | 漏斗同款并发 | **仅 8/20 通过，12 个 000** |
| 内核组测 API ×72 | `GET /group/PROBE/delay` | **一次调用 5.1s，27 个节点有延迟值** |
| Java 同款裸 CONNECT+TLS | 模拟 HttpURLConnection | CONNECT 200 → TLS → **204**（排除 Java 栈差异） |

### 7.3 根因

**select 是全局状态，与并发探活互相踩踏**：漏斗并发 8-16 时，"切到节点 B"的瞬间，节点 A 的请求还在飞——所有在途请求实际测的是最后选中节点或连接竞争态 → 大量超时(000) → 全部判死。这解释了全部真机现象（72/72 判死 + select 均 204 + 用户直连正常）。

### 7.4 修复（v0.3.0，已改码）

**两阶段漏斗**（TestEngine.java 重构 + MihomoApiClient.groupDelay 新增）：
- **阶段1（L1 死活）**：`GET /group/PROBE/delay` 一次调用，内核自己并发测全组——天然并发安全。有延迟值=存活；无=dead("5s内无响应")。实测 72 节点 5.1s 出结果。
- **阶段2（L2 污染 + L3 出口IP）**：仅对存活节点（实测 72→27）**串行** select+探活——数量锐减后串行不慢，且无竞争。
- progress 回调协议不变；concurrency 参数废弃（保留签名兼容）。

### 7.5 改动文件与备份

| 文件 | 变更 | 备份 |
|---|---|---|
| `app/src/main/java/com/pureprobe/app/MihomoApiClient.java` | +groupDelay() | .bak-concurrency |
| `app/src/main/java/com/pureprobe/app/TestEngine.java` | start() 两阶段重构，probeNode→probeNodeL2L3，删并发池 | .bak-concurrency |

### 7.6 待真机验证

- [ ] GitHub Actions 出 APK（v0.3.0）→ 真机重测 72 节点
- [ ] 预期：存活节点应显示真实延迟（不再 72/72 判死）
- [ ] 若仍异常：抓内核日志（log-level 已在 app-sim 验证 info 可用）+ App 内诊断模式（见「三」建议 2）

### 7.7 真机验证结果（2026-09-10，主人实测）

**✅ 成功**。并发竞争根因修复确认有效：72 节点不再全判死，存活节点显示真实延迟。
v0.3.0（build-18）从归档状态正式复活为本项目的修复版本。

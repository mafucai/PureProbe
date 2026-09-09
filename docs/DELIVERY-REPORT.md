# PureProbe 交付报告（v0.2.6 终版 · 2026-09-10）

> 状态：**停止修复，归档**。主人决定不再继续排查。
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

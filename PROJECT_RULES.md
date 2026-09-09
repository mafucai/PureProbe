# PureProbe 节点体检 — 项目规则

> **状态：已归档（2026-09-10），主人决定停止修复。** 最终状态/剩余嫌疑/重启指引 → `docs/DELIVERY-REPORT.md`
> 定位：给主人个人的 Android 工具 App。输入机场订阅链接，自动逐节点体检（死活 → 污染 → 出口 IP 纯净度），产出"干净节点"排行榜和可导出的排除名单。
> 仓库：github.com/mafucai/PureProbe · 编译：仅 GitHub Actions 云端，本地零 Android SDK。

## 铁律

1. **设计已确认（2026-09-08）**：漏斗四层架构 + mihomo 内核 + WebView UI。改设计必须先汇报。
2. **改前备份**：任何文件修改前 `cp x x.bak-日期`；>500 行文件分块读写。
3. **推送纪律**：本地验证全绿 → 给主人看 → 确认后才 push 触发 Actions。不经 2-3 步直接 push 视为违规。
4. **编译前过 Skills 检查门禁**（webapp-testing / dom-static-check / debugger 按改动命中），全绿才编译。
5. **流量红线**：默认并发 ≤8；每节点每轮探活请求 ≤3 个小请求（204 级别）；测速默认关闭；IP 纯净度查询按出口 IP 去重 + 24h 缓存。禁止对机场大流量轰炸（防风控封号——这是本项目存在的前提）。
6. **订阅链接只存本机**（App 私有目录），禁止写日志、禁止上传任何服务器、禁止打进备份提交。
7. **GPL 合规**：mihomo 是 GPLv3，打包其二进制必须在仓库显著位置放 LICENSE-attribution 和源码链接；本项目自身代码不复制 GPL 项目源码，只借鉴思路。
8. **权限最小化**：只用 INTERNET（+未来通知按需）。不要 VPN 权限（mihomo 只开本地 127.0.0.1 端口，不做全局代理）。
9. 每次 Web/JS 改动跑 `python3 scripts/preflight.py`，全绿才算完成。
10. 失败必沉淀：构建/功能失败按「失败 → 根因 → 应对」三段式记入本文档末尾表格。
11. **图标已定**：蓝紫渐变圆角方 + 白盾 + 渐变对勾（呼应 UI 主色）。来源 `scripts/gen_icon.py`，改图标跑脚本重生成 + show_image 给主人确认，禁止手工改 PNG。5 密度 mipmap 已生成。

## 模块边界

| 目录 | 职责 | 不许做 |
|---|---|---|
| `app/src/main/java/.../MihomoManager` | 内核生命周期（启动/停止/健康检查） | 不做测试逻辑 |
| `.../TestEngine` | 漏斗测试调度 + 结果判定 | 不碰 UI、不直接管内核进程 |
| `.../SubStore` | 订阅增删 + 结果缓存持久化 | 不做网络请求 |
| `app/src/main/assets/js/` | 全部 UI（WebView + bridge-sim） | 不写死任何节点/订阅数据 |
| `app/src/main/jniLibs/` | 只放 mihomo 官方 release 二进制改名 libmihomo.so | 不自行编译内核 |

## 失败经验沉淀表

| 失败 | 根因 | 应对 |
|---|---|---|
| Run#3 (build-7) v0.1.3 真机仍"按钮全死" + JS 报 "[object Object] is not valid JSON" | 双根因：① refreshSubscription 的 synchronized 跨 20s 网络等待持锁，桥线程全堵；② evaluateJavascript 拼接 JSON 无引号包裹，eval 语义歧义 | ① 网络等待零持锁（SubscriptionRepo）+ refreshing 防重入；② Java 侧 quote() 转义成 JS 字符串字面量 + JS norm() 统一 parse。教训：Android 桥回调必须引号包裹；锁永不跨网络 |
| build-11 (v0.2.2) 真机"体检完成但已测0" | 双根因（本机起真内核复现铁证）：① 内核配置 PROBE 组只含 AUTOPOOL，`select(provider节点)` 全 400 "proxy not exist" → 全标 dead；② 前端 onDone 不重拉数据，进度不带结果 → UI 永远"未测" | ① PROBE 组直接 `use:[sub]` 吃 provider 全部节点（本机验证 select 204 成功）；② 进度回调实时带单节点结果 + onDone 重拉全量（v0.2.3）。教训：内核 API 行为必须本机起真内核验证，不能靠猜 |
| build-10 (v0.2.1) 真机"订阅拉取失败 provider文件大小=517996" | **订阅实际下载成功**；Java 读内核 API 用 `http://127.0.0.1` 被 Android 明文策略拦截（连回环也拦） | network_security_config 白名单 127.0.0.1/localhost/ip-api.com（v0.2.2）。教训：targetSdk 高于 23 默认禁明文，回环 HTTP 也要白名单 |
| Run#34204801806 下载内核 404 | mihomo android 资产名是 `mihomo-android-arm64-v8-版本.gz`（多了 -v8），按猜的 `arm64` 写 URL 404 | 下载前先查 release 真实资产名（API），workflow 已修正 |
| Run#34205166907 编译失败 | ① `Process.pid()` 是 Java 9 API，Android Process 类没有；② SubStore 漏 import InputStream | ① 改用 `proc.destroy()+waitFor(3s)+destroyForcibly()`；② 补 import。教训：本地无 javac，写 Android 代码避免 Java 9+ Process API |
| build-13 (v0.2.4) 真机"全部不通"但用户代理正常可用 | **Java SOCKS 代理在本地解析 DNS**（gstatic 被污染解析成假 IP→直连失败→全标 dead）；且全 dead 结果被增量缓存→之后"无待测节点"永远测不了（bug#15叠加） | ① 探活改走 mihomo **混合端口 HTTP CONNECT**（域名由内核远程解析，与用户翻墙行为一致，本机双204验证）；② 增量缓存只跳过 clean，dead/polluted 每轮重测（v0.2.5）。教训：Java Proxy.Type.SOCKS ≠ 远程DNS，翻墙探活必须 HTTP CONNECT |
| build-14 (v0.2.5) 真机仍全dead | **mode:global 下 GLOBAL.now=DIRECT，流量不走 PROBE 组，select 全是空操作**（真内核复现铁证：切节点出口 IP 恒不变） | 配置改 mode:rule + MATCH,PROBE（本机实测：select 后出口真实切换 Singapore）。另：REALITY 节点 authentication failed 为节点侧/兼容问题（指纹 ios/chrome/无 三种均失败），hy2 为沙盒 UDP 阻断（真机待验）→ v0.2.6 加 reason 诊断字段 |

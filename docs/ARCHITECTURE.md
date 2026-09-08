# PureProbe — 架构设计（v1）

## 一句话
机场订阅 → mihomo 内核逐节点拨号 → 漏斗三层判定（死活/污染/IP风险）→ WebView 排行榜。

## 架构总览

```
┌─────────────────────────────────────────────┐
│ Android App (纯 Java, minSdk 26)            │
│                                             │
│  ┌─────────┐   ┌──────────────────────┐    │
│  │ WebView │◄─►│ JavaBridge (Java↔JS) │    │
│  │  UI     │   └──────────┬───────────┘    │
│  │ (assets)│              │                │
│  └─────────┘      ┌───────┴────────┐       │
│                   │   TestEngine   │       │
│                   │ (漏斗调度/并发8)│       │
│                   └───┬────────┬───┘       │
│            ┌──────────┘        └─────────┐ │
│      ┌─────┴──────┐              ┌───────┴──┐
│      │ MihomoMgr  │              │ SubStore │
│      │ 子进程管理  │              │ 订阅+缓存 │
│      └─────┬──────┘              └──────────┘
│            │ exec libmihomo.so (本地 socks 端口)
└────────────┼────────────────────────────────┘
             ▼
  mihomo 内核 (127.0.0.1:7900 socks)
  └─ 配置: proxy-providers 吃订阅URL / 静态文件
     external-controller: 127.0.0.1:7901  ← Java 用 REST API 逐节点切换
```

## 关键设计决策

### D1. mihomo 只做"拨号器"，不做代理
- 内核配置：无 rules 劫持，`allow-lan: false`，只监听 127.0.0.1
- Java 侧逐节点测试：通过 REST API `PUT /proxies/{group}` 切换 select 组到目标节点 → 经 127.0.0.1:7900 socks 发请求 → 读结果 → 换下一个
- App 卸载即干净，无 VPN 服务，无系统代理改动

### D2. 漏斗判定（每节点状态机）
```
未测 → L1探活(204) ──失败──→ [不通]
         │通
         ▼
       L2污染(google generate_204) ──失败──→ [污染]
         │通
         ▼
       L3出口IP: GET ip-api.com/json?fields=...,proxy,hosting
         ├─ proxy=true 或 hosting=true 或风险字段差 → [高风险]
         └─ 干净 → [干净] + 记录延迟
```
- 全部请求经节点 socks 通道发出，超时 5s
- 出口 IP 查询按 IP 去重（先 GET ip-api 拿到 IP 后，同 IP 直接复用结论）

### D3. 内核二进制
- 来源：mihomo 官方 release `mihomo-android-arm64-vX.Y.Z.gz`（gunzip 后改名 `libmihomo.so`）
- 放 `app/src/main/jniLibs/arm64-v8a/`，运行时从 `context.applicationInfo.nativeLibraryDir + "/libmihomo.so"` exec（绕过 W^X 限制，借鉴 ClashMetaForAndroid）
- 升级内核 = 替换文件，不改代码

### D4. 订阅与缓存
- 订阅列表 + 测试结果：JSON 文件存 `getFilesDir()`
- 缓存结构：`{节点名: {status, latency, exitIp, checkedAt}}`，24h 内且上次"干净/高风险"的节点默认跳过（增量模式可关）
- 订阅刷新：下载订阅内容到本地临时文件交给 mihomo proxy-provider（内核解析，Java 不碰格式）

### D5. 前端（主人钦定顺序）
1. 先做 assets 前端 + bridge-sim，浏览器全绿
2. 模块拆分：`state.js / bridge-sim.js / bridge.js / render.js / test.js / init.js` + 调试面板内联 body 第一行
3. Java 层后写，桥接方法与前端一一对应

## 目录结构（规划）
```
PureProbe/
├── PROJECT_RULES.md RISK_CHECKLIST.md ACCEPTANCE.md LOW_MODEL_TASK_TEMPLATE.md
├── scripts/preflight.py
├── docs/（设计补充、失败案例）
├── app/src/main/
│   ├── AndroidManifest.xml          # 仅 INTERNET
│   ├── java/com/nodeDoctor/
│   │   ├── MainActivity.java        # WebView + Bridge
│   │   ├── MihomoManager.java
│   │   ├── TestEngine.java
│   │   └── SubStore.java
│   ├── jniLibs/arm64-v8a/libmihomo.so
│   └── assets/
│       ├── index.html + css/ + js/（6 模块）
│       └── （mihomo 运行配置由 Java 动态生成写入 filesDir）
├── .github/workflows/apk.yml
├── build.gradle settings.gradle gradle/wrapper/
└── THIRD-PARTY-LICENSES.md
```

## 端口约定
- mihomo socks: 127.0.0.1:7900
- external-controller: 127.0.0.1:7901
- 均仅本机回环，配置里 `bind-address: 127.0.0.1`

## 借鉴来源（合规声明）
| 项目 | 借鉴内容 | 许可 |
|---|---|---|
| faceair/clash-speedtest | 漏斗测试流程、early-stop、按 provider 吃订阅 | GPLv3（只借鉴思路，未抄码） |
| MetaCubeX/mihomo | 内核二进制直接使用（GPLv3 合规引用） | GPLv3 |
| MetaCubeX/ClashMetaForAndroid | nativeLibraryDir 执行内核的做法 | Apache-2.0? （落地时核实） |

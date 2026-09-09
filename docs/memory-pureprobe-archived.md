---
name: pureprobe-archived
description: PureProbe 节点体检 App 归档记录——终态、剩余未解之谜、重启指引、技术教训
scope: project
type: project
---

# PureProbe 归档（2026-09-10）

主人决定停止修复，项目归档。

## 终态
- 仓库 github.com/mafucai/PureProbe，最新 build-15 (v0.2.6)
- 真机：订阅 72 节点全部拉到、漏斗调度全跑通，但 72 个全判"不通"（0 干净）——同一手机用户代理正常，判定链路差最后一环
- 完整终态/剩余嫌疑/重启指引：/workspace/PureProbe/docs/DELIVERY-REPORT.md
- 失败台账 16 条：/workspace/PureProbe/PROJECT_RULES.md

## 修复过的问题（16个，全有真机/真内核验证）
内核404资产名(arm64-v8) / Process.pid()不存在 / jniLibs不打包→useLegacyPackaging / 明文HTTP拦回环→network_security_config / JS桥串行阻塞→后台线程 / synchronized跨网络等待→零持锁 / evaluateJavascript JSON必须quote / 订阅已存在死锁 / 订阅异步下载没等→轮询20s / DNS污染→HTTP CONNECT远程解析 / mode:global下GLOBAL=DIRECT select全空操作→mode:rule+MATCH,PROBE（真内核实测修复）

## 未解之谜（重启时先查）
真机 select 204 + 探活发出但全 dead。剩余嫌疑：①Java HttpURLConnection CONNECT 在真机的兼容差异 ②手机其他 VPN/代理劫持 127.0.0.1:7900 ③内核出站路由 ④超时太紧。下一步建议：真机 logcat 抓内核 warning 日志 + App 加诊断模式。

## 可复用资产
- gen_icon.py（PIL 图标生成，新项目复制改配色）
- preflight.py（治理检查）
- apk.yml workflow（云端构建+签名+Release）
- 本机真内核复现方法：/tmp/mihomo-test/ 模式（下 mihomo arm64 + 订阅 YAML + REST API 实测）——本次最有效的排查手段

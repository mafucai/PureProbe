# RISK_CHECKLIST — PureProbe

每次交付前逐项过。⛔=红线 ❗=需注意 ✅=已处理

## 法律与许可
- ✅ mihomo GPLv3：仓库放 `THIRD-PARTY-LICENSES.md`（GPLv3 全文链接 + 版权声明 + 源码链接）
- ✅ 本项目代码原创，不复制 clash-speedtest / ClashMetaForAndroid 源码，只借鉴公开文档化的思路
- ❗ 本 App 仅主人个人使用；若公开分发仓库，需含 GPL 义务条款

## 账号安全（最高优先级）
- ⛔ 默认并发 ≤8，UI 上限 16，禁止更高
- ⛔ 每节点每轮：1 次 204 探活 + 1 次 Google 204 + （幸存者）1 次出口 IP 查询
- ⛔ 禁止对机场订阅 URL 高频刷新（手动刷新 + ≥10 分钟间隔）
- ✅ IP 纯净度查询走 ip-api.com 免费接口（45 次/分钟），按唯一出口 IP 去重 + 24h 缓存
- ✅ 节点间随机 0-300ms 抖动，避免机器人特征

## 隐私
- ⛔ 订阅 URL / 节点配置 / 测试结果只存 App 私有目录（`getFilesDir()`），不进日志、不进 git、不上传
- ⛔ 日志脱敏：任何 print/log 不得输出完整订阅 URL（token 部分）
- ✅ 导出功能只导出节点名和结论，不含密钥字段

## 技术风险
- ❗ Android 10+ 禁止 exec app 数据目录文件 → mihomo 必须放 `jniLibs/arm64-v8a/libmihomo.so`，从 `nativeLibraryDir` 执行（借鉴 ClashMetaForAndroid 做法）
- ❗ mihomo 子进程必须在 App 退到后台/被杀时清理（ onDestroy + 进程组 kill ），防僵尸进程耗电
- ❗ 订阅格式差异 → 用 mihomo `proxy-providers` 让内核自己解析（base64/链接列表/YAML 全支持），Java 不写解析器
- ❗ ip-api.com 限速 45/min → 批量查询必须节流 + 失败降级（查不到就标"未知"，不判污染）
- ✅ WebView 崩溃兜底：业务 JS 报错时调试面板仍可显示（调试面板内联在 body 第一行）

## 构建风险（继承通用清单）
- ✅ 纯 WebView + Java，无 AndroidX/Material 依赖 → 不需要 android.useAndroidX（引依赖时必须补）
- ✅ 签名：keystore base64 存 GitHub Secrets，固定 alias，保证可覆盖安装
- ✅ 交付走 request_file_export 或 GitHub Release 直链，禁 cp /sdcard/Download

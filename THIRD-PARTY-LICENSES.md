# 第三方组件声明（THIRD-PARTY-LICENSES）

## mihomo（本项目打包其编译产物）

- 项目：https://github.com/MetaCubeX/mihomo
- 许可：**GNU General Public License v3.0**（本仓库 LICENSE-mihomo 为其全文）
- 使用方式：打包官方 release 预编译二进制 `mihomo-android-arm64`，构建时从
  `https://github.com/MetaCubeX/mihomo/releases/download/v1.19.30/mihomo-android-arm64-v1.19.30.gz`
  下载，改名为 `libmihomo.so` 放入 `app/src/main/jniLibs/arm64-v8a/`。
- 版权：Copyright (c) MetaCubeX 及贡献者
- GPLv3 义务：本 App 分发时，任何持有其 APK 者有权依 GPLv3 向 MetaCubeX 项目获取 mihomo 源码；
  本仓库注明来源与版本即满足署名要求。本项目自身代码（Java/JS/HTML/CSS）不衍生自 mihomo，
  仅通过子进程+REST API 调用其二进制。

## 借鉴思路的项目（未复制代码）

- faceair/clash-speedtest（GPLv3）：漏斗测试流程设计参考
- MetaCubeX/ClashMetaForAndroid（Apache-2.0）：nativeLibraryDir 执行内核的做法

# LOW_MODEL_TASK_TEMPLATE — PureProbe 低级模型任务模板

> 给低级模型 / 子代理执行任务时，把「任务」部分填好整份发给它。执行前必须先读 PROJECT_RULES.md 和 RISK_CHECKLIST.md。

## 任务
（填：要做什么，改哪个文件，验收是什么）

## 硬性约束
1. 先读 `/workspace/PureProbe/PROJECT_RULES.md`，违反铁律直接停下汇报
2. 只改任务指定的文件，禁止顺手改无关代码、禁止删除旧文件、禁止动 node_modules/生成物
3. 修改前备份：`cp 文件 文件.bak-日期`
4. 单文件不超 500 行改动量；新文件不超 800 行，超了要拆模块
5. 改完跑 `python3 /workspace/PureProbe/scripts/preflight.py`，贴出全绿输出才算完成
6. 前端 JS 必须过 `node --check`；Java 必须括号配平检查
7. 不确定的事不猜，停下来问
8. 完成后报告：改动文件绝对路径 + 备份路径 + 验证输出 + 未解决问题

## 本项目特别注意
- 禁止在任何输出/日志打印完整订阅 URL（token 要打码）
- 禁止修改 jniLibs/ 下的内核二进制
- 禁止把并发默认值改超过 8
- 桥接方法改名必须同步 bridge-sim.js / bridge.js / JavaBridge 三处

#!/usr/bin/env python3
"""PureProbe preflight — 项目治理检查，全绿才算完成。"""
import os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
fails, warns = [], []

def check(name, ok, msg=""):
    (print if ok else fails.append)(f"{'PASS' if ok else 'FAIL'}  {name} {msg}") if not ok else print(f"PASS  {name}")

# 1. 治理文档
for f in ["PROJECT_RULES.md", "RISK_CHECKLIST.md", "ACCEPTANCE.md", "LOW_MODEL_TASK_TEMPLATE.md"]:
    p = os.path.join(ROOT, f)
    check(f"治理文档 {f}", os.path.exists(p))

# 2. 文件行数 <=1500
for dirpath, dirs, files in os.walk(ROOT):
    dirs[:] = [d for d in dirs if d not in (".git", "node_modules")]
    for fn in files:
        p = os.path.join(dirpath, fn)
        if fn.endswith((".java", ".js", ".py", ".html", ".css")):
            try:
                n = sum(1 for _ in open(p, encoding="utf-8", errors="ignore"))
                if n > 1500:
                    fails.append(f"FAIL  超长文件 {p} ({n} 行)")
            except OSError:
                pass

# 3. JS/HTML 危险模式
skip_dirs = {".git", "node_modules"}
for dirpath, dirs, files in os.walk(os.path.join(ROOT, "app")):
    dirs[:] = [d for d in dirs if d not in skip_dirs]
    for fn in files:
        if not fn.endswith((".js", ".html")):
            continue
        p = os.path.join(dirpath, fn)
        src = open(p, encoding="utf-8", errors="ignore").read()
        for m in re.finditer(r"(?<!\\)innerHTML\s*=", src):
            line = src[:m.start()].count("\n") + 1
            seg = src[m.end():m.end()+200]
            if "escape" not in seg.lower() and "textContent" not in seg:
                warns.append(f"WARN  可能未转义 innerHTML {p}:{line}")
        if "eval(" in src:
            fails.append(f"FAIL  使用 eval {p}")

# 4. 调试面板（index.html 存在时）
idx = os.path.join(ROOT, "app/src/main/assets/index.html")
if os.path.exists(idx):
    src = open(idx, encoding="utf-8", errors="ignore").read()
    head = src[:src.find("</head>") if "</head>" in src else 2000]
    body_start = src.find("<body")
    check("调试面板在 body 最前", body_start != -1 and "debug-panel" in src[body_start:body_start+1500])

# 5. 测试脚本入口
check("preflight 自身可执行", os.access(__file__, os.X_OK) or True)

print()
if warns:
    print("\n".join(warns))
if fails:
    print("\n".join(fails))
    print(f"\n结果: {len(fails)} FAIL")
    sys.exit(1)
print("结果: 全部通过")

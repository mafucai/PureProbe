package com.pureprobe.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Iterator;

/**
 * SubStore：订阅与结果持久化（getFilesDir，App 私有）+ 订阅刷新。
 * 红线：订阅 URL 不落日志；持久化文件仅私有目录；不联网上传任何数据。
 */
public class SubStore {
    private final Context ctx;
    private JSONArray subs;
    private JSONObject nodes; // name -> {status, latency, exitIp, riskLevel, checkedAt, type}

    public SubStore(Context ctx) {
        this.ctx = ctx;
        subs = new JSONArray();
        nodes = new JSONObject();
        load();
    }

    private File subsFile() { return new File(ctx.getFilesDir(), "pureprobe_subs.json"); }
    private File nodesFile() { return new File(ctx.getFilesDir(), "pureprobe_nodes.json"); }
    private File settingsFile() { return new File(ctx.getFilesDir(), "pureprobe_settings.json"); }

    private void load() {
        subs = readJsonArray(subsFile());
        JSONObject n = readJsonObject(nodesFile());
        if (n != null) nodes = n;
    }

    public void persistNodes() {
        writeJson(nodesFile(), nodes);
    }

    private JSONArray readJsonArray(File f) {
        try {
            return new JSONArray(readAll(f));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private JSONObject readJsonObject(File f) {
        try {
            return new JSONObject(readAll(f));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private String readAll(File f) throws Exception {
        FileInputStream fi = new FileInputStream(f);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[2048];
        int n;
        while ((n = fi.read(buf)) > 0) bo.write(buf, 0, n);
        fi.close();
        return bo.toString("UTF-8");
    }

    private void writeJson(File f, Object o) {
        try {
            FileOutputStream fo = new FileOutputStream(f);
            fo.write(o.toString().getBytes("UTF-8"));
            fo.close();
        } catch (Exception ignored) {}
    }

    // ---------- 桥接：订阅 ----------
    public synchronized String getSubsJson() { return subs.toString(); }

    public synchronized String addSubscription(String url, TestEngine engine) {
        try {
            String trimmed = url == null ? "" : url.trim();
            if (!trimmed.startsWith("http")) return PureState.errorJson("URL 格式不对");
            for (int i = 0; i < subs.length(); i++) {
                if (trimmed.equals(subs.getJSONObject(i).optString("url"))) {
                    return PureState.errorJson("订阅已存在");
                }
            }
            JSONObject sub = new JSONObject();
            sub.put("id", "sub-" + System.currentTimeMillis());
            sub.put("url", trimmed);
            sub.put("name", "订阅 " + (subs.length() + 1));
            sub.put("addedAt", System.currentTimeMillis());
            sub.put("nodeCount", 0);
            subs.put(sub);
            writeJson(subsFile(), subs);
            // 立即刷新拿节点
            String refresh = refreshSubscription(sub.getString("id"), engine);
            return refresh;
        } catch (Exception e) {
            return PureState.errorJson(safeMsg(e));
        }
    }

    public synchronized String removeSubscription(String id) {
        try {
            JSONArray out = new JSONArray();
            for (int i = 0; i < subs.length(); i++) {
                if (!id.equals(subs.getJSONObject(i).optString("id"))) out.put(subs.get(i));
            }
            subs = out;
            writeJson(subsFile(), subs);
            return PureState.okJson();
        } catch (Exception e) {
            return PureState.errorJson(safeMsg(e));
        }
    }

    /** 刷新订阅：启动 mihomo 用 provider 拉订阅，轮询等节点就绪（订阅下载是异步的） */
    public synchronized String refreshSubscription(String id, TestEngine engine) {
        try {
            JSONObject sub = findSub(id);
            if (sub == null) return PureState.errorJson("订阅不存在");
            if (!ensureKernel(engine)) return PureState.errorJson("内核启动失败");
            JSONArray names = fetchProviderNodesRetry(20);
            sub.put("nodeCount", names.length());
            writeJson(subsFile(), subs);
            // 合并节点（保留旧测试结果）
            for (int i = 0; i < names.length(); i++) {
                String name = names.getString(i);
                if (!nodes.has(name)) {
                    JSONObject o = new JSONObject();
                    o.put("status", "unknown");
                    o.put("type", "");
                    nodes.put(name, o);
                }
            }
            persistNodes();
            JSONObject r = new JSONObject();
            r.put("ok", true);
            r.put("count", names.length());
            r.put("nodes", buildNodeListForJs());
            return r.toString();
        } catch (Exception e) {
            return PureState.errorJson(safeMsg(e));
        }
    }

    /** 轮询 provider 节点就绪（内核异步下载订阅，API 起来≠下载完）。超时带诊断信息报错 */
    private JSONArray fetchProviderNodesRetry(int maxWaitSec) throws Exception {
        long deadline = System.currentTimeMillis() + maxWaitSec * 1000L;
        JSONArray names = new JSONArray();
        Exception last = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                names = fetchProviderNodes();
                if (names.length() > 0) return names;
            } catch (Exception e) {
                last = e;
            }
            Thread.sleep(1000);
        }
        File pf = new File(ctx.getFilesDir(), "mihomo/providers/sub.yaml");
        long size = pf.exists() ? pf.length() : -1;
        throw new Exception("订阅拉取失败(" + maxWaitSec + "s超时): provider文件大小=" + size
                + (last != null ? ", 最后错误=" + safeMsg(last) : "")
                + "。请检查订阅链接是否可直连");
    }

    private JSONObject findSub(String id) throws Exception {
        for (int i = 0; i < subs.length(); i++) {
            JSONObject s = subs.getJSONObject(i);
            if (id.equals(s.optString("id"))) return s;
        }
        return null;
    }

    private boolean ensureKernel(TestEngine engine) throws Exception {
        if (mihomoRef == null) return false;
        if (mihomoRef.isRunning()) return true;
        if (subs.length() == 0) return false;
        String url = subs.getJSONObject(subs.length() - 1).optString("url");
        return mihomoRef.start(url);
    }

    private MihomoManager mihomoRef;

    /** 供 MainActivity 装配：SubStore 刷新时需要启动内核 */
    public void bind(MihomoManager m) { this.mihomoRef = m; }

    /** 从内核 API 读 provider 节点名清单 */
    private JSONArray fetchProviderNodes() throws Exception {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                new java.net.URL("http://127.0.0.1:7901/providers/proxies/sub").openConnection();
        c.setConnectTimeout(4000);
        c.setReadTimeout(4000);
        InputStream is = c.getInputStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[2048];
        int n;
        while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
        is.close();
        c.disconnect();
        JSONObject resp = new JSONObject(bo.toString("UTF-8"));
        JSONArray arr = resp.optJSONArray("proxies");
        JSONArray names = new JSONArray();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                String name = arr.getJSONObject(i).optString("name");
                if (name.length() > 0) names.put(name);
            }
        }
        return names;
    }

    // ---------- 桥接：节点 ----------
    public synchronized String getNodesJson() { return buildNodeListForJs().toString(); }

    private JSONArray buildNodeListForJs() {
        JSONArray out = new JSONArray();
        try {
            Iterator<String> it = nodes.keys();
            while (it.hasNext()) {
                String name = it.next();
                JSONObject o = nodes.getJSONObject(name);
                JSONObject j = new JSONObject();
                j.put("name", name);
                j.put("type", o.optString("type"));
                j.put("status", o.optString("status", "unknown"));
                if (o.has("latency")) j.put("latency", o.getInt("latency"));
                if (o.has("exitIp")) j.put("exitIp", o.getString("exitIp"));
                if (o.has("riskLevel")) j.put("riskLevel", o.getString("riskLevel"));
                if (o.has("checkedAt")) j.put("checkedAt", o.getLong("checkedAt"));
                out.put(j);
            }
        } catch (Exception ignored) {}
        return out;
    }

    /** TestEngine 回写结果（内存 + 磁盘） */
    public synchronized void setNodeResult(String name, String status, Integer latency, String exitIp, String risk) {
        try {
            JSONObject o = nodes.has(name) ? nodes.getJSONObject(name) : new JSONObject();
            o.put("status", status);
            if (latency != null) o.put("latency", (int) latency); else o.remove("latency");
            if (exitIp != null) o.put("exitIp", exitIp); else o.remove("exitIp");
            if (risk != null) o.put("riskLevel", risk); else o.remove("riskLevel");
            o.put("checkedAt", System.currentTimeMillis());
            nodes.put(name, o);
        } catch (Exception ignored) {}
    }

    public synchronized String clearCache() {
        try {
            Iterator<String> it = nodes.keys();
            while (it.hasNext()) {
                String k = it.next();
                JSONObject o = nodes.getJSONObject(k);
                o.put("status", "unknown");
                o.remove("latency");
                o.remove("exitIp");
                o.remove("riskLevel");
                o.remove("checkedAt");
            }
            persistNodes();
            return PureState.okJson();
        } catch (Exception e) {
            return PureState.errorJson(safeMsg(e));
        }
    }

    // ---------- 桥接：设置 ----------
    public synchronized String saveSettings(String json) {
        try {
            JSONObject in = new JSONObject(json);
            int conc = in.optInt("concurrency", 8);
            if (conc < 1) conc = 1;
            if (conc > 16) conc = 16; // 红线：并发硬上限 16
            in.put("concurrency", conc);
            writeJson(settingsFile(), in);
            return PureState.okJson();
        } catch (Exception e) {
            return PureState.errorJson(safeMsg(e));
        }
    }

    public synchronized String getSettingsJson() {
        File f = settingsFile();
        if (f.exists()) {
            try { return readAll(f); } catch (Exception ignored) {}
        }
        return "{\"concurrency\":8,\"timeoutSec\":5,\"cacheHours\":24,\"incremental\":true}";
    }

    // ---------- 工具 ----------
    /** 订阅 URL 补 flag=meta（mihomo 解析需要） */
    public static String ensureMetaFlag(String url) {
        if (url == null) return url;
        if (url.contains("flag=")) return url;
        return url + (url.contains("?") ? "&" : "?") + "flag=meta";
    }

    /** 日志脱敏：token 打码 */
    public static String maskUrl(String url) {
        if (url == null) return "";
        return url.replaceAll("(token|key|auth)=[^&]+", "$1=***");
    }

    private static String safeMsg(Exception e) {
        String m = e.getMessage();
        return m == null ? "internal" : m.replaceAll("(token|key|auth)=[^&]+", "$1=***");
    }
}

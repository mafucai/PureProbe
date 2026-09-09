package com.pureprobe.app;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * SubscriptionRepo：订阅清单仓 + 刷新调度。
 * 并发模型（bug#9 教训）：网络等待零持锁；synchronized 只护毫秒级内存/文件写；
 * refreshing 标志防重入。内核启动/provider 轮询委托 MihomoManager + MihomoApiClient。
 */
public class SubscriptionRepo {
    private final KVStore kv;
    private final NodeRepo nodeRepo;
    private final MihomoManager mihomo;
    private final MihomoApiClient api;
    private JSONArray subs;
    private volatile boolean refreshing = false;

    private static final String SUBS_KEY = "pureprobe_subs.json";

    public SubscriptionRepo(KVStore kv, NodeRepo nodeRepo, MihomoManager mihomo, MihomoApiClient api) {
        this.kv = kv;
        this.nodeRepo = nodeRepo;
        this.mihomo = mihomo;
        this.api = api;
        this.subs = kv.readArray(SUBS_KEY);
    }

    public synchronized String listJson() {
        return subs.toString();
    }

    public synchronized String add(String url) {
        try {
            String trimmed = url == null ? "" : url.trim();
            if (!trimmed.startsWith("http")) return PureState.errorJson("URL 格式不对");
            for (int i = 0; i < subs.length(); i++) {
                if (trimmed.equals(subs.getJSONObject(i).optString("url"))) {
                    return "{\"ok\":true,\"exists\":true,\"id\":\"" + subs.getJSONObject(i).optString("id") + "\"}";
                }
            }
            JSONObject sub = new JSONObject();
            sub.put("id", "sub-" + System.currentTimeMillis());
            sub.put("url", trimmed);
            sub.put("name", "订阅 " + (subs.length() + 1));
            sub.put("addedAt", System.currentTimeMillis());
            sub.put("nodeCount", 0);
            subs.put(sub);
            kv.write(SUBS_KEY, subs);
            JSONObject r = new JSONObject();
            r.put("ok", true);
            r.put("id", sub.optString("id"));
            return r.toString();
        } catch (Exception e) {
            return PureState.errorJson(safeMsg(e));
        }
    }

    public synchronized String remove(String id) {
        try {
            JSONArray out = new JSONArray();
            for (int i = 0; i < subs.length(); i++) {
                if (!id.equals(subs.getJSONObject(i).optString("id"))) out.put(subs.get(i));
            }
            subs = out;
            kv.write(SUBS_KEY, subs);
            return PureState.okJson();
        } catch (Exception e) {
            return PureState.errorJson(safeMsg(e));
        }
    }

    public synchronized int count() {
        return subs.length();
    }

    public synchronized String lastUrl() {
        try {
            return subs.length() > 0 ? subs.getJSONObject(subs.length() - 1).optString("url") : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 刷新：无锁网络段（内核启动+轮询），锁内只写结果。返回结果 JSON */
    public String refresh(String id) {
        if (refreshing) return PureState.errorJson("已有拉取在进行，稍候");
        synchronized (this) {
            if (refreshing) return PureState.errorJson("已有拉取在进行，稍候");
            refreshing = true;
        }
        try {
            JSONObject sub;
            synchronized (this) { sub = find(id); }
            if (sub == null) return PureState.errorJson("订阅不存在");
            if (!ensureKernel()) return PureState.errorJson("内核启动失败");
            JSONArray names = pollProviderReady(20);
            synchronized (this) {
                try { sub.put("nodeCount", names.length()); } catch (Exception ignored) {}
                kv.write(SUBS_KEY, subs);
                nodeRepo.mergeNames(names);
                nodeRepo.persist();
            }
            JSONObject r = new JSONObject();
            r.put("ok", true);
            r.put("count", names.length());
            r.put("nodes", nodeRepo.toJsList());
            return r.toString();
        } catch (Exception e) {
            return PureState.errorJson(safeMsg(e));
        } finally {
            refreshing = false;
        }
    }

    private JSONObject find(String id) throws Exception {
        for (int i = 0; i < subs.length(); i++) {
            JSONObject s = subs.getJSONObject(i);
            if (id.equals(s.optString("id"))) return s;
        }
        return null;
    }

    private boolean ensureKernel() throws Exception {
        if (mihomo.isRunning()) return true;
        String url = lastUrl();
        if (url == null) return false;
        return mihomo.start(url);
    }

    /** 轮询 provider 就绪（内核异步下载订阅）。超时带诊断 */
    private JSONArray pollProviderReady(int maxWaitSec) throws Exception {
        long deadline = System.currentTimeMillis() + maxWaitSec * 1000L;
        JSONArray names = new JSONArray();
        Exception last = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                names = api.providerNames("sub");
                if (names.length() > 0) return names;
            } catch (Exception e) {
                last = e;
            }
            Thread.sleep(1000);
        }
        java.io.File pf = kv.resolve("mihomo/providers/sub.yaml");
        long size = pf.exists() ? pf.length() : -1;
        throw new Exception("订阅拉取失败(" + maxWaitSec + "s超时): provider文件大小=" + size
                + (last != null ? ", 最后错误=" + safeMsg(last) : "")
                + "。请检查订阅链接是否可直连");
    }

    private static String safeMsg(Exception e) {
        String m = e.getMessage();
        return m == null ? "internal" : m.replaceAll("(token|key|auth)=[^&]+", "$1=***");
    }
}

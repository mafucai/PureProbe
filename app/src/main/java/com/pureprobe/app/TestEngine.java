package com.pureprobe.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TestEngine：漏斗三层判定调度（薄层）。
 * L1 死活(204) → L2 污染(Google 204) → L3 出口IP风险(ip-api)。
 * 请求委托 ProbeHttpClient，切节点委托 MihomoApiClient，结果写 NodeRepo。
 * 流量红线：并发≤16 硬上限；每节点每轮 ≤3 个 204 级请求；IP 查询按出口去重由缓存自然覆盖。
 */
public class TestEngine {
    private final ProbeHttpClient http;
    private final MihomoApiClient api;
    private final NodeRepo nodeRepo;
    private volatile boolean stopFlag = false;
    private ExecutorService pool;

    private static final String LIVENESS_URL = "https://www.gstatic.com/generate_204";
    private static final String POLLUTION_URL = "https://www.google.com/generate_204";
    private static final String IP_INFO_URL = "http://ip-api.com/json/?fields=status,message,country,as,proxy,hosting,query";

    public TestEngine(ProbeHttpClient http, MihomoApiClient api, NodeRepo nodeRepo) {
        this.http = http;
        this.api = api;
        this.nodeRepo = nodeRepo;
    }

    /**
     * v0.3.0 重构：两阶段漏斗。
     *
     * 阶段1（L1 死活）：groupDelay 一次调用让内核并发测全组，天然并发安全，
     *   根除"select 全局状态 + 并发探活互相踩踏"导致的 72/72 误判死。
     *   有延迟值 → 存活进阶段2；无延迟值 → dead（含真实原因标注）。
     * 阶段2（L2 污染 + L3 出口IP）：仅对存活节点串行 select+探活，
     *   数量已大幅减少（本机实测 72→27），串行不会慢且无竞争。
     */
    public String start(String nodeNamesJson, int concurrency, int timeoutSec,
                        final ProgressCb progressCb, final DoneCb doneCb) {
        try {
            if (PureState.isTesting()) return PureState.errorJson("已有测试在跑");
            JSONArray names = new JSONArray(nodeNamesJson);
            PureState.setTesting(true);
            stopFlag = false;
            final int total = names.length();

            // ── 阶段1: 内核组测全组死活（一次 API 调用，天然并发安全）──
            final java.util.Map<String, Integer> alive = new java.util.LinkedHashMap<String, Integer>();
            JSONObject delays = api.groupDelay("PROBE", timeoutSec * 1000);
            if (delays == null) {
                PureState.setTesting(false);
                return PureState.errorJson("组测API失败（内核未运行或API不通）");
            }
            for (int i = 0; i < total; i++) {
                final String name = names.getString(i);
                Integer delay = delays.has(name) ? delays.optInt(name, -1) : null;
                JSONObject nodeResult;
                if (delay != null && delay > 0) {
                    alive.put(name, delay);
                    // 存活节点先落 L1 结果（延迟来自内核组测），L2/L3 由 probeNodeL2L3 覆盖
                    nodeResult = nodeRepo.setResult(name, "alive", delay, null, null, "L1组测通过");
                } else {
                    nodeResult = nodeRepo.setResult(name, "dead", null, null, null, "L1死活:5s内无响应(组测)");
                }
                if (progressCb != null) progressCb.on(i + 1, total, name, nodeResult);
            }

            // ── 阶段2: 仅存活节点串行 L2/L3 ──
            final int total2 = alive.size();
            final AtomicInteger done = new AtomicInteger(0);
            pool = Executors.newFixedThreadPool(1); // 串行: select 全局状态,不可并发
            for (final String name : alive.keySet()) {
                pool.execute(new Runnable() {
                    public void run() {
                        if (stopFlag) return;
                        JSONObject nodeResult = probeNodeL2L3(name, timeoutSec);
                        int d = done.incrementAndGet();
                        if (progressCb != null) progressCb.on(d, total2, name, nodeResult);
                    }
                });
            }
            new Thread(new Runnable() {
                public void run() {
                    try {
                        while (done.get() < total2 && !stopFlag) Thread.sleep(400);
                    } catch (InterruptedException ignored) {}
                    pool.shutdown();
                    nodeRepo.persist();
                    PureState.setTesting(false);
                    if (doneCb != null) doneCb.on(done.get());
                }
            }, "pp-test-finish").start();

            JSONObject out = new JSONObject();
            try {
                out.put("ok", true);
                out.put("total", total);
                out.put("alive", total2);
                out.put("deadL1", total - total2);
            } catch (Exception ignored) {}
            return out.toString();
        } catch (Exception e) {
            PureState.setTesting(false);
            return PureState.errorJson(e.getMessage());
        }
    }

    /** 阶段2: 对已确认存活的节点做 L2 污染 + L3 出口IP（select 已在阶段1确定存活的前提下串行执行） */
    private JSONObject probeNodeL2L3(String name, int timeoutSec) {
        if (!api.select("PROBE", name)) {
            return nodeRepo.setResult(name, "dead", null, null, null, "select失败(L2)");
        }
        if (!http.expect(POLLUTION_URL, 204, timeoutSec)) {
            return nodeRepo.setResult(name, "polluted", null, null, null, "DNS污染");
        }
        // L3: 出口 IP 风险（ip-api 免费版 45/min，简单节流）
        try { Thread.sleep(1400); } catch (InterruptedException ignored) {}
        JSONObject info = http.getJson(IP_INFO_URL, 6);
        if (info == null) {
            return nodeRepo.setResult(name, "clean", http.lastLatency(), null, null);
        }
        boolean proxy = info.optBoolean("proxy", false);
        boolean hosting = info.optBoolean("hosting", false);
        String exitIp = info.optString("query", null);
        if (proxy || hosting) {
            return nodeRepo.setResult(name, "risky", http.lastLatency(), exitIp, "high");
        }
        return nodeRepo.setResult(name, "clean", http.lastLatency(), exitIp, "low");
    }

    public void stop() {
        stopFlag = true;
        if (pool != null) pool.shutdownNow();
        PureState.setTesting(false);
    }

    public void shutdown() { stop(); }

    public interface ProgressCb { void on(int tested, int total, String current, org.json.JSONObject nodeResult); }
    public interface DoneCb { void on(int tested); }
}

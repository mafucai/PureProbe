package com.pureprobe.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;

/**
 * NodeRepo：节点结果状态仓（内存 + KVStore 持久化）。
 * 单一职责：节点状态 CRUD。锁粒度 = 方法级毫秒写，无网络。
 */
public class NodeRepo {
    private final KVStore kv;
    private JSONObject nodes; // name -> {status, latency, exitIp, riskLevel, checkedAt, type}

    public NodeRepo(KVStore kv) {
        this.kv = kv;
        this.nodes = kv.readObject("pureprobe_nodes.json");
    }

    public synchronized void persist() {
        kv.write("pureprobe_nodes.json", nodes);
    }

    /** 合并节点名单（保留已有测试结果） */
    public synchronized int mergeNames(JSONArray names) {
        try {
            for (int i = 0; i < names.length(); i++) {
                String name = names.getString(i);
                if (!nodes.has(name)) {
                    JSONObject o = new JSONObject();
                    o.put("status", "unknown");
                    o.put("type", "");
                    nodes.put(name, o);
                }
            }
            return names.length();
        } catch (Exception e) {
            return 0;
        }
    }

    public synchronized void clearResults() {
        try {
            Iterator<String> it = nodes.keys();
            while (it.hasNext()) {
                JSONObject o = nodes.getJSONObject(it.next());
                o.put("status", "unknown");
                o.remove("latency");
                o.remove("exitIp");
                o.remove("riskLevel");
                o.remove("checkedAt");
            }
        } catch (Exception ignored) {}
    }

    public synchronized JSONArray setResult(String name, String status, Integer latency, String exitIp, String risk) {
        try {
            JSONObject o = nodes.has(name) ? nodes.getJSONObject(name) : new JSONObject();
            o.put("status", status);
            if (latency != null) o.put("latency", (int) latency); else o.remove("latency");
            if (exitIp != null) o.put("exitIp", exitIp); else o.remove("exitIp");
            if (risk != null) o.put("riskLevel", risk); else o.remove("riskLevel");
            o.put("checkedAt", System.currentTimeMillis());
            nodes.put(name, o);
            return nodeJson(name, o); // 供进度回调实时推给前端
        } catch (Exception ignored) {}
        return null;
    }

    private JSONObject nodeJson(String name, JSONObject o) {
        try {
            JSONObject j = new JSONObject();
            j.put("name", name);
            j.put("type", o.optString("type"));
            j.put("status", o.optString("status", "unknown"));
            if (o.has("latency")) j.put("latency", o.getInt("latency"));
            if (o.has("exitIp")) j.put("exitIp", o.getString("exitIp"));
            if (o.has("riskLevel")) j.put("riskLevel", o.getString("riskLevel"));
            if (o.has("checkedAt")) j.put("checkedAt", o.getLong("checkedAt"));
            return j;
        } catch (Exception e) {
            return null;
        }
    }

    public synchronized JSONArray toJsList() {
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

    public synchronized int count() {
        return nodes.length();
    }
}

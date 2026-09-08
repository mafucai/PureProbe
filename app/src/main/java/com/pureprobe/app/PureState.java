package com.pureprobe.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

/** 共享可变状态与线程安全开关（前端 PureState 的原生侧对应物） */
public final class PureState {
    private static volatile boolean testing = false;

    public static boolean isTesting() { return testing; }
    public static void setTesting(boolean v) { testing = v; }

    private PureState() {}

    /** 安全取字符串 */
    public static String optString(JSONObject o, String key, String def) {
        try { return o.optString(key, def); } catch (Exception e) { return def; }
    }

    public static JSONArray parseArray(String json) throws JSONException {
        return new JSONArray(json);
    }

    public static String errorJson(String msg) {
        try {
            JSONObject o = new JSONObject();
            o.put("ok", false);
            o.put("error", msg);
            return o.toString();
        } catch (JSONException e) {
            return "{\"ok\":false,\"error\":\"internal\"}";
        }
    }

    public static String okJson() { return "{\"ok\":true}"; }
}

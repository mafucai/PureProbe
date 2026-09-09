package com.pureprobe.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;

/**
 * KVStore：纯 JSON 文件读写（私有目录）。单一职责：字节 ↔ JSONObject/JSONArray。
 * 不含业务逻辑、不含网络、不持长锁。
 */
public final class KVStore {
    private final Context ctx;

    public KVStore(Context ctx) {
        this.ctx = ctx;
    }

    public JSONArray readArray(String name) {
        try {
            return new JSONArray(readAll(file(name)));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    public JSONObject readObject(String name) {
        try {
            return new JSONObject(readAll(file(name)));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public void write(String name, Object o) {
        try {
            FileOutputStream fo = new FileOutputStream(file(name));
            fo.write(o.toString().getBytes("UTF-8"));
            fo.close();
        } catch (Exception ignored) {}
    }

    public boolean exists(String name) {
        return file(name).exists();
    }

    public String readRaw(String name) throws Exception {
        return readAll(file(name));
    }

    public File resolve(String name) {
        return file(name);
    }

    private File file(String name) {
        return new File(ctx.getFilesDir(), name);
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
}

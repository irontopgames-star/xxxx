package com.bearfriender.client.render;

import com.bearfriender.BearfrienderMod;
import com.bearfriender.entity.anim.BearAnimation;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Datos de Bearfriender cargados desde los recursos: malla con pesos, esqueleto y animaciones.
 * Se generan con tools/build_bear.py a partir de oso.bbmodel.
 */
public final class BearfrienderModel {
    private static final ResourceLocation MESH = new ResourceLocation(BearfrienderMod.MOD_ID, "mesh/bearfriender.mesh");
    private static final ResourceLocation RIG = new ResourceLocation(BearfrienderMod.MOD_ID, "mesh/bearfriender.rig.json");
    private static final ResourceLocation ANIMS = new ResourceLocation(BearfrienderMod.MOD_ID, "animations/entity/bearfriender.animation.json");

    private static BearfrienderModel instance;
    private static boolean failed;

    // Esqueleto
    public int boneCount;
    public String[] boneNames;
    public int[] parent;
    public float[] pivot;           // 3 por hueso

    // Vértices (únicos por posición)
    public int vertexCount;
    public float[] pos, nrm;        // 3 por vértice
    public int[] joint;             // 4 por vértice
    public float[] weight;          // 4 por vértice

    // Quads: primero los del cuerpo, luego los del cristal de la pantalla
    public int mainQuads, screenQuads;
    public int[] cornerIdx;         // 4 por quad
    public float[] cornerU, cornerV;

    public final Map<String, BearAnimation> animations = new HashMap<>();
    private final Map<String, Integer> boneIndex = new HashMap<>();

    public int bone(String name) {
        Integer i = boneIndex.get(name);
        return i == null ? -1 : i;
    }

    /** Devuelve el modelo cargado, o null si falló la carga (se registra el error una sola vez). */
    public static synchronized BearfrienderModel get() {
        if (instance == null && !failed) {
            try {
                instance = load(Minecraft.getInstance().getResourceManager());
                BearfrienderMod.LOGGER.info("Bearfriender: malla cargada ({} vértices, {} huesos, {} animaciones).",
                        instance.vertexCount, instance.boneCount, instance.animations.size());
            } catch (IOException | RuntimeException e) {
                failed = true;
                BearfrienderMod.LOGGER.error("Bearfriender: no se pudo cargar la malla del oso", e);
            }
        }
        return instance;
    }

    /** Se llama al recargar recursos (F3+T). */
    public static synchronized void invalidate() {
        instance = null;
        failed = false;
    }

    private static BearfrienderModel load(ResourceManager rm) throws IOException {
        BearfrienderModel m = new BearfrienderModel();
        loadRig(rm, m);
        loadMesh(rm, m);
        loadAnimations(rm, m);
        return m;
    }

    private static void loadRig(ResourceManager rm, BearfrienderModel m) throws IOException {
        try (InputStream in = rm.getResourceOrThrow(RIG).open();
             InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonArray arr = JsonParser.parseReader(r).getAsJsonObject().getAsJsonArray("bones");
            int n = arr.size();
            m.boneCount = n;
            m.boneNames = new String[n];
            m.parent = new int[n];
            m.pivot = new float[n * 3];
            Map<String, Integer> idx = new HashMap<>();
            for (int i = 0; i < n; i++) {
                JsonObject o = arr.get(i).getAsJsonObject();
                m.boneNames[i] = o.get("name").getAsString();
                idx.put(m.boneNames[i], i);
                JsonElement p = o.get("parent");
                m.parent[i] = (p == null || p.isJsonNull()) ? -1 : idx.get(p.getAsString());
                JsonArray pv = o.getAsJsonArray("pivot");
                for (int k = 0; k < 3; k++) m.pivot[i * 3 + k] = pv.get(k).getAsFloat();
            }
            m.boneIndex.putAll(idx);
        }
    }

    private static void loadMesh(ResourceManager rm, BearfrienderModel m) throws IOException {
        byte[] bytes;
        try (InputStream in = rm.getResourceOrThrow(MESH).open()) {
            bytes = in.readAllBytes();
        }
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        b.position(4);                       // "BEAR"
        int version = b.getInt();
        if (version != 1) throw new IOException("Versión de malla no soportada: " + version);
        m.vertexCount = b.getInt();
        m.mainQuads = b.getInt();
        m.screenQuads = b.getInt();
        int bones = b.getInt();
        if (bones != m.boneCount) throw new IOException("La malla y el esqueleto no coinciden");

        int vc = m.vertexCount;
        m.pos = new float[vc * 3];
        m.nrm = new float[vc * 3];
        m.joint = new int[vc * 4];
        m.weight = new float[vc * 4];
        for (int i = 0; i < vc; i++) {
            for (int k = 0; k < 3; k++) m.pos[i * 3 + k] = b.getFloat();
            for (int k = 0; k < 3; k++) m.nrm[i * 3 + k] = b.getFloat();
            for (int k = 0; k < 4; k++) m.joint[i * 4 + k] = b.get() & 0xFF;
            for (int k = 0; k < 4; k++) m.weight[i * 4 + k] = b.getFloat();
        }
        int corners = (m.mainQuads + m.screenQuads) * 4;
        m.cornerIdx = new int[corners];
        m.cornerU = new float[corners];
        m.cornerV = new float[corners];
        for (int i = 0; i < corners; i++) {
            m.cornerIdx[i] = b.getInt();
            m.cornerU[i] = b.getFloat();
            m.cornerV[i] = b.getFloat();
        }
    }

    private static void loadAnimations(ResourceManager rm, BearfrienderModel m) throws IOException {
        try (InputStream in = rm.getResourceOrThrow(ANIMS).open();
             InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject all = JsonParser.parseReader(r).getAsJsonObject().getAsJsonObject("animations");
            for (Map.Entry<String, JsonElement> e : all.entrySet()) {
                JsonObject a = e.getValue().getAsJsonObject();
                float length = a.get("animation_length").getAsFloat();
                int loop = BearAnimation.LOOP_ONCE;
                JsonElement le = a.get("loop");
                if (le != null && le.isJsonPrimitive()) {
                    if (le.getAsJsonPrimitive().isBoolean()) {
                        loop = le.getAsBoolean() ? BearAnimation.LOOP : BearAnimation.LOOP_ONCE;
                    } else if ("hold_on_last_frame".equals(le.getAsString())) {
                        loop = BearAnimation.HOLD;
                    }
                }
                BearAnimation anim = new BearAnimation(e.getKey(), length, loop, m.boneCount);
                for (Map.Entry<String, JsonElement> be : a.getAsJsonObject("bones").entrySet()) {
                    int bi = m.bone(be.getKey());
                    if (bi < 0) {
                        BearfrienderMod.LOGGER.warn("Animación '{}': el hueso '{}' no existe", e.getKey(), be.getKey());
                        continue;
                    }
                    JsonObject ch = be.getValue().getAsJsonObject();
                    anim.rot[bi] = readTrack(ch.getAsJsonObject("rotation"));
                    anim.pos[bi] = readTrack(ch.getAsJsonObject("position"));
                    anim.scl[bi] = readTrack(ch.getAsJsonObject("scale"));
                }
                m.animations.put(e.getKey(), anim);
            }
        }
    }

    private static BearAnimation.Track readTrack(JsonObject o) {
        if (o == null || o.size() == 0) return null;
        List<float[]> frames = new ArrayList<>();       // {tiempo, x, y, z}
        for (Map.Entry<String, JsonElement> k : o.entrySet()) {
            JsonArray v = k.getValue().getAsJsonArray();
            frames.add(new float[]{Float.parseFloat(k.getKey()), v.get(0).getAsFloat(), v.get(1).getAsFloat(), v.get(2).getAsFloat()});
        }
        frames.sort((p, q) -> Float.compare(p[0], q[0]));
        float[] times = new float[frames.size()];
        float[][] values = new float[frames.size()][3];
        for (int i = 0; i < times.length; i++) {
            float[] f = frames.get(i);
            times[i] = f[0];
            values[i][0] = f[1];
            values[i][1] = f[2];
            values[i][2] = f[3];
        }
        return new BearAnimation.Track(times, values);
    }
}

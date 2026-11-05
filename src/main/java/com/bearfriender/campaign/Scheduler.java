package com.bearfriender.campaign;

import java.util.ArrayList;
import java.util.List;

/**
 * Tareas diferidas del servidor (en ticks). No se guardan: si se cierra el mundo, se pierden.
 * Los efectos que programan son siempre reversibles al cargar (luces y televisores se restauran al iniciar).
 */
public final class Scheduler {
    private record Task(long due, Runnable run) {}

    private static final List<Task> TASKS = new ArrayList<>();
    private static long now;

    private Scheduler() {}

    public static void after(int ticks, Runnable run) {
        TASKS.add(new Task(now + Math.max(0, ticks), run));
    }

    /** Se llama una vez por tick del servidor. */
    public static void tick() {
        now++;
        if (TASKS.isEmpty()) return;
        List<Task> due = new ArrayList<>();
        TASKS.removeIf(t -> {
            if (t.due() <= now) {
                due.add(t);
                return true;
            }
            return false;
        });
        for (Task t : due) {
            try {
                t.run().run();
            } catch (RuntimeException e) {
                com.bearfriender.BearfrienderMod.LOGGER.error("Error en una tarea programada", e);
            }
        }
    }

    public static int pending() { return TASKS.size(); }

    public static void clear() {
        TASKS.clear();
    }
}

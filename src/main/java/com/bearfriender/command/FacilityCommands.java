package com.bearfriender.command;

import com.bearfriender.BearfrienderMod;
import com.bearfriender.structure.FacilityData;
import com.bearfriender.structure.FacilityLayout;
import com.bearfriender.structure.FacilityPlacer;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Map;

/**
 * Comandos para gestionar la instalación (requieren permisos de operador):
 *   /bearfriender facility build            construye en el cielo (zona libre, sin tocar el terreno)
 *   /bearfriender facility build here       construye donde estás, solo si el terreno es natural
 *   /bearfriender facility build here force construye donde estás aunque haya construcciones (las destruye)
 *   /bearfriender facility remove           retira la instalación
 *   /bearfriender facility tp               te lleva a la recepción
 *   /bearfriender facility info             estado y posiciones clave
 *   /bearfriender facility exit open|close  abre o cierra la salida
 */
@Mod.EventBusSubscriber(modid = BearfrienderMod.MOD_ID)
public final class FacilityCommands {
    /** Altura del suelo cuando se construye en el cielo. */
    private static final int SKY_Y = 200;
    private static final int SKY_TRIES = 8;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("bearfriender")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("facility")
                        .then(Commands.literal("build")
                                .executes(c -> build(c.getSource(), false, false))
                                .then(Commands.literal("here")
                                        .executes(c -> build(c.getSource(), true, false))
                                        .then(Commands.literal("force")
                                                .executes(c -> build(c.getSource(), true, true)))))
                        .then(Commands.literal("remove").executes(c -> remove(c.getSource())))
                        .then(Commands.literal("tp").executes(c -> teleport(c.getSource())))
                        .then(Commands.literal("info").executes(c -> info(c.getSource())))
                        .then(Commands.literal("exit")
                                .then(Commands.literal("open").executes(c -> exit(c.getSource(), true)))
                                .then(Commands.literal("close").executes(c -> exit(c.getSource(), false))))));
    }

    private static int fail(CommandSourceStack src, String msg) {
        src.sendFailure(Component.literal(msg));
        return 0;
    }

    private static int ok(CommandSourceStack src, String msg) {
        src.sendSuccess(() -> Component.literal(msg), true);
        return 1;
    }

    // ------------------------------------------------------------------------------------

    private static int build(CommandSourceStack src, boolean here, boolean force) {
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (Exception e) {
            return fail(src, "Este comando debe ejecutarlo un jugador.");
        }
        MinecraftServer server = src.getServer();
        FacilityData data = FacilityData.get(server);
        if (data.isBuilt()) {
            return fail(src, "La instalación ya está construida. Usa '/bearfriender facility remove' antes de crear otra.");
        }
        ServerLevel level = player.serverLevel();
        if (level.dimension() != Level.OVERWORLD) {
            return fail(src, "La instalación solo puede construirse en el Overworld.");
        }

        BlockPos origin;
        if (here) {
            origin = new BlockPos(player.getBlockX(), player.getBlockY() - 1, player.getBlockZ());
            if (origin.getY() + FacilityLayout.MIN_Y < level.getMinBuildHeight()
                    || origin.getY() + FacilityLayout.MAX_Y >= level.getMaxBuildHeight()) {
                return fail(src, "No hay altura suficiente aquí para construir la instalación.");
            }
            if (!force) {
                int bad = FacilityPlacer.countBlocking(level, origin, false);
                if (bad > 0) {
                    return fail(src, "Hay " + bad + " bloques que no parecen naturales en la zona (¿construcciones?). "
                            + "Cambia de lugar o usa '/bearfriender facility build here force' (los destruirá).");
                }
            }
        } else {
            int y = Math.min(SKY_Y, level.getMaxBuildHeight() - FacilityLayout.MAX_Y - 2);
            int x0 = (player.getBlockX() >> 4) << 4;
            int z0 = (player.getBlockZ() >> 4) << 4;
            origin = null;
            for (int i = 0; i < SKY_TRIES && origin == null; i++) {
                BlockPos candidate = new BlockPos(x0 + 64 * i, y, z0);
                if (FacilityPlacer.countBlocking(level, candidate, true) == 0) origin = candidate;
            }
            if (origin == null) {
                return fail(src, "No se encontró una zona libre en el cielo cerca de ti. Prueba desde otro lugar.");
            }
        }

        Map<String, List<BlockPos>> anchors = FacilityPlacer.build(level, origin);
        data.markBuilt(level, origin, anchors);
        BearfrienderMod.LOGGER.info("Instalación construida en {}", origin);
        ok(src, "Instalación construida en " + origin.getX() + " " + origin.getY() + " " + origin.getZ()
                + ". Te llevo a la recepción.");
        return teleport(src);
    }

    private static int remove(CommandSourceStack src) {
        FacilityData data = FacilityData.get(src.getServer());
        if (!data.isBuilt()) return fail(src, "No hay ninguna instalación construida.");
        ServerLevel level = data.level(src.getServer());
        if (level == null) return fail(src, "No se encontró la dimensión de la instalación.");
        FacilityPlacer.remove(level, data);
        data.clear();
        return ok(src, "Instalación retirada.");
    }

    private static int teleport(CommandSourceStack src) {
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (Exception e) {
            return fail(src, "Este comando debe ejecutarlo un jugador.");
        }
        FacilityData data = FacilityData.get(src.getServer());
        if (!data.isBuilt()) return fail(src, "No hay ninguna instalación construida.");
        ServerLevel level = data.level(src.getServer());
        BlockPos spawn = data.anchor("spawn");
        if (level == null || spawn == null) return fail(src, "La instalación no tiene punto de inicio registrado.");
        player.teleportTo(level, spawn.getX() + 0.5, spawn.getY() + 0.07, spawn.getZ() + 0.5, 0.0F, 0.0F);
        return ok(src, "Teletransportado a la recepción.");
    }

    private static int info(CommandSourceStack src) {
        FacilityData data = FacilityData.get(src.getServer());
        if (!data.isBuilt()) return ok(src, "No hay ninguna instalación construida.");
        BlockPos o = data.origin();
        StringBuilder sb = new StringBuilder();
        sb.append("Instalación en ").append(o.getX()).append(' ').append(o.getY()).append(' ').append(o.getZ())
                .append(" | salida ").append(data.isExitOpen() ? "ABIERTA" : "cerrada").append("\nAnclas: ");
        for (Map.Entry<String, List<BlockPos>> e : data.allAnchors().entrySet()) {
            sb.append(e.getKey()).append('×').append(e.getValue().size()).append("  ");
        }
        return ok(src, sb.toString());
    }

    private static int exit(CommandSourceStack src, boolean open) {
        FacilityData data = FacilityData.get(src.getServer());
        if (!data.isBuilt()) return fail(src, "No hay ninguna instalación construida.");
        ServerLevel level = data.level(src.getServer());
        if (level == null) return fail(src, "No se encontró la dimensión de la instalación.");
        FacilityPlacer.applyExit(level, data.origin(), open);
        data.setExitOpen(open);
        return ok(src, open ? "Salida abierta." : "Salida cerrada.");
    }

    private FacilityCommands() {}
}

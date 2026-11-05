package com.bearfriender.command;

import com.bearfriender.BearfrienderMod;
import com.bearfriender.block.TvControl;
import com.bearfriender.campaign.BearDirector;
import com.bearfriender.campaign.BearTuning;
import com.bearfriender.campaign.CampaignData;
import com.bearfriender.campaign.CampaignEvents;
import com.bearfriender.campaign.CampaignManager;
import com.bearfriender.campaign.EventType;
import com.bearfriender.campaign.MissionId;
import com.bearfriender.campaign.NightDefinition;
import com.bearfriender.campaign.Scheduler;
import com.bearfriender.config.BearfrienderConfig;
import com.bearfriender.entity.BearfrienderEntity;
import com.bearfriender.entity.ai.BearState;
import com.bearfriender.structure.FacilityData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;
import java.util.List;

/**
 * Comandos de la campaña (permisos de operador):
 *   /bearfriender campaign start            empieza la campaña (noche 1) y te lleva a la recepción
 *   /bearfriender campaign stop             la detiene y limpia el oso, luces y televisores
 *   /bearfriender campaign status           muestra el estado
 *   /bearfriender campaign night <1-5>      salta a una noche (pruebas)
 *   /bearfriender campaign event <tipo>     dispara un evento ahora (pruebas)
 *   /bearfriender campaign mission <id> <true|false>   marca una misión (pruebas hasta la Fase 6)
 *   /bearfriender campaign tapes <0-3>      cintas destruidas (pruebas)
 *   /bearfriender campaign bear appear [min] [max]   hace aparecer al oso a lo lejos unos segundos (pruebas)
 *   /bearfriender campaign bear hide        lo devuelve a su cámara sellada
 *   /bearfriender campaign bear boss        lo coloca en la sala central (como en la noche 5)
 *   /bearfriender campaign win              simula la derrota del oso: apaga los televisores y abre la salida
 */
@Mod.EventBusSubscriber(modid = BearfrienderMod.MOD_ID)
public final class CampaignCommands {
    private CampaignCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("bearfriender")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("campaign")
                        .then(Commands.literal("start").executes(c -> start(c.getSource())))
                        .then(Commands.literal("stop").executes(c -> stop(c.getSource())))
                        .then(Commands.literal("status").executes(c -> status(c.getSource())))
                        .then(Commands.literal("win").executes(c -> win(c.getSource())))
                        .then(Commands.literal("bear")
                                .then(Commands.literal("appear")
                                        .executes(c -> appear(c.getSource(), 10, 30))
                                        .then(Commands.argument("min", IntegerArgumentType.integer(2, 60))
                                                .then(Commands.argument("max", IntegerArgumentType.integer(3, 80))
                                                        .executes(c -> appear(c.getSource(),
                                                                IntegerArgumentType.getInteger(c, "min"),
                                                                IntegerArgumentType.getInteger(c, "max"))))))
                                .then(Commands.literal("hide").executes(c -> bearHide(c.getSource())))
                                .then(Commands.literal("boss").executes(c -> bearBoss(c.getSource()))))
                        .then(Commands.literal("night")
                                .then(Commands.argument("n", IntegerArgumentType.integer(1, 5))
                                        .executes(c -> night(c.getSource(), IntegerArgumentType.getInteger(c, "n")))))
                        .then(Commands.literal("tapes")
                                .then(Commands.argument("n", IntegerArgumentType.integer(0, 3))
                                        .executes(c -> tapes(c.getSource(), IntegerArgumentType.getInteger(c, "n")))))
                        .then(Commands.literal("event")
                                .then(Commands.argument("tipo", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(EventType.values()).map(Enum::name), b))
                                        .executes(c -> event(c.getSource(), StringArgumentType.getString(c, "tipo")))))
                        .then(Commands.literal("mission")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(MissionId.values()).map(m -> m.id), b))
                                        .then(Commands.argument("hecha", BoolArgumentType.bool())
                                                .executes(c -> mission(c.getSource(), StringArgumentType.getString(c, "id"),
                                                        BoolArgumentType.getBool(c, "hecha"))))))));
    }

    private static int ok(CommandSourceStack src, String msg) {
        src.sendSuccess(() -> Component.literal(msg), true);
        return 1;
    }

    private static int fail(CommandSourceStack src, String msg) {
        src.sendFailure(Component.literal(msg));
        return 0;
    }

    private static int start(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        String err = CampaignManager.startCampaign(src.getServer(), p);
        return err == null ? ok(src, "Campaña iniciada: NOCHE 1.") : fail(src, err);
    }

    private static int stop(CommandSourceStack src) {
        CampaignManager.stopCampaign(src.getServer());
        return ok(src, "Campaña detenida.");
    }

    private static int win(CommandSourceStack src) {
        CampaignData camp = CampaignData.get(src.getServer());
        if (!camp.isActive() || camp.isEnded()) return fail(src, "No hay una campaña en curso.");
        CampaignManager.completeCampaign(src.getServer());
        return ok(src, "Secuencia final iniciada.");
    }

    private static int night(CommandSourceStack src, int n) {
        MinecraftServer server = src.getServer();
        CampaignData camp = CampaignData.get(server);
        FacilityData fac = FacilityData.get(server);
        if (!camp.isActive() || camp.isEnded() || !fac.isBuilt()) return fail(src, "No hay una campaña en curso.");
        ServerLevel level = fac.level(server);
        if (level == null) return fail(src, "No se encontró la instalación.");
        Scheduler.clear();
        CampaignManager.advanceTo(server, level, fac, camp, n);
        return ok(src, "Saltaste a la noche " + n + ".");
    }

    private static int tapes(CommandSourceStack src, int n) {
        CampaignData camp = CampaignData.get(src.getServer());
        camp.setTapesDestroyed(n);
        camp.setMission(MissionId.TAPES, n >= 3);
        CampaignManager.sync(src.getServer(), null);
        return ok(src, "Cintas destruidas: " + n + "/3.");
    }

    private static int mission(CommandSourceStack src, String id, boolean done) {
        MissionId m = MissionId.byId(id);
        if (m == null) return fail(src, "Misión desconocida. Usa: power, tapes o signal.");
        CampaignData camp = CampaignData.get(src.getServer());
        camp.setMission(m, done);
        CampaignManager.sync(src.getServer(), null);
        return ok(src, m.title + ": " + (done ? "completada" : "pendiente"));
    }

    private static int event(CommandSourceStack src, String name) {
        EventType type;
        try {
            type = EventType.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return fail(src, "Evento desconocido. Prueba con la ayuda de autocompletado.");
        }
        MinecraftServer server = src.getServer();
        FacilityData fac = FacilityData.get(server);
        CampaignData camp = CampaignData.get(server);
        if (!fac.isBuilt()) return fail(src, "No hay instalación construida.");
        ServerLevel level = fac.level(server);
        if (level == null) return fail(src, "No se encontró la instalación.");
        List<ServerPlayer> players = CampaignManager.participants(server, level, fac);
        if (players.isEmpty()) return fail(src, "Debes estar dentro de la instalación.");
        // Se ejecuta con valores de prueba (intensidad 2, apariciones de 8 a 20 bloques)
        NightDefinition.ScheduledEvent ev = new NightDefinition.ScheduledEvent(0F, type, 2, 8, 20, 80,
                "Esto es un mensaje de prueba.");
        CampaignEvents.run(new CampaignEvents.Ctx(level, fac, camp, players), ev);
        return ok(src, "Evento " + type.name() + " lanzado.");
    }

    private static int appear(CommandSourceStack src, int min, int max) {
        ServerPlayer p = src.getPlayer();
        MinecraftServer server = src.getServer();
        FacilityData fac = FacilityData.get(server);
        if (p == null || !fac.isBuilt()) return fail(src, "Debes ser un jugador y la instalación debe estar construida.");
        ServerLevel level = fac.level(server);
        if (level == null || CampaignManager.participants(server, level, fac).stream().noneMatch(x -> x == p)) {
            return fail(src, "Debes estar dentro de la instalación.");
        }
        boolean ok = BearDirector.flash(level, fac, CampaignData.get(server), p, min, max, 140);
        return ok ? ok(src, "Bearfriender aparece a lo lejos... míralo.")
                : fail(src, "No encontré un sitio con línea de visión en ese rango. Prueba desde un pasillo o una sala abierta, "
                + "o con otro rango (ej: appear 6 20). Si el oso ya está en escena, usa 'bear hide' primero.");
    }

    private static int bearHide(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        FacilityData fac = FacilityData.get(server);
        ServerLevel level = fac.isBuilt() ? fac.level(server) : null;
        if (level == null) return fail(src, "No hay instalación construida.");
        BearDirector.hide(level, fac, CampaignData.get(server));
        return ok(src, "El oso vuelve a su cámara sellada.");
    }

    private static int bearBoss(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        FacilityData fac = FacilityData.get(server);
        ServerLevel level = fac.isBuilt() ? fac.level(server) : null;
        if (level == null) return fail(src, "No hay instalación construida.");
        BearDirector.placeBoss(level, fac, CampaignData.get(server));
        return ok(src, "Bearfriender espera en la sala central.");
    }

    private static int status(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        CampaignData camp = CampaignData.get(server);
        FacilityData fac = FacilityData.get(server);
        if (!camp.isActive()) {
            return ok(src, "Campaña: inactiva" + (camp.isEnded() ? " (terminada)" : "") + ". Instalación: "
                    + (fac.isBuilt() ? "construida" : "sin construir") + ".");
        }
        int night = camp.getNight();
        int len = BearfrienderConfig.NIGHT_DURATION_SECONDS.get() * 20;
        StringBuilder sb = new StringBuilder();
        sb.append("Campaña: NOCHE ").append(night);
        if (camp.getExtraLoops() > 0) sb.append(" (repetida ").append(camp.getExtraLoops()).append(" veces)");
        if (night < 5) {
            sb.append(" | tiempo ").append(camp.getNightTicks() / 20).append("/").append(len / 20).append(" s");
        }
        if (camp.isEnded()) sb.append(" | TERMINADA, salida ").append(fac.isExitOpen() ? "abierta" : "cerrada");
        sb.append("\nMisiones: ");
        for (MissionId m : MissionId.values()) {
            sb.append(camp.isMissionDone(m) ? "✓ " : "□ ").append(m.id).append("  ");
        }
        sb.append("| cintas ").append(camp.getTapesDestroyed()).append("/3");
        BearTuning t = BearTuning.effective(night, camp);
        sb.append(String.format("\nOso: uso de TV %.0f%%, acecho %.0f-%.0f bloques, persecución %s, puertas %s",
                t.tvUseChance() * 100, t.stalkMin(), t.stalkMax(), t.chaseAllowed() ? "sí" : "no",
                t.opensDoors() ? "sí" : "no"));
        ServerLevel level = fac.isBuilt() ? fac.level(server) : null;
        if (level != null && camp.getBearId() != null) {
            Entity e = level.getEntity(camp.getBearId());
            if (e instanceof BearfrienderEntity b) {
                String where = b.getBearState() == BearState.DORMANT ? "en su cámara sellada" : "en escena";
                sb.append(" | ").append(b.getBearState()).append(" (").append(where).append(") en ")
                        .append(b.blockPosition().getX()).append(' ').append(b.blockPosition().getY()).append(' ')
                        .append(b.blockPosition().getZ());
            } else {
                sb.append(" | oso: zona sin cargar o aún no creado");
            }
        }
        sb.append("\nTareas pendientes: ").append(Scheduler.pending()).append(" | televisores retenidos: ").append(TvControl.held());
        return ok(src, sb.toString());
    }
}

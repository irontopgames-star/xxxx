package com.bearfriender.campaign;

import com.bearfriender.BearfrienderMod;
import com.bearfriender.block.TvControl;
import com.bearfriender.campaign.NightDefinition.ScheduledEvent;
import com.bearfriender.config.BearfrienderConfig;
import com.bearfriender.network.CampaignSyncPacket;
import com.bearfriender.network.ModNetwork;
import com.bearfriender.structure.FacilityData;
import com.bearfriender.structure.FacilityPlacer;
import com.bearfriender.structure.LightControl;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.bearfriender.registry.ModSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Controlador propio de la campaña de cinco noches. No usa el ciclo día/noche de Minecraft:
 * lleva su reloj, dispara los eventos programados y gestiona noches, muerte, final y escape.
 * Todo ocurre en el servidor; el cliente solo recibe el estado para dibujar el reloj.
 */
@Mod.EventBusSubscriber(modid = BearfrienderMod.MOD_ID)
public final class CampaignManager {
    private CampaignManager() {}

    // =====================================================================================
    //  Eventos de Forge
    // =====================================================================================

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) tick(server);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        restoreWorld(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        Scheduler.clear();
        TvControl.clear();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp && sp.getServer() != null) {
            sync(sp.getServer(), sp);
        }
    }

    /** Provisional hasta la Fase 9: si Bearfriender muere en la noche 5, se inicia la secuencia final. */
    @SubscribeEvent
    public static void onBearDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof com.bearfriender.entity.BearfrienderEntity bear) || bear.level().isClientSide) return;
        MinecraftServer server = bear.getServer();
        if (server == null) return;
        CampaignData camp = CampaignData.get(server);
        if (camp.isActive() && !camp.isEnded() && camp.getNight() == 5) {
            Scheduler.after(1, () -> completeCampaign(server));
        }
    }

    /** Si un jugador muere dentro de la instalación, reaparece en la recepción. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.getServer() == null) return;
        MinecraftServer server = p.getServer();
        CampaignData camp = CampaignData.get(server);
        FacilityData fac = FacilityData.get(server);
        if (!camp.isActive() || camp.isEnded() || !fac.isBuilt()) return;
        ServerLevel level = fac.level(server);
        if (level == null || !inside(p, level, fac)) return;
        setRespawnAtFacility(p, level, fac);
    }

    /** Política de reinicio tras morir: por defecto se reinicia el encuentro y se conserva el progreso. */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.getServer() == null || event.isEndConquered()) return;
        MinecraftServer server = p.getServer();
        CampaignData camp = CampaignData.get(server);
        FacilityData fac = FacilityData.get(server);
        if (!camp.isActive() || camp.isEnded() || !fac.isBuilt()) return;
        ServerLevel level = fac.level(server);
        if (level == null) return;

        resetEncounter(level, fac, camp);
        if (BearfrienderConfig.DEATH_POLICY.get() == BearfrienderConfig.DeathPolicy.RESTART_NIGHT) {
            camp.restartNightClock();
            camp.clearFiredForNight(camp.getNight());
        }
        p.displayClientMessage(Component.literal("Despiertas de nuevo...").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC), true);
        sync(server, null);
    }

    // =====================================================================================
    //  Bucle principal
    // =====================================================================================

    private static void tick(MinecraftServer server) {
        Scheduler.tick();
        FacilityData fac = FacilityData.get(server);
        if (!fac.isBuilt()) return;
        ServerLevel level = fac.level(server);
        if (level == null) return;
        CampaignData camp = CampaignData.get(server);

        List<ServerPlayer> players = participants(server, level, fac);
        TvControl.tick(level, players);
        long t = level.getGameTime();

        if (!camp.isActive()) {
            if (BearfrienderConfig.AUTO_START.get() && !players.isEmpty() && t % 20 == 0) {
                startCampaign(server, players.get(0));
            }
            return;
        }
        if (camp.isEnded()) {
            if (t % 10 == 0) checkEscape(server, level, fac, camp, players);
            return;
        }

        if (t % 20 == 0) sync(server, null);
        if (t % 100 == 0) ensureRespawns(players, level, fac);
        if (players.isEmpty()) return;                       // la noche se detiene si nadie está dentro

        if (t % 40 == 0) BearDirector.maintain(level, fac, camp);

        if (camp.getNight() < 5) {
            camp.addNightTick();
            fireEvents(server, level, fac, camp, players);
            if (camp.getNightTicks() >= nightLength()) nightEnded(server, level, fac, camp, players);
        }
    }

    private static int nightLength() {
        return BearfrienderConfig.NIGHT_DURATION_SECONDS.get() * 20;
    }

    // =====================================================================================
    //  Jugadores
    // =====================================================================================

    private static AABB bounds(FacilityData fac) {
        BlockPos min = fac.min();
        BlockPos max = fac.max();
        return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
    }

    private static boolean inside(ServerPlayer p, ServerLevel level, FacilityData fac) {
        return p.serverLevel() == level && bounds(fac).contains(p.position());
    }

    public static List<ServerPlayer> participants(MinecraftServer server, ServerLevel level, FacilityData fac) {
        List<ServerPlayer> out = new ArrayList<>();
        AABB box = bounds(fac);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.serverLevel() == level && !p.isSpectator() && p.isAlive() && box.contains(p.position())) out.add(p);
        }
        return out;
    }

    private static void setRespawnAtFacility(ServerPlayer p, ServerLevel level, FacilityData fac) {
        BlockPos spawn = fac.anchor("spawn");
        if (spawn != null) p.setRespawnPosition(level.dimension(), spawn, 0.0F, true, false);
    }

    private static void ensureRespawns(List<ServerPlayer> players, ServerLevel level, FacilityData fac) {
        BlockPos spawn = fac.anchor("spawn");
        if (spawn == null) return;
        for (ServerPlayer p : players) {
            if (!spawn.equals(p.getRespawnPosition()) || p.getRespawnDimension() != level.dimension()) {
                setRespawnAtFacility(p, level, fac);
            }
        }
    }

    // =====================================================================================
    //  Sincronización con el cliente
    // =====================================================================================

    /** Envía el estado a un jugador, o a todos si player es null. */
    public static void sync(MinecraftServer server, ServerPlayer player) {
        CampaignData camp = CampaignData.get(server);
        CampaignSyncPacket packet = new CampaignSyncPacket(camp.isActive(), camp.isEnded(), camp.getNight(),
                camp.getExtraLoops(), camp.getNightTicks(), nightLength(), camp.getMissionMask());
        if (player != null) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        } else {
            ModNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
        }
    }

    // =====================================================================================
    //  Eventos programados
    // =====================================================================================

    private static void fireEvents(MinecraftServer server, ServerLevel level, FacilityData fac, CampaignData camp,
                                   List<ServerPlayer> players) {
        NightDefinition nd = NightDefinition.of(camp.getNight());
        List<ScheduledEvent> events = nd.events();
        if (events.isEmpty()) return;
        float progress = camp.getNightTicks() / (float) nightLength();
        for (int i = 0; i < events.size(); i++) {
            ScheduledEvent ev = events.get(i);
            if (progress < ev.at()) continue;
            String key = camp.getNight() + ":" + camp.getExtraLoops() + ":" + i;
            if (camp.hasFired(key)) continue;
            camp.markFired(key);
            CampaignEvents.run(new CampaignEvents.Ctx(level, fac, camp, players), ev);
        }
    }

    // =====================================================================================
    //  Noches
    // =====================================================================================

    private static void nightEnded(MinecraftServer server, ServerLevel level, FacilityData fac, CampaignData camp,
                                   List<ServerPlayer> players) {
        int n = camp.getNight();
        if (n < 4) {
            advanceTo(server, level, fac, camp, n + 1);
        } else if (camp.allMissionsDone()) {
            advanceTo(server, level, fac, camp, 5);
        } else {
            // Faltan misiones: la noche 4 se repite hasta que se completen
            camp.nextLoop();
            for (ServerPlayer p : players) {
                p.displayClientMessage(Component.literal("La señal sigue activa. La noche no termina...")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC), true);
            }
            sync(server, null);
        }
    }

    public static void advanceTo(MinecraftServer server, ServerLevel level, FacilityData fac, CampaignData camp, int night) {
        camp.setNight(night);
        camp.clearFiredForNight(night);
        TvControl.releaseAll(level);
        LightControl.restoreAll(level, fac);
        List<ServerPlayer> players = participants(server, level, fac);
        NightDefinition nd = NightDefinition.of(night);
        title(players, nd.title(), nd.subtitle());
        if (night == 5) {
            night5Intro(server, level, fac, camp);
        } else {
            BearDirector.hide(level, fac, camp);
        }
        sync(server, null);
    }

    /** Escena de apertura de la noche 5: todos los televisores, apagón y el oso en la sala central. */
    private static void night5Intro(MinecraftServer server, ServerLevel level, FacilityData fac, CampaignData camp) {
        camp.markCinematic("night5_intro");
        Scheduler.after(50, () -> {
            CampaignEvents.tvAllFace(new CampaignEvents.Ctx(level, fac, camp, participants(server, level, fac)), 600);
        });
        for (int i = 0; i < 5; i++) {
            final int k = i;
            Scheduler.after(90 + i * 10, () -> LightControl.setAll(level, fac, k % 2 == 0 ? 0 : LightControl.DEFAULT_LEVEL));
        }
        Scheduler.after(150, () -> LightControl.setAll(level, fac, 0));
        Scheduler.after(200, () -> {
            BearDirector.placeBoss(level, fac, camp);
            BlockPos c = fac.anchor("boss_center");
            if (c != null) level.playSound(null, c, ModSounds.BEAR_ROAR.get(), SoundSource.HOSTILE, 1.6F, 1.0F);
        });
        Scheduler.after(240, () -> LightControl.restoreAll(level, fac));
    }

    // =====================================================================================
    //  Inicio, parada y reinicio
    // =====================================================================================

    /** Inicia la campaña. Devuelve un texto de error, o null si todo salió bien. */
    public static String startCampaign(MinecraftServer server, ServerPlayer starter) {
        FacilityData fac = FacilityData.get(server);
        if (!fac.isBuilt()) return "Primero construye la instalación con /bearfriender facility build.";
        CampaignData camp = CampaignData.get(server);
        if (camp.isActive()) return "La campaña ya está en marcha. Usa /bearfriender campaign stop para detenerla.";
        ServerLevel level = fac.level(server);
        if (level == null) return "No se encontró la dimensión de la instalación.";

        camp.start();
        // Punto de regreso del jugador que inicia
        if (starter != null && !inside(starter, level, fac)) {
            camp.setReturn(starter.level().dimension().location().toString(), starter.blockPosition());
        } else {
            BlockPos sp = server.overworld().getSharedSpawnPos();
            camp.setReturn(Level.OVERWORLD.location().toString(), sp);
        }

        // Escenario en estado inicial
        TvControl.releaseAll(level);
        LightControl.restoreAll(level, fac);
        FacilityPlacer.applyExit(level, fac.origin(), false);
        fac.setExitOpen(false);
        BearDirector.removeAll(level, fac, camp);
        BearDirector.ensureBear(level, fac, camp);

        BlockPos spawn = fac.anchor("spawn");
        if (starter != null && spawn != null) {
            starter.teleportTo(level, spawn.getX() + 0.5, spawn.getY() + 0.07, spawn.getZ() + 0.5, 0.0F, 0.0F);
            setRespawnAtFacility(starter, level, fac);
        }
        NightDefinition n1 = NightDefinition.of(1);
        title(participants(server, level, fac), n1.title(), n1.subtitle());
        sync(server, null);
        return null;
    }

    public static void stopCampaign(MinecraftServer server) {
        FacilityData fac = FacilityData.get(server);
        CampaignData camp = CampaignData.get(server);
        ServerLevel level = fac.isBuilt() ? fac.level(server) : null;
        if (level != null) {
            BearDirector.removeAll(level, fac, camp);
            TvControl.releaseAll(level);
            LightControl.restoreAll(level, fac);
        }
        Scheduler.clear();
        camp.stop();
        sync(server, null);
    }

    /** Al cargar el mundo: luces y televisores vuelven a su estado normal (las tareas en curso se perdieron). */
    private static void restoreWorld(MinecraftServer server) {
        FacilityData fac = FacilityData.get(server);
        if (!fac.isBuilt()) return;
        ServerLevel level = fac.level(server);
        if (level == null) return;
        LightControl.restoreAll(level, fac);
        TvControl.releaseAll(level);
    }

    /** Reinicio del encuentro actual tras una muerte: el oso vuelve a su sitio y se limpian los efectos. */
    private static void resetEncounter(ServerLevel level, FacilityData fac, CampaignData camp) {
        Scheduler.clear();
        TvControl.releaseAll(level);
        LightControl.restoreAll(level, fac);
        if (camp.getNight() == 5) BearDirector.placeBoss(level, fac, camp);
        else BearDirector.hide(level, fac, camp);
    }

    // =====================================================================================
    //  Final: derrota del oso, salida y escape
    // =====================================================================================

    /** Secuencia final: el oso cae, los televisores se apagan uno tras otro y se abre la salida. */
    public static void completeCampaign(MinecraftServer server) {
        FacilityData fac = FacilityData.get(server);
        CampaignData camp = CampaignData.get(server);
        if (!fac.isBuilt() || !camp.isActive() || camp.isEnded()) return;
        ServerLevel level = fac.level(server);
        if (level == null) return;
        camp.finish();
        Scheduler.clear();

        // El oso (si sigue ahí): pantalla apagada y animación de derrota. No se crea uno nuevo.
        if (camp.getBearId() != null && level.getEntity(camp.getBearId()) instanceof com.bearfriender.entity.BearfrienderEntity bear) {
            bear.setNoAi(true);
            bear.setScreenMode(com.bearfriender.entity.ScreenMode.OFF);
            if (bear.isAlive()) bear.playAction(com.bearfriender.entity.BearAction.DEATH);
        }
        List<ServerPlayer> players = participants(server, level, fac);
        title(players, "SE ACABÓ", "");

        // Los televisores se apagan uno tras otro, del más cercano al oso al más lejano
        BlockPos center = fac.anchor("boss_center");
        List<BlockPos> tvs = new ArrayList<>(fac.anchors("tv"));
        if (center != null) tvs.sort(Comparator.comparingDouble(p -> p.distSqr(center)));
        int delay = 40;
        for (BlockPos tv : tvs) {
            Scheduler.after(delay, () -> {
                if (level.isLoaded(tv)) {
                    com.bearfriender.block.CrtTvBlock.setScreen(level, tv, level.getBlockState(tv), com.bearfriender.block.TvScreen.OFF);
                }
            });
            delay += 6;
        }
        TvControl.clear();
        for (BlockPos tv : tvs) TvControl.hold(level, tv, com.bearfriender.block.TvScreen.INTERFERENCE, delay + 20, 0.0D);

        // Salida abierta
        Scheduler.after(delay + 20, () -> {
            FacilityPlacer.applyExit(level, fac.origin(), true);
            fac.setExitOpen(true);
            LightControl.restoreAll(level, fac);
            level.playSound(null, fac.origin().offset(40, 2, 27), ModSounds.EXIT_OPEN.get(), SoundSource.BLOCKS, 1.6F, 1.0F);
            title(participants(server, level, fac), "LA SALIDA SE HA ABIERTO", "");
        });
        // Pista final: un único televisor sonríe un instante y se apaga
        Scheduler.after(delay + 140, () -> {
            List<BlockPos> list = fac.anchors("tv");
            if (!list.isEmpty()) {
                BlockPos tv = list.get(0);
                TvControl.hold(level, tv, com.bearfriender.block.TvScreen.SMILE, 50, 0.0D);
            }
        });
        sync(server, null);
    }

    private static void checkEscape(MinecraftServer server, ServerLevel level, FacilityData fac, CampaignData camp,
                                    List<ServerPlayer> players) {
        if (!fac.isExitOpen()) return;
        List<BlockPos> triggers = fac.anchors("exit_trigger");
        for (ServerPlayer p : players) {
            for (BlockPos t : triggers) {
                if (p.distanceToSqr(t.getX() + 0.5, t.getY() + 0.5, t.getZ() + 0.5) < 3.0D * 3.0D) {
                    escape(server, p, camp);
                    break;
                }
            }
        }
    }

    private static void escape(MinecraftServer server, ServerPlayer p, CampaignData camp) {
        ServerLevel dest = null;
        BlockPos pos = camp.getReturnPos();
        if (!camp.getReturnDim().isEmpty()) {
            dest = server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(camp.getReturnDim())));
        }
        if (dest == null) {
            dest = server.overworld();
            pos = dest.getSharedSpawnPos();
        }
        p.teleportTo(dest, pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, p.getYRot(), 0.0F);
        title(List.of(p), "HAS ESCAPADO", "...eso parece.");
    }

    // =====================================================================================
    //  Utilidades
    // =====================================================================================

    public static void title(List<ServerPlayer> players, String main, String sub) {
        for (ServerPlayer p : players) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(sub).withStyle(ChatFormatting.GRAY)));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(main).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
        }
    }
}

package com.bearfriender.structure;

import com.bearfriender.entity.BearfrienderEntity;
import com.bearfriender.block.BalloonBlock;
import com.bearfriender.block.CrtTvBlock;
import com.bearfriender.block.PlushBlock;
import com.bearfriender.block.ToyBlocksBlock;
import com.bearfriender.entity.BrokenTeddyEntity;
import com.bearfriender.registry.ModBlocks;
import com.bearfriender.registry.ModEntities;
import com.bearfriender.structure.FacilityLayout.Arch;
import com.bearfriender.structure.FacilityLayout.Door;
import com.bearfriender.structure.FacilityLayout.Room;
import com.bearfriender.structure.FacilityLayout.Style;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.Tags;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Construye, retira y abre/cierra la salida de la instalación. Todo con bloques de vanilla.
 * Las posiciones clave se devuelven como "anclas" (ver FacilityData) para que las demás fases
 * coloquen televisores, objetos de misión y mecanismos reales en ellas.
 *
 * Convención de anclas: una ancla con y=1 es la celda de aire sobre el suelo (donde hay una alfombra fina).
 */
public final class FacilityPlacer {
    /** Actualiza clientes y evita las reacciones en cadena de forma (así no se rompen puertas ni alfombras al construir). */
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final int LAMP_LEVEL = 4;

    /** Tema de pared por sala: {franja A, franja B, zócalo}. */
    private static final Map<String, Block[]> THEME = Map.ofEntries(
            Map.entry("reception", new Block[]{Blocks.PINK_TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.DARK_OAK_PLANKS}),
            Map.entry("living", new Block[]{Blocks.LIGHT_BLUE_TERRACOTTA, Blocks.YELLOW_TERRACOTTA, Blocks.DARK_OAK_PLANKS}),
            Map.entry("bedroom", new Block[]{Blocks.LIGHT_BLUE_TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.SPRUCE_PLANKS}),
            Map.entry("storage", new Block[]{Blocks.ORANGE_TERRACOTTA, Blocks.BROWN_TERRACOTTA, Blocks.SPRUCE_PLANKS}),
            Map.entry("office", new Block[]{Blocks.GREEN_TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.DARK_OAK_PLANKS}),
            Map.entry("tapes", new Block[]{Blocks.PURPLE_TERRACOTTA, Blocks.BLACK_TERRACOTTA, Blocks.DARK_OAK_PLANKS}),
            Map.entry("teddy", new Block[]{Blocks.BROWN_TERRACOTTA, Blocks.BLACK_TERRACOTTA, Blocks.DARK_OAK_PLANKS}),
            Map.entry("stairwell", new Block[]{Blocks.RED_TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.DARK_OAK_PLANKS}),
            Map.entry("exit_hall", new Block[]{Blocks.LIME_TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.DARK_OAK_PLANKS}),
            Map.entry("central", new Block[]{Blocks.RED_TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.BLACK_TERRACOTTA}),
            Map.entry("ring", new Block[]{Blocks.BLUE_TERRACOTTA, Blocks.PURPLE_TERRACOTTA, Blocks.GRAY_TERRACOTTA}));

    private static final Block[] CONFETTI = {Blocks.RED_CARPET, Blocks.YELLOW_CARPET, Blocks.LIGHT_BLUE_CARPET,
            Blocks.LIME_CARPET, Blocks.PINK_CARPET, Blocks.ORANGE_CARPET};
    private static final Block[] WOOL_BOXES = {Blocks.RED_WOOL, Blocks.BLUE_WOOL, Blocks.YELLOW_WOOL,
            Blocks.LIME_WOOL, Blocks.PINK_WOOL, Blocks.PURPLE_WOOL};
    private static final Block[] BANNERS = {Blocks.RED_WALL_BANNER, Blocks.YELLOW_WALL_BANNER, Blocks.BLUE_WALL_BANNER,
            Blocks.GREEN_WALL_BANNER, Blocks.PINK_WALL_BANNER, Blocks.PURPLE_WALL_BANNER};

    private final ServerLevel level;
    private final BlockPos origin;
    private final RandomSource rnd;
    private final Map<String, List<BlockPos>> anchors = new LinkedHashMap<>();

    private FacilityPlacer(ServerLevel level, BlockPos origin) {
        this.level = level;
        this.origin = origin;
        this.rnd = RandomSource.create(origin.asLong());
    }

    // =====================================================================================
    //  API pública
    // =====================================================================================

    /** Construye la instalación con su suelo de planta baja en origin.y y la esquina NO en (origin.x, origin.z). */
    public static Map<String, List<BlockPos>> build(ServerLevel level, BlockPos origin) {
        FacilityPlacer p = new FacilityPlacer(level, origin);
        p.buildAll();
        return p.anchors;
    }

    /** Cuenta bloques que impedirían construir. strictAir=true exige aire; false admite terreno natural. */
    public static int countBlocking(ServerLevel level, BlockPos origin, boolean strictAir) {
        int bad = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < FacilityLayout.SIZE_X; x++) {
            for (int z = 0; z < FacilityLayout.SIZE_Z; z++) {
                for (int y = FacilityLayout.MIN_Y; y <= FacilityLayout.MAX_Y; y++) {
                    pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    BlockState s = level.getBlockState(pos);
                    boolean ok = strictAir ? s.isAir() : isNatural(level, pos, s);
                    if (!ok) bad++;
                }
            }
        }
        return bad;
    }

    private static boolean isNatural(ServerLevel level, BlockPos pos, BlockState s) {
        if (s.isAir() || !s.getFluidState().isEmpty()) return true;
        if (level.getBlockEntity(pos) != null) return false;
        return s.is(BlockTags.DIRT) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.SAND)
                || s.is(BlockTags.LEAVES) || s.is(BlockTags.OVERWORLD_NATURAL_LOGS) || s.is(BlockTags.TERRACOTTA)
                || s.is(BlockTags.ICE) || s.is(BlockTags.SNOW) || s.is(Tags.Blocks.ORES)
                || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(Blocks.MUD) || s.is(Blocks.BEDROCK)
                || s.canBeReplaced();
    }

    /** Elimina la instalación (bloques y las entidades del mod que haya dentro). */
    public static void remove(ServerLevel level, FacilityData data) {
        BlockPos min = data.min();
        BlockPos max = data.max();
        AABB box = new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
        for (Entity e : level.getEntitiesOfClass(Entity.class, box,
                e -> e instanceof BearfrienderEntity || e instanceof BrokenTeddyEntity)) {
            e.discard();
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int y = min.getY(); y <= max.getY(); y++) {
                    pos.set(x, y, z);
                    if (!level.getBlockState(pos).isAir()) level.setBlock(pos, AIR, FLAGS);
                }
            }
        }
    }

    /** Abre o cierra la salida (sellada con bloques de hierro). */
    public static void applyExit(ServerLevel level, BlockPos origin, boolean open) {
        BlockState state = open ? AIR : Blocks.IRON_BLOCK.defaultBlockState();
        for (int z = FacilityLayout.EXIT_Z0; z <= FacilityLayout.EXIT_Z1; z++) {
            for (int y = 1; y <= 4; y++) {
                level.setBlock(origin.offset(FacilityLayout.EXIT_WALL_X, y, z), state, FLAGS);
            }
        }
    }

    // =====================================================================================
    //  Construcción
    // =====================================================================================

    private void buildAll() {
        fillShell();
        paintWalls();
        carveRooms();
        carpets();
        for (Door d : FacilityLayout.DOORS) carveDoor(d);
        for (Arch a : FacilityLayout.ARCHES) carveArch(a);
        carveStairs();
        carveExitTunnel();
        furnish();
        lighting();
        cobwebs();
        spawnTeddy();
    }

    // ---- utilidades ----

    private void set(int x, int y, int z, BlockState s) {
        level.setBlock(origin.offset(x, y, z), s, FLAGS);
    }

    private void box(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++)
                    set(x, y, z, s);
    }

    private void anchor(String name, int x, int y, int z) {
        anchors.computeIfAbsent(name, k -> new ArrayList<>()).add(origin.offset(x, y, z));
    }

    private static BlockState st(Block b) { return b.defaultBlockState(); }

    private BlockState wallBlock(Style style) {
        int r = rnd.nextInt(100);
        return switch (style) {
            case HOUSE -> st(r < 55 ? Blocks.SPRUCE_PLANKS : r < 85 ? Blocks.DARK_OAK_PLANKS : Blocks.STRIPPED_SPRUCE_LOG);
            case INDUSTRIAL -> st(r < 45 ? Blocks.STONE_BRICKS : r < 70 ? Blocks.CRACKED_STONE_BRICKS
                    : r < 85 ? Blocks.ANDESITE : Blocks.MOSSY_STONE_BRICKS);
            case CORRIDOR -> st(r < 50 ? Blocks.STONE_BRICKS : r < 75 ? Blocks.CRACKED_STONE_BRICKS
                    : r < 90 ? Blocks.POLISHED_ANDESITE : Blocks.MOSSY_STONE_BRICKS);
            case BASEMENT, ARENA -> st(r < 55 ? Blocks.DEEPSLATE_BRICKS : r < 85 ? Blocks.CRACKED_DEEPSLATE_BRICKS
                    : Blocks.COBBLED_DEEPSLATE);
        };
    }

    /** Pared de una sala: rayas de dos colores, zócalo de madera y desconchones. Las salas sin tema usan su estilo. */
    private BlockState wallAt(Room r, int x, int y, int z) {
        Block[] th = THEME.get(r.id().startsWith("ring") ? "ring" : r.id());
        if (th == null) return wallBlock(r.style());
        if (y >= r.yCeiling()) return st(th[1]);
        if (y <= r.yFloor() + 2) return st(th[2]);
        Block stripe = (((x + z) / 2) & 1) == 0 ? th[0] : th[1];
        if (rnd.nextInt(100) < 8) stripe = Blocks.GRAY_TERRACOTTA;          // desconchones
        return st(stripe);
    }

    private BlockState floorBlock(Style style) {
        int r = rnd.nextInt(100);
        return switch (style) {
            case HOUSE -> st(r < 60 ? Blocks.DARK_OAK_PLANKS : Blocks.SPRUCE_PLANKS);
            case INDUSTRIAL -> st(r < 60 ? Blocks.POLISHED_ANDESITE : Blocks.STONE_BRICKS);
            case CORRIDOR -> st(r < 70 ? Blocks.POLISHED_ANDESITE : Blocks.COBBLESTONE);
            case BASEMENT -> st(r < 60 ? Blocks.COBBLED_DEEPSLATE : Blocks.DEEPSLATE_TILES);
            case ARENA -> st(Blocks.DEEPSLATE_TILES);
        };
    }

    // ---- estructura ----

    /** Bloque macizo con el volumen completo; después se vacían las salas. */
    private void fillShell() {
        for (int x = 0; x <= 40; x++)
            for (int z = 0; z <= 40; z++)
                for (int y = FacilityLayout.MIN_Y; y <= FacilityLayout.MAX_Y; y++)
                    set(x, y, z, wallBlock(Style.INDUSTRIAL));
        // Túnel de salida
        for (int x = 41; x <= 47; x++)
            for (int z = 26; z <= 29; z++)
                for (int y = FacilityLayout.MIN_Y; y <= FacilityLayout.MAX_Y; y++)
                    set(x, y, z, wallBlock(Style.INDUSTRIAL));
    }

    /** Pinta el anillo de muro alrededor de cada sala (suelo, techo y paredes) con su estilo. */
    private void paintWalls() {
        for (Room r : FacilityLayout.ROOMS) {
            for (int x = r.x0() - 1; x <= r.x1() + 1; x++) {
                for (int z = r.z0() - 1; z <= r.z1() + 1; z++) {
                    boolean interior = x >= r.x0() && x <= r.x1() && z >= r.z0() && z <= r.z1();
                    if (interior) continue;
                    for (int y = r.yFloor(); y <= r.yCeiling(); y++) set(x, y, z, wallAt(r, x, y, z));
                }
            }
        }
    }

    private void carveRooms() {
        for (Room r : FacilityLayout.ROOMS) {
            for (int x = r.x0(); x <= r.x1(); x++) {
                for (int z = r.z0(); z <= r.z1(); z++) {
                    set(x, r.yFloor(), z, floorBlock(r.style()));
                    for (int y = r.yFloor() + 1; y <= r.yTop(); y++) set(x, y, z, AIR);
                    set(x, r.yCeiling(), z, wallAt(r, x, r.yCeiling(), z));
                }
            }
        }
    }

    /**
     * Alfombra fina en todo el suelo: da ambiente de zona de juegos (ajedrez, confeti) y además impide
     * que aparezcan monstruos naturales dentro.
     */
    private void carpets() {
        for (Room r : FacilityLayout.ROOMS) {
            for (int x = r.x0(); x <= r.x1(); x++) {
                for (int z = r.z0(); z <= r.z1(); z++) {
                    Block c;
                    switch (r.style()) {
                        case HOUSE, ARENA -> c = ((x + z) & 1) == 0 ? r.carpetA() : r.carpetB();
                        case CORRIDOR -> {
                            c = Blocks.BLACK_CARPET;
                            if (rnd.nextInt(100) < 9) c = CONFETTI[rnd.nextInt(CONFETTI.length)];
                        }
                        default -> c = rnd.nextInt(100) < 72 ? r.carpetA() : r.carpetB();
                    }
                    if (rnd.nextInt(100) < 3) c = Blocks.GRAY_CARPET;       // desgaste
                    set(x, r.yFloor() + 1, z, st(c));
                }
            }
        }
    }

    private void carveDoor(Door d) {
        for (int i = 0; i < 2; i++) {
            int x = d.xWall() ? d.fixed() : d.start() + i;
            int z = d.xWall() ? d.start() + i : d.fixed();
            for (int y = d.yBase(); y <= d.yBase() + 3; y++) set(x, y, z, AIR);
            Direction facing = d.xWall() ? Direction.EAST : Direction.SOUTH;
            DoorHingeSide hinge;
            if (d.xWall()) hinge = i == 0 ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT;
            else hinge = i == 0 ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT;
            BlockState lower = Blocks.DARK_OAK_DOOR.defaultBlockState()
                    .setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HINGE, hinge)
                    .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
            set(x, d.yBase(), z, lower);
            set(x, d.yBase() + 1, z, lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            anchor("door", x, d.yBase(), z);
        }
    }

    private void carveArch(Arch a) {
        for (int i = 0; i < a.width(); i++) {
            int x = a.xWall() ? a.fixed() : a.start() + i;
            int z = a.xWall() ? a.start() + i : a.fixed();
            for (int y = 1; y <= 4; y++) set(x, y, z, AIR);
        }
    }

    /** Escalera abierta que baja al sótano hacia el oeste, y puerta de acceso al sótano. */
    private void carveStairs() {
        BlockState stair = Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST);
        for (int i = 0; i < FacilityLayout.STAIR_STEPS; i++) {
            int x = FacilityLayout.STAIR_TOP_X - i;
            int y = -i;
            for (int z = FacilityLayout.STAIR_Z0; z <= FacilityLayout.STAIR_Z1; z++) {
                for (int yy = y + 1; yy <= 5; yy++) set(x, yy, z, AIR);
                set(x, y, z, stair);
            }
        }
        int landing = FacilityLayout.STAIR_TOP_X - FacilityLayout.STAIR_STEPS + 1;   // x=32
        for (int z = FacilityLayout.STAIR_Z0; z <= FacilityLayout.STAIR_Z1; z++) {
            for (int y = -6; y <= -3; y++) set(landing - 1, y, z, AIR);                // hueco en el muro x=31
        }
    }

    private void carveExitTunnel() {
        for (int x = 41; x <= FacilityLayout.TUNNEL_X1; x++) {
            for (int z = FacilityLayout.EXIT_Z0; z <= FacilityLayout.EXIT_Z1; z++) {
                for (int y = 1; y <= 4; y++) set(x, y, z, AIR);
                set(x, 1, z, st(Blocks.LIGHT_GRAY_CARPET));
            }
        }
        // El hueco del muro (x=40) queda sellado hasta que se desbloquee la salida
        applyExit(level, origin, false);
        // Luz del final del túnel
        set(FacilityLayout.TUNNEL_X1, 4, FacilityLayout.EXIT_Z0, st(Blocks.GLOWSTONE));
        set(FacilityLayout.TUNNEL_X1, 4, FacilityLayout.EXIT_Z1, st(Blocks.GLOWSTONE));
        anchor("exit_seal", FacilityLayout.EXIT_WALL_X, 1, FacilityLayout.EXIT_Z0);
        anchor("exit_trigger", FacilityLayout.TUNNEL_X1, 1, FacilityLayout.EXIT_Z0);
        anchor("exit_trigger", FacilityLayout.TUNNEL_X1, 1, FacilityLayout.EXIT_Z1);
    }

    // ---- mobiliario ----

    /** Televisor CRT apagado, mirando al lado contrario de la pared a la que está pegado. */
    private void tv(int x, int y, int z) {
        tv(x, y, z, wallFacing(x, y, z));
    }

    private void tv(int x, int y, int z, Direction facing) {
        set(x, y, z, ModBlocks.CRT_TV.get().defaultBlockState().setValue(CrtTvBlock.FACING, facing));
        anchor("tv", x, y, z);
    }

    private Direction wallFacing(int x, int y, int z) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockState n = level.getBlockState(origin.offset(x + d.getStepX(), y, z + d.getStepZ()));
            if (!n.isAir() && !n.is(ModBlocks.CRT_TV.get())) return d.getOpposite();
        }
        return Direction.NORTH;
    }

    // ---- decoración de juguetería ----

    private void plush(int kind, int x, int y, int z, Direction facing) {
        Block b = kind == 0 ? ModBlocks.PLUSH_TEDDY.get() : kind == 1 ? ModBlocks.PLUSH_BUNNY.get() : ModBlocks.PLUSH_DUCK.get();
        set(x, y, z, b.defaultBlockState().setValue(PlushBlock.FACING, facing));
    }

    private void toyBlocks(int x, int y, int z, Direction facing) {
        set(x, y, z, ModBlocks.TOY_BLOCKS.get().defaultBlockState().setValue(ToyBlocksBlock.FACING, facing));
    }

    private void banner(int x, int y, int z, Direction facing, int color) {
        set(x, y, z, BANNERS[Math.floorMod(color, BANNERS.length)].defaultBlockState().setValue(WallBannerBlock.FACING, facing));
    }

    /** Globo: peso de cubos en el suelo, hilo hacia arriba y el globo en yTop. */
    private void balloon(int x, int z, int yTop, int color, boolean weight) {
        set(x, 1, z, weight ? ModBlocks.TOY_BLOCKS.get().defaultBlockState()
                : ModBlocks.BALLOON_STRING.get().defaultBlockState());
        for (int y = 2; y < yTop; y++) set(x, y, z, ModBlocks.BALLOON_STRING.get().defaultBlockState());
        set(x, yTop, z, ModBlocks.BALLOON.get().defaultBlockState().setValue(BalloonBlock.COLOR, Math.floorMod(color, BalloonBlock.COLORS)));
    }

    /** Racimo de tres globos atados a un mismo peso; sx/sz (+1 o -1) dicen hacia dónde se abre el racimo. */
    private void balloonCluster(int x, int z, int sx, int sz) {
        int c = rnd.nextInt(BalloonBlock.COLORS);
        balloon(x, z, 4, c, true);
        balloon(x + sx, z, 3, c + 2, false);
        balloon(x, z + sz, 4, c + 4, false);
    }

    /** Estantería de juguetes pegada a una pared: dos baldas llenas de peluches, cubos y cajas de colores. */
    private void toyShelf(int x0, int z0, int x1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                set(x, 1, z, st(Blocks.DARK_OAK_PLANKS));
                set(x, 3, z, st(Blocks.DARK_OAK_PLANKS));
                stock(x, 2, z);
                stock(x, 4, z);
            }
        }
    }

    private void stock(int x, int y, int z) {
        int r = rnd.nextInt(100);
        Direction f = Direction.from2DDataValue(rnd.nextInt(4));
        if (r < 18) plush(0, x, y, z, f);
        else if (r < 34) plush(1, x, y, z, f);
        else if (r < 50) plush(2, x, y, z, f);
        else if (r < 66) toyBlocks(x, y, z, f);
        else if (r < 82) set(x, y, z, st(WOOL_BOXES[rnd.nextInt(WOOL_BOXES.length)]));
    }

    private void stand(int x, int y, int z) {
        set(x, y, z, st(Blocks.BARREL));
    }

    private void candle(int x, int y, int z) {
        set(x, y, z, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true));
    }

    private void seat(int x, int y, int z, Direction ascent) {
        set(x, y, z, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, ascent));
    }

    private void furnish() {
        final Direction N = Direction.NORTH, S = Direction.SOUTH, E = Direction.EAST, W = Direction.WEST;

        // ---------------- Recepción (tienda de juguetes) ----------------
        for (int x = 6; x <= 7; x++) {                         // entrada principal tapiada
            for (int y = 1; y <= 4; y++) set(x, y, 0, st(Blocks.DARK_OAK_PLANKS));
            for (int y = 2; y <= 3; y++) {
                set(x, y, 0, Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.EAST, true).setValue(IronBarsBlock.WEST, true));
            }
        }
        anchor("entrance", 6, 1, 1);
        box(3, 1, 5, 5, 1, 5, st(Blocks.DARK_OAK_PLANKS));     // mostrador
        set(4, 2, 5, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, S));
        candle(3, 2, 5);
        plush(0, 5, 2, 5, S);
        for (int z = 3; z <= 5; z++) seat(1, 1, z, W);         // banco
        toyShelf(12, 1, 12, 3);
        stand(10, 1, 1);
        tv(10, 2, 1);
        plush(1, 2, 1, 7, E);
        plush(2, 11, 1, 3, W);
        balloonCluster(2, 7, 1, 1);
        balloonCluster(11, 6, -1, 1);
        banner(3, 4, 1, S, 0);
        banner(9, 4, 1, S, 1);
        anchor("spawn", 6, 1, 4);
        anchor("hide_spot", 12, 1, 8);

        // ---------------- Sala de juegos (televisores) ----------------
        for (int x : new int[]{16, 19, 22, 25}) {
            stand(x, 1, 1);
            tv(x, 2, 1);
        }
        for (int x = 16; x <= 18; x++) seat(x, 1, 5, S);
        for (int x = 22; x <= 24; x++) seat(x, 1, 5, S);
        box(19, 1, 4, 21, 1, 4, st(Blocks.SPRUCE_SLAB));
        toyBlocks(20, 1, 6, N);
        set(26, 1, 1, st(Blocks.JUKEBOX));                     // caja de música
        toyShelf(14, 6, 14, 8);
        toyShelf(26, 6, 26, 8);
        stand(15, 1, 8);
        candle(15, 2, 8);
        plush(0, 15, 1, 6, N);
        plush(1, 23, 1, 6, N);
        plush(2, 17, 1, 7, N);
        balloonCluster(14, 2, 1, 1);
        balloonCluster(25, 3, -1, 1);
        banner(17, 4, 1, S, 2);
        banner(23, 4, 1, S, 4);
        anchor("component_spot", 25, 1, 7);
        anchor("hide_spot", 25, 1, 8);

        // ---------------- Dormitorio infantil ----------------
        set(37, 1, 3, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, E).setValue(BedBlock.PART, BedPart.FOOT));
        set(38, 1, 3, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, E).setValue(BedBlock.PART, BedPart.HEAD));
        toyShelf(28, 1, 31, 1);
        stand(33, 1, 1);
        tv(33, 2, 1);
        stand(38, 1, 5);
        candle(38, 2, 5);
        plush(0, 37, 1, 5, W);
        plush(1, 36, 1, 6, N);
        plush(2, 34, 1, 4, S);
        balloonCluster(33, 6, 1, 1);
        banner(36, 4, 1, S, 3);
        banner(39, 4, 6, W, 5);
        anchor("component_spot", 31, 1, 7);
        anchor("hide_spot", 39, 1, 8);

        // ---------------- Despacho ----------------
        box(2, 1, 12, 4, 1, 12, st(Blocks.DARK_OAK_PLANKS));
        candle(2, 2, 12);
        plush(0, 3, 2, 12, S);
        seat(3, 1, 13, S);
        box(1, 1, 10, 1, 3, 13, st(Blocks.BOOKSHELF));
        stand(7, 1, 15);
        stand(8, 1, 15);
        balloonCluster(6, 13, -1, 1);
        banner(5, 4, 10, S, 5);
        anchor("tape_spot", 4, 2, 12);
        anchor("component_spot", 8, 2, 15);

        // ---------------- Habitación de cintas VHS ----------------
        box(1, 1, 17, 1, 3, 23, st(Blocks.BOOKSHELF));
        box(8, 1, 17, 8, 3, 18, st(Blocks.BOOKSHELF));
        box(8, 1, 22, 8, 3, 23, st(Blocks.BOOKSHELF));
        stand(4, 1, 18);
        tv(4, 2, 18);
        stand(4, 1, 22);
        tv(4, 2, 22);
        plush(0, 4, 1, 19, N);          // peluches mirando la tele
        plush(1, 4, 1, 21, S);
        box(5, 1, 20, 5, 1, 20, st(Blocks.SPRUCE_SLAB));
        anchor("component_spot", 6, 1, 21);

        // ---------------- Habitación del osito (a oscuras) ----------------
        stand(2, 1, 26);
        candle(2, 2, 26);
        set(1, 1, 30, st(Blocks.HAY_BLOCK));
        seat(2, 1, 28, W);
        plush(0, 6, 1, 27, W);
        plush(2, 5, 1, 29, N);
        plush(1, 7, 1, 26, S);
        anchor("teddy", 3, 1, 28);
        anchor("hide_spot", 8, 1, 30);

        // ---------------- Almacén ----------------
        toyShelf(2, 34, 5, 34);
        toyShelf(8, 34, 9, 34);
        stand(11, 1, 39);
        stand(12, 1, 38);
        stand(2, 1, 38);
        box(6, 1, 38, 7, 2, 38, st(Blocks.BIRCH_PLANKS));       // cajas de cartón
        plush(0, 4, 1, 36, S);
        plush(2, 5, 1, 37, N);
        balloonCluster(10, 37, -1, 1);
        banner(5, 4, 32, S, 4);
        anchor("tape_spot", 2, 2, 38);
        anchor("component_spot", 7, 3, 38);
        anchor("hide_spot", 1, 1, 39);

        // ---------------- Sala de grabación ----------------
        box(15, 1, 35, 17, 3, 35, st(Blocks.GLASS));
        box(22, 1, 35, 25, 3, 35, st(Blocks.GLASS));
        box(16, 1, 38, 24, 1, 38, st(Blocks.DARK_OAK_PLANKS));
        for (int x : new int[]{17, 21, 24}) tv(x, 2, 38, N);
        plush(2, 22, 2, 38, N);
        set(15, 1, 38, st(Blocks.NOTE_BLOCK));
        set(25, 1, 38, st(Blocks.NOTE_BLOCK));
        set(26, 1, 38, st(Blocks.JUKEBOX));
        anchor("tape_spot", 19, 2, 38);
        anchor("hide_spot", 26, 1, 39);

        // ---------------- Sala eléctrica ----------------
        set(33, 2, 39, st(Blocks.IRON_BLOCK));                    // panel (marcador)
        box(32, 1, 39, 32, 2, 39, st(Blocks.COPPER_BLOCK));
        box(34, 1, 39, 34, 2, 39, st(Blocks.COPPER_BLOCK));
        set(33, 3, 39, st(Blocks.REDSTONE_LAMP));
        box(37, 1, 37, 38, 2, 38, st(Blocks.IRON_BLOCK));
        set(37, 3, 37, st(Blocks.REDSTONE_LAMP));
        stand(31, 1, 32);
        tv(31, 2, 32);
        anchor("electric_panel", 33, 2, 38);
        anchor("component_spot", 30, 1, 34);
        anchor("hide_spot", 39, 1, 32);

        // ---------------- Laboratorio ----------------
        box(34, 1, 12, 36, 1, 12, st(Blocks.QUARTZ_BLOCK));
        box(34, 1, 16, 36, 1, 16, st(Blocks.QUARTZ_BLOCK));
        set(38, 1, 11, st(Blocks.CAULDRON));
        box(38, 1, 14, 38, 3, 17, st(Blocks.GLASS));
        plush(0, 39, 1, 16, W);                                  // un peluche encerrado
        stand(33, 1, 10);
        tv(33, 2, 10);
        stand(36, 1, 10);
        tv(36, 2, 10);
        anchor("component_spot", 35, 2, 16);
        anchor("hide_spot", 39, 1, 10);

        // ---------------- Escalera ----------------
        for (int x = 33; x <= 38; x++) {                          // barandilla junto al hueco
            set(x, 1, 20, st(Blocks.DARK_OAK_PLANKS));
            set(x, 1, 23, st(Blocks.DARK_OAK_PLANKS));
        }
        stand(39, 1, 20);
        candle(39, 2, 20);

        // ---------------- Vestíbulo de salida ----------------
        stand(34, 1, 26);
        tv(34, 2, 26);
        box(36, 1, 30, 38, 1, 30, st(Blocks.DARK_OAK_PLANKS));
        balloonCluster(37, 27, 1, 1);
        banner(36, 4, 26, S, 3);
        anchor("hide_spot", 39, 1, 30);

        // ---------------- Sala central (pista de circo) ----------------
        for (int[] p : new int[][]{{17, 17}, {23, 17}, {17, 23}, {23, 23}}) {
            for (int y = 1; y <= 5; y++) {                        // columnas a rayas de caramelo
                set(p[0], y, p[1], st((y & 1) == 1 ? Blocks.RED_CONCRETE : Blocks.WHITE_CONCRETE));
            }
        }
        tv(18, 3, 17, S);
        tv(22, 3, 17, S);
        tv(18, 3, 23, N);
        tv(22, 3, 23, N);
        for (int[] p : new int[][]{{16, 20}, {24, 20}, {20, 16}, {20, 24}}) {
            set(p[0], 1, p[1], st(Blocks.COPPER_BLOCK));
            set(p[0], 2, p[1], st(Blocks.REDSTONE_LAMP));
            anchor("boss_node", p[0], 1, p[1]);
        }
        // Peluches sentados alrededor de la pista, mirando al centro
        int kind = 0;
        for (int x : new int[]{16, 17, 23, 24}) {
            plush(kind++ % 3, x, 1, 14, S);
            plush(kind++ % 3, x, 1, 26, N);
        }
        for (int z : new int[]{16, 17, 23, 24}) {
            plush(kind++ % 3, 14, 1, z, E);
            plush(kind++ % 3, 26, 1, z, W);
        }
        balloonCluster(14, 14, 1, 1);
        balloonCluster(26, 14, -1, 1);
        balloonCluster(14, 26, 1, -1);
        balloonCluster(26, 26, -1, -1);
        anchor("boss_center", 20, 1, 20);

        // ---------------- Pasillo en anillo ----------------
        tv(10, 2, 22);
        tv(30, 2, 20);
        tv(22, 2, 10);
        tv(23, 2, 30);
        for (int[] p : new int[][]{{15, 10}, {25, 10}}) banner(p[0], 4, p[1], S, p[0]);
        for (int[] p : new int[][]{{15, 30}, {25, 30}}) banner(p[0], 4, p[1], N, p[0] + 1);
        for (int[] p : new int[][]{{10, 15}, {10, 25}}) banner(p[0], 4, p[1], E, p[1]);
        for (int[] p : new int[][]{{30, 15}, {30, 25}}) banner(p[0], 4, p[1], W, p[1] + 2);
        for (int[] p : new int[][]{{10, 10}, {30, 10}, {10, 30}, {30, 30}}) anchor("hide_spot", p[0], 1, p[1]);
        for (int[] p : new int[][]{{12, 19}, {28, 19}, {19, 12}, {19, 28}}) anchor("peek_spot", p[0], 1, p[1]);

        // ---------------- Sótano ----------------
        box(25, -6, 19, 25, -2, 19, st(Blocks.DEEPSLATE_BRICKS));
        box(25, -6, 25, 25, -2, 25, st(Blocks.DEEPSLATE_BRICKS));
        box(27, -6, 17, 28, -5, 18, st(Blocks.BLACKSTONE));       // incinerador (marcador)
        anchor("incinerator", 27, -6, 19);
        tv(22, -5, 26);
        anchor("hide_spot", 30, -6, 28);

        // ---------------- Sala de transmisión ----------------
        box(14, -6, 21, 16, -5, 23, st(Blocks.IRON_BLOCK));      // transmisor central (marcador)
        set(15, -4, 22, st(Blocks.REDSTONE_LAMP));
        anchor("transmitter", 17, -6, 22);
        for (int z : new int[]{19, 22, 25}) {
            set(10, -5, z, st(Blocks.COPPER_BLOCK));
            anchor("switch", 10, -5, z);
        }
        for (int x : new int[]{12, 15, 18}) {
            tv(x, -5, 16);
            tv(x, -5, 28);
        }
        anchor("hide_spot", 10, -6, 28);

        // ---------------- Cámara sellada de Bearfriender ----------------
        anchor("bear_hold", 35, -6, 35);
    }

    // ---- iluminación tenue ----

    private void lamp(int x, int yCeil, int z, int lightLevel) {
        set(x, yCeil, z, st(Blocks.REDSTONE_LAMP));                                   // lámpara apagada (estética)
        set(x, yCeil - 1, z, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, lightLevel)); // luz invisible
        anchor("lamp", x, yCeil - 1, z);
    }

    private void lighting() {
        for (Room r : FacilityLayout.ROOMS) {
            if (r.id().equals("teddy") || r.id().startsWith("ring") || r.id().equals("central") || r.id().equals("bear_hold")) continue;
            lamp(r.cx(), r.yTop(), r.cz(), LAMP_LEVEL);
        }
        for (int x : new int[]{13, 20, 27}) { lamp(x, 5, 11, LAMP_LEVEL); lamp(x, 5, 29, LAMP_LEVEL); }
        for (int z : new int[]{16, 24}) { lamp(11, 5, z, LAMP_LEVEL); lamp(29, 5, z, LAMP_LEVEL); }
        for (int[] p : new int[][]{{15, 15}, {25, 15}, {15, 25}, {25, 25}}) lamp(p[0], 5, p[1], LAMP_LEVEL);
    }

    private void cobwebs() {
        for (Room r : FacilityLayout.ROOMS) {
            if (r.style() == Style.CORRIDOR || r.id().equals("bear_hold")) continue;
            int[][] corners = {{r.x0(), r.z0()}, {r.x1(), r.z0()}, {r.x0(), r.z1()}, {r.x1(), r.z1()}};
            for (int[] c : corners) {
                if (rnd.nextInt(100) < 50 && level.getBlockState(origin.offset(c[0], r.yTop(), c[1])).isAir()) {
                    set(c[0], r.yTop(), c[1], st(Blocks.COBWEB));
                }
            }
        }
    }

    private void spawnTeddy() {
        List<BlockPos> list = anchors.get("teddy");
        if (list == null || list.isEmpty()) return;
        BlockPos p = list.get(0);
        BrokenTeddyEntity teddy = ModEntities.BROKEN_TEDDY.get().create(level);
        if (teddy == null) return;
        teddy.moveTo(p.getX() + 0.5, p.getY() + 0.0625, p.getZ() + 0.5, -90.0F, 0.0F);
        level.addFreshEntity(teddy);
    }
}

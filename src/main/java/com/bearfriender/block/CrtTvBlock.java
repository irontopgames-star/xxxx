package com.bearfriender.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import com.bearfriender.registry.ModSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Televisor CRT. Cinco imágenes de pantalla (TvScreen); las encendidas emiten algo de luz.
 * Bearfriender las enciende al acercarse (ver TvProximity); el jugador puede alternar apagado/estática con clic derecho.
 */
public class CrtTvBlock extends HorizontalDirectionalBlock {
    public static final EnumProperty<TvScreen> SCREEN = EnumProperty.create("screen", TvScreen.class);
    private static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 12.0D, 15.0D);

    public CrtTvBlock() {
        super(BlockBehaviour.Properties.of()
                .strength(0.6F)
                .sound(SoundType.METAL)
                .noOcclusion()
                .lightLevel(state -> state.getValue(SCREEN).light()));
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH)
                .setValue(SCREEN, TvScreen.OFF));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SCREEN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (!level.isClientSide) {
            boolean off = state.getValue(SCREEN) == TvScreen.OFF;
            setScreen((ServerLevel) level, pos, state, off ? TvScreen.STATIC : TvScreen.OFF);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Cambia la imagen de la pantalla (solo servidor) y reproduce el sonido que corresponde a la transición. */
    public static void setScreen(ServerLevel level, BlockPos pos, BlockState state, TvScreen screen) {
        if (!(state.getBlock() instanceof CrtTvBlock)) return;
        TvScreen old = state.getValue(SCREEN);
        if (old == screen) return;
        level.setBlock(pos, state.setValue(SCREEN, screen), Block.UPDATE_ALL);
        if (screen == TvScreen.OFF) {
            level.playSound(null, pos, ModSounds.TV_OFF.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
        } else if (old == TvScreen.OFF) {
            level.playSound(null, pos, ModSounds.TV_ON.get(), SoundSource.BLOCKS, 0.9F, 1.0F);
            if (screen == TvScreen.STATIC || screen == TvScreen.INTERFERENCE) {
                level.playSound(null, pos, ModSounds.TV_STATIC.get(), SoundSource.BLOCKS, 0.45F, 1.0F);
            }
        }
        if (screen == TvScreen.FACE || screen == TvScreen.SMILE) {
            level.playSound(null, pos, ModSounds.TV_FACE.get(), SoundSource.BLOCKS, 0.7F, 1.0F);
        }
    }
}

package testmod;

import com.almostreliable.energymeter.ModConstants;
import com.almostreliable.energymeter.core.Registration;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import testmod.content.EnergyEmitterBlock;
import testmod.content.EnergyEmitterBlockEntity;
import testmod.content.EnergyEmitterBlockMenu;
import testmod.content.EnergyReceiverBlock;
import testmod.content.EnergyReceiverBlockEntity;
import testmod.content.ForwardingCableHandler;

import java.util.List;
import java.util.function.Function;

public final class TestRegistration {

    // @formatter:off

    private static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TestMod.MOD_ID);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(TestMod.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TestMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, TestMod.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ModConstants.MOD_ID);

    public static final DeferredBlock<EnergyReceiverBlock> ENERGY_RECEIVER_BLOCK = registerBlock("energy_receiver_block", EnergyReceiverBlock::new);
    public static final DeferredBlock<EnergyEmitterBlock> ENERGY_EMITTER_BLOCK = registerBlock("energy_emitter_block", EnergyEmitterBlock::new);
    public static final DeferredBlock<Block> ENERGY_CABLE_BLOCK = registerBlock("energy_cable_block", Block::new);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EnergyReceiverBlockEntity>> ENERGY_RECEIVER_BLOCK_ENTITY = registerBlockEntity(ENERGY_RECEIVER_BLOCK, EnergyReceiverBlockEntity::new);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EnergyEmitterBlockEntity>> ENERGY_EMITTER_BLOCK_ENTITY = registerBlockEntity(ENERGY_EMITTER_BLOCK, EnergyEmitterBlockEntity::new);
    public static final DeferredHolder<MenuType<?>, MenuType<EnergyEmitterBlockMenu>> ENERGY_EMITTER_BLOCK_MENU = registerMenu(ENERGY_EMITTER_BLOCK, EnergyEmitterBlockEntity.class, EnergyEmitterBlockMenu::new);

    // @formatter:on

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = CREATIVE_TABS.register(
        "tab", () -> CreativeModeTab.builder()
            .title(Component.literal("Testmod"))
            .icon(Items.NETHER_STAR::getDefaultInstance)
            .noScrollBar()
            .displayItems((features, output) -> output.acceptAll(List.of(ENERGY_EMITTER_BLOCK.toStack(), ENERGY_RECEIVER_BLOCK.toStack(), ENERGY_CABLE_BLOCK.toStack())))
            .build()
    );

    private TestRegistration() {}

    public static void init(IEventBus modEventBus) {
        CREATIVE_TABS.register(modEventBus);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);

        modEventBus.addListener(TestRegistration::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            Capabilities.EnergyStorage.BLOCK,
            ENERGY_RECEIVER_BLOCK_ENTITY.get(),
            EnergyReceiverBlockEntity::getEnergyCapability
        );
        event.registerBlock(
            Capabilities.EnergyStorage.BLOCK,
            (level, pos, state, blockEntity, side) -> new ForwardingCableHandler(level, pos, side),
            ENERGY_CABLE_BLOCK.get()
        );
    }

    private static <B extends Block> DeferredBlock<B> registerBlock(
        String id, Function<BlockBehaviour.Properties, B> factory
    ) {
        var block = BLOCKS.registerBlock(
            id,
            factory,
            BlockBehaviour.Properties.of().strength(2f).mapColor(MapColor.METAL).sound(SoundType.METAL)
        );
        ITEMS.registerSimpleBlockItem(block);
        return block;
    }

    @SuppressWarnings("DataFlowIssue")
    private static <E extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<E>> registerBlockEntity(
        DeferredBlock<?> block, BlockEntityType.BlockEntitySupplier<E> factory
    ) {
        return BLOCK_ENTITIES.register(block.getId().getPath(), () -> BlockEntityType.Builder.of(factory, block.get()).build(null));
    }

    private static <M extends AbstractContainerMenu, E extends BlockEntity> DeferredHolder<MenuType<?>, MenuType<M>> registerMenu(
        DeferredBlock<?> block, Class<E> blockEntityClass, Registration.MenuSupplier<M, E> factory
    ) {
        return MENUS.register(
            block.getId().getPath(),
            () -> IMenuTypeExtension.create((wid, playerInventory, data) -> {
                BlockPos pos = data.readBlockPos();
                // noinspection resource
                BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(pos);
                if (!blockEntityClass.isInstance(blockEntity)) {
                    throw new IllegalStateException("block entity is not of the expected type");
                }
                return factory.create(wid, playerInventory, blockEntityClass.cast(blockEntity));
            })
        );
    }
}

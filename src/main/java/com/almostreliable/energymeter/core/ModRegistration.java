package com.almostreliable.energymeter.core;

import com.almostreliable.energymeter.EnergyMeter;
import com.almostreliable.energymeter.ModConstants;
import com.almostreliable.energymeter.block.MeterBlock;
import com.almostreliable.energymeter.block.MonitorBlock;
import com.almostreliable.energymeter.block.MonitorBlockItem;
import com.almostreliable.energymeter.block.entity.MeterBlockEntity;
import com.almostreliable.energymeter.block.entity.MonitorBlockEntity;
import com.almostreliable.energymeter.data.EnergyMeterLang;
import com.almostreliable.energymeter.menu.MeterMenu;
import com.almostreliable.energymeter.menu.MonitorMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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

import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class ModRegistration {

    // @formatter:off

    // registries
    private static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ModConstants.MOD_ID);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ModConstants.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ModConstants.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ModConstants.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ModConstants.MOD_ID);

    // blocks
    public static final DeferredBlock<MeterBlock> METER_BLOCK = registerBlock(Constants.METER_ID, "Energy Meter", MeterBlock::new);
    public static final DeferredBlock<MonitorBlock> MONITOR_BLOCK = registerBlock(Constants.MONITOR_ID, "External Monitor", MonitorBlock::new, MonitorBlockItem::new);

    // block entities
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MeterBlockEntity>> METER_BLOCK_ENTITY = registerBlockEntity(METER_BLOCK, MeterBlockEntity::new);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MonitorBlockEntity>> MONITOR_BLOCK_ENTITY = registerBlockEntity(MONITOR_BLOCK, MonitorBlockEntity::new);

    // menus
    public static final DeferredHolder<MenuType<?>, MenuType<MeterMenu>> METER_MENU = registerMenu(METER_BLOCK, MeterBlockEntity.class, MeterMenu::new);
    public static final DeferredHolder<MenuType<?>, MenuType<MonitorMenu>> MONITOR_MENU = registerMenu(MONITOR_BLOCK, MonitorBlockEntity.class, MonitorMenu::new);

    // @formatter:on

    // creative tab
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = CREATIVE_TABS.register(
        "tab", () -> CreativeModeTab.builder()
            .title(EnergyMeterLang.LangEntry.of("tab", "main", ModConstants.MOD_NAME).get())
            .icon(METER_BLOCK::toStack)
            .noScrollBar()
            .displayItems((_, output) -> {
                output.acceptAll(getKnownItems());
                if (EnergyMeter.isModLoaded(Constants.GUIDE_ME)) {
                    var guideStack = getGuideBookStack();
                    if (guideStack == null) return;
                    output.accept(guideStack);
                }
            })
            .build()
    );

    private ModRegistration() {}

    public static void init(IEventBus modEventBus) {
        CREATIVE_TABS.register(modEventBus);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);

        modEventBus.addListener(ModRegistration::registerCapabilities);
    }

    private static Collection<ItemStack> getKnownItems() {
        return ITEMS.getEntries().stream().map(e -> e.value().getDefaultInstance()).toList();
    }

    public static Iterable<Block> getKnownBlocks() {
        return BLOCKS.getEntries().stream().map(e -> (Block) e.value()).toList();
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, METER_BLOCK_ENTITY.get(), MeterBlockEntity::getEnergyCapability);
    }

    private static <B extends Block> DeferredBlock<B> registerBlock(
        String id, String name, Function<BlockBehaviour.Properties, B> factory
    ) {
        var block = registerBlockOnly(id, name, factory);
        ITEMS.registerSimpleBlockItem(block);
        return block;
    }

    private static <B extends Block> DeferredBlock<B> registerBlock(
        String id, String name, Function<BlockBehaviour.Properties, B> factory,
        BiFunction<Block, Item.Properties, ? extends BlockItem> itemFactory
    ) {
        var block = registerBlockOnly(id, name, factory);
        ITEMS.registerItem(id, properties -> itemFactory.apply(block.get(), properties));
        return block;
    }

    private static <B extends Block> DeferredBlock<B> registerBlockOnly(
        String id, String name, Function<BlockBehaviour.Properties, B> factory
    ) {
        var block = BLOCKS.registerBlock(
            id,
            factory,
            () -> BlockBehaviour.Properties.of().strength(2f).mapColor(MapColor.METAL).sound(SoundType.METAL)
        );
        EnergyMeterLang.LangEntry.of("block", id, name);
        EnergyMeterLang.LangEntry.item(id, name);
        return block;
    }

    private static <E extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<E>> registerBlockEntity(
        DeferredBlock<?> block, BlockEntityType.BlockEntitySupplier<E> factory
    ) {
        return BLOCK_ENTITIES.register(block.getId().getPath(), () -> new BlockEntityType<>(factory, block.get()));
    }

    private static <M extends AbstractContainerMenu, E extends BlockEntity> DeferredHolder<MenuType<?>, MenuType<M>> registerMenu(
        DeferredBlock<?> block, Class<E> blockEntityClass, MenuSupplier<M, E> factory
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

    @Nullable
    private static ItemStack getGuideBookStack() {
        var guideItem = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Constants.GUIDE_ME, "guide"));
        if (guideItem == Items.AIR) return null;

        // noinspection unchecked
        var guideComponent = (DataComponentType<Identifier>) BuiltInRegistries.DATA_COMPONENT_TYPE
            .getValue(Identifier.fromNamespaceAndPath(Constants.GUIDE_ME, "guide_id"));
        if (guideComponent == null) return null;

        var guideStack = guideItem.getDefaultInstance();
        guideStack.set(guideComponent, EnergyMeter.getRL("guide"));

        return guideStack;
    }

    @FunctionalInterface
    public interface MenuSupplier<M extends AbstractContainerMenu, E extends BlockEntity> {

        M create(int wid, Inventory playerInventory, E blockEntity);
    }
}

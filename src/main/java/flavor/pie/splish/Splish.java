package flavor.pie.splish;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import flavor.pie.util.conversions.ItemStackTranslator;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.slf4j.Logger;
import org.spongepowered.api.Game;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.data.Transaction;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.meta.ItemEnchantment;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.action.FishingEvent;
import org.spongepowered.api.event.filter.cause.First;
import org.spongepowered.api.event.game.GameReloadEvent;
import org.spongepowered.api.event.game.state.GamePostInitializationEvent;
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;
import org.spongepowered.api.item.Enchantment;
import org.spongepowered.api.item.Enchantments;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.ItemStackBuilderPopulators;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.util.weighted.WeightedTable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@Plugin(id="splish",name="Splish",authors="pie_flavor",description="Allows manipulation of the fishing loot table.",version="1.0.0")
public class Splish {
    @Inject
    Game game;
    @Inject
    PluginContainer container;
    @Inject @ConfigDir(sharedRoot = false)
    Path dir;
    @Inject
    Logger logger;
    HoconConfigurationLoader loader;
    Path file;
    ConfigurationNode root;
    Random random;
    WeightedTable<ItemStackGenerator> defaultTable;
    WeightedTable<ItemStackGenerator> level1Table;
    WeightedTable<ItemStackGenerator> level2Table;
    WeightedTable<ItemStackGenerator> level3Table;
    @Listener
    public void preInit(GamePreInitializationEvent e) throws Exception {
        random = new Random();
        if (!dir.toFile().exists()) {
            if (!dir.toFile().mkdir()) {
                logger.error("Could not create the config folder!");
                disable();
                throw new IOException();
            }
        }
        file = dir.resolve("table.conf");
        if (!file.toFile().exists()) {
            try {
                game.getAssetManager().getAsset(this, "default.conf").get().copyToFile(file);
            } catch (IOException ex) {
                logger.error("Could not create the config file!");
                disable();
                throw ex;
            }
        }
        loader = HoconConfigurationLoader.builder().setPath(file).build();
        try {
            root = loader.load();
        } catch (IOException ex) {
            logger.error("Could not load the config file!");
            disable();
            throw ex;
        }
    }
    @Listener
    public void postInit(GamePostInitializationEvent e) {
        generateTable();
    }
    void generateTable() {
        defaultTable = new WeightedTable<>();
        level1Table = new WeightedTable<>();
        level2Table = new WeightedTable<>();
        level3Table = new WeightedTable<>();
        List<? extends ConfigurationNode> list = root.getNode("entries").getChildrenList();
        for (ConfigurationNode node : list) {
            ConfigurationNode chance = node.getNode("chance");
            defaultTable.add(new ItemStackGenerator(node), chance.getNode("unenchanted").getDouble());
            level1Table.add(new ItemStackGenerator(node), chance.getNode("lots1").getDouble());
            level2Table.add(new ItemStackGenerator(node), chance.getNode("lots2").getDouble());
            level3Table.add(new ItemStackGenerator(node), chance.getNode("lots3").getDouble());
        }
    }
    @Listener
    public void reload(GameReloadEvent e) throws IOException {
        try {
            root = loader.load();
        } catch (IOException ex) {
            logger.error("Could not reload the config file!");
            throw ex;
        }
        generateTable();
    }
    private void disable() {
        game.getEventManager().unregisterPluginListeners(this);
        game.getCommandManager().getOwnedBy(this).forEach(game.getCommandManager()::removeMapping);
    }
    @Listener
    public void fish(FishingEvent.Stop e, @First Player p) {
        Transaction<ItemStackSnapshot> transaction = e.getItemStackTransaction();
        if (transaction.getFinal().equals(ItemStackSnapshot.NONE)) {
            return;
        }
        ItemStack rod = p.getItemInHand().get();
        int level = 0;
        Optional<List<ItemEnchantment>> enchs_ = rod.get(Keys.ITEM_ENCHANTMENTS);
        if (enchs_.isPresent()) {
            List<ItemEnchantment> enchs = enchs_.get();
            for (ItemEnchantment ench : enchs) {
                if (ench.getEnchantment().equals(Enchantments.LUCK_OF_THE_SEA)) {
                    level = ench.getLevel();
                }
            }
        }
        switch (level) {
            case 0:
                transaction.setCustom(defaultTable.get(random).get(0).get().createSnapshot());
                break;
            case 1:
                transaction.setCustom(level1Table.get(random).get(0).get().createSnapshot());
                break;
            case 2:
                transaction.setCustom(level2Table.get(random).get(0).get().createSnapshot());
                break;
            case 3:
                transaction.setCustom(level3Table.get(random).get(0).get().createSnapshot());
                break;
        }
    }
    class ItemStackGenerator {
        ConfigurationNode node;
        ItemStackGenerator(ConfigurationNode node) {
            this.node = node;
        }
        ItemStack get() {
            ItemStack stack;
            try {
                stack = ItemStackTranslator.convertNode(node.getNode("item"));
            } catch (ObjectMappingException e) {
                throw new RuntimeException(e);
            }
            ItemStack.Builder builder = ItemStack.builder().from(stack);
            ConfigurationNode randEnchant = node.getNode("extra", "rand-enchant");
            if (!randEnchant.isVirtual()) {
                List<Enchantment> enchs = Lists.newArrayList(game.getRegistry().getAllOf(Enchantment.class));
                if (!randEnchant.getNode("extended").getBoolean()) enchs = enchs.stream().filter(e -> e.canBeAppliedByTable(stack)).collect(Collectors.toCollection(Lists::newArrayList));
                BiConsumer<ItemStack.Builder, Random> consumer = ItemStackBuilderPopulators.enchantmentsWithVanillaLevelVariance(enchs);
                consumer.accept(builder, random);
            }
            ConfigurationNode randDamage = node.getNode("extra", "rand-damage");
            if (!randDamage.isVirtual()) {
                int min = randDamage.getNode("min").getInt(0);
                int max = randDamage.getNode("max").getInt(1);
                int damage = random.nextInt(max - min + 1) - min;
                builder.keyValue(Keys.ITEM_DURABILITY, damage);
            }
            return builder.build();
        }
    }
}

# Splish

Splish is a plugin that admins can use to modify the fishing loot table.

### Configuration
The default generated config contains Minecraft's default fishing table. Have a look at it to better understand the system.

The `entries[]` tag is a list of all entry tags.
Each entry tag has three components:

1. The `item{}` tag. This is a standard item tag formatted how Mojang does it. See [here](https://minecraft.gamepedia.com/Player.dat_format#Item_structure) for details. The `id`, `Count`, and `Damage` tags are required. Also, unlike in /give, everything else must be wrapped in `tag{}`.
2. The `chance{}` tag. This is a compound containing four tags: `unenchanted`, `lots1`, `lots2`, and `lots3`. lots stands for Luck of the Sea, and each tag represents the number of levels of Luck of the Sea that the fishing rod is enchanted with. While the chances have been written so that they all add up to 1, this is not actually necessary. So if I had 3 elements, with chances 1, 1, and 3, the first two would have a 1/5 chance and the third would have a 3/5 chance.
3. The `extra{}` tag. This tag is optional, and may contain one of a few extra modifiers.
  1. `rand-enchant{}` is a modifier that randomly enchants the item. If `extended` is true, it can apply enchantments that the item does not accept normally from an enchanting table. Unfortunately this cannot entirely mimic vanilla functionality; this is an API limitation, as the only random enchantment generator in Sponge does not accept an experience level argument.
  2. `rand-damage{}` is a modifier that randomly damages the item, from `min` to `max`. Note that this cannot do variances, such as wool colors; it is only applicable to tool durability. The number specified is how much damage is taken off the tool, not how much health it has left.

### Commands
This plugin has no direct commands, but it supports `/sponge plugins reload`. When run, it will reload the configuration.

### Changelog

1.0.0: I'd tell you, but the computer that used to hold this information was recently fished out of the ocean.

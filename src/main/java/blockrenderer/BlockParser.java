package blockrenderer;

import se.llbit.nbt.CompoundTag;
import se.llbit.nbt.NamedTag;
import se.llbit.nbt.StringTag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BlockParser {
    private static final Pattern BLOCK_PATTERN =
            Pattern.compile("^([a-z0-9_]+):([a-z0-9_]+)(?:\\[([^\\]]+)\\])?$");

    /**
     * Parse Minecraft-like block syntax, e.g. <code>minecraft:red_candle[candles=3, waterlogged=true]</code> into a
     * block NBT tag with Name and Properties keys.
     *
     * @param input Input string
     * @return Block NBT tag
     */
    public static CompoundTag parse(String input) {
        Matcher m = BLOCK_PATTERN.matcher(input);

        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid block string: " + input);
        }

        String namespace = m.group(1);
        String blockName = m.group(2);
        String propertiesRaw = m.group(3);

        List<NamedTag> properties = new ArrayList<>();
        if (propertiesRaw != null) {
            String[] pairs = propertiesRaw.split("\\s*,\\s*");
            for (String pair : pairs) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    properties.add(new NamedTag(kv[0], new StringTag(kv[1])));
                }
            }
        }

        return new CompoundTag(Arrays.asList(
                new NamedTag("Name", new StringTag(namespace + ":" + blockName)),
                new NamedTag("Properties", new CompoundTag(properties))));
    }
}

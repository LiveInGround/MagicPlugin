package com.elmakers.mine.bukkit.block.magic;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import javax.annotation.Nonnull;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

import com.elmakers.mine.bukkit.api.spell.Spell;
import com.elmakers.mine.bukkit.magic.Mage;
import com.elmakers.mine.bukkit.utility.StringUtils;
import com.elmakers.mine.bukkit.utility.random.RandomUtils;
import com.elmakers.mine.bukkit.utility.random.WeightedPair;

public class Caster {
    @Nonnull
    private final Deque<WeightedPair<String>> spells;
    private final boolean recast;
    private final boolean undoAll;
    private final boolean allowOverlapping;

    public Caster(@Nonnull MagicBlockTemplate automaton, ConfigurationSection configuration) {
        spells = new ArrayDeque<>();
        RandomUtils.populateStringProbabilityMap(spells, configuration, "spells");
        recast = configuration.getBoolean("recast", true);
        undoAll = configuration.getBoolean("undo_all", false);
        allowOverlapping = configuration.getBoolean("allow_overlap", false);
    }

    public void cast(Mage mage, Location location) {
        if (!allowOverlapping) {
            if (!mage.getPendingBatches().isEmpty()) {
                return;
            }
        }
        String castSpell = RandomUtils.weightedRandom(spells);
        if (castSpell != null && castSpell.length() > 0) {
            String[] parameters = null;
            Spell spell = null;
            if (!castSpell.equalsIgnoreCase("none")) {
                if (castSpell.contains(" ")) {
                    parameters = StringUtils.split(castSpell, ' ');
                    castSpell = parameters[0];
                    parameters = Arrays.copyOfRange(parameters, 1, parameters.length);
                }
                spell = mage.getSpell(castSpell);
            }
            if (spell != null) {
                if (location != null) {
                    mage.setLocation(location);
                }
                spell.cast(parameters);
            }
        }
    }

    public boolean isRecast() {
        return recast;
    }

    public boolean isUndoAll() {
        return undoAll;
    }
}

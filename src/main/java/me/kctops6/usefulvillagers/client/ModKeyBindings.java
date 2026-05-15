package me.kctops6.usefulvillagers.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public class ModKeyBindings {
    public static final String CATEGORY = "key.categories.productivevillagers";

    public static final KeyMapping VILLAGER_INV_KEY = new KeyMapping(
            "key.productivevillagers.open_inventory",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V, // Default key is 'V'
            CATEGORY
    );
}
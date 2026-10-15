package org.qwonix.skyblockEnhanced.client;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

public class ECommands {
    private static int startMacro(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("Called /command sub_command_one."), false);
        return 1;
    }
}

package org.qwonix.skyblockEnhanced.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;

import static java.lang.Math.PI;
import static java.lang.Math.abs;

public class SkyblockEnhancedClient implements ClientModInitializer {
    private static KeyMapping MACRO_KEY;
    private static final Minecraft client = Minecraft.getInstance();
    private static boolean wasMacroActive = false;
    private enum MoveDirection { LEFT,RIGHT,FORWARD,BACK}
    private static MoveDirection MacroDirection = MoveDirection.FORWARD;
    private static long lastWarped = 0;
    private static final long WARP_COOLDOWN = 2000;
    private static float YAW = 135F;
    private static float PITCH = -36F;
    private enum Mode {MELON,CANE,SUN}
    private static Mode CurrentMode = Mode.MELON;
    private double lx = 0;
    private double ly = 0;
    private double lz = 0;
    private double startY = 0;
    private PrintWriter writer = null;
    private double lastLaneSwap = 0;
    private double pitytimer = 0;
    private final LinkedList<Double> lastDistances = new LinkedList<>();
    private static final int MAX_SIZE = 5;
    private static boolean isActive = false;

    @Override
    public void onInitializeClient() {
        try {
            // Создаем папку logs если её нет
            java.io.File logDir = new java.io.File("logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }

            // Создаем файл с датой в названии
            String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            java.io.File logFile = new java.io.File("logs/distance_" + date + ".csv");

            writer = new PrintWriter(new FileWriter(logFile, true));
            // Заголовок CSV
            writer.println("Distance");
            writer.flush();

            System.out.println("[DistanceLogger] Log file created: " + logFile.getAbsolutePath());

        } catch (Exception e) {
            System.err.println("[DistanceLogger] Failed to create log file: " + e.getMessage());
        }
        MACRO_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.skyblockenhanced.macro",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_V,
                KeyMapping.Category.MOVEMENT
        ));



        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("macro")
                    .then(ClientCommandManager.literal("settings")
                            .then(ClientCommandManager.argument("yaw", FloatArgumentType.floatArg(-180F,180F))
                                    .then(ClientCommandManager.argument("pitch", FloatArgumentType.floatArg(-90F,90F))
                                            .executes(SkyblockEnhancedClient::settings)

                                    )
                            )
                    )
            );
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("macro")
                    .then(ClientCommandManager.literal("mode")
                            .then(ClientCommandManager.argument("mode", StringArgumentType.string())
                                    .executes(SkyblockEnhancedClient::changeMode)
                            )
                    )
            );
        });

        ClientTickEvents.START_CLIENT_TICK.register(client -> {

            if (MACRO_KEY.consumeClick()) {
                isActive = !isActive;
                // Оповещение
            }
            long currentTime = System.currentTimeMillis();
            if (client.player == null || client.level == null) return;


            KeyMapping forward = KeyMapping.get("key.forward");
            KeyMapping back = KeyMapping.get("key.back");
            KeyMapping left = KeyMapping.get("key.left");
            KeyMapping right = KeyMapping.get("key.right");
            KeyMapping attackKey = KeyMapping.get("key.attack");
            String UblockId = BuiltInRegistries.BLOCK.getKey(BlockUnder().getBlock()).toString();
            double tx = client.player.getX();
            double ty = client.player.getY();
            double tz = client.player.getZ();
            double distance = Math.sqrt(Math.pow(tx-lx,2)+Math.pow(ty-ly,2)+Math.pow(tz-lz,2));


                if (isActive) {

                    writer.printf("%.5f%n",distance);
                    writer.flush();
                    if (!wasMacroActive){
                        startY = ty;
                        lastLaneSwap = 0;
                        pitytimer = 10;
                    }
                    lastDistances.add(distance);


                    if (lastDistances.size() > MAX_SIZE) {
                        lastDistances.removeFirst();
                    }
                    lastLaneSwap +=1;
                    pitytimer -=1;

                    if (CurrentMode == Mode.CANE) {

                        switch (UblockId) {
                            case "minecraft:lime_terracotta" -> MacroDirection = MoveDirection.RIGHT;
                            case "minecraft:red_terracotta" -> MacroDirection = MoveDirection.FORWARD;
                            case "minecraft:tnt" -> {
                                if (currentTime - lastWarped >= WARP_COOLDOWN) {
                                    client.player.connection.sendCommand("warp garden");
                                    lastWarped = System.currentTimeMillis();
                                }
                            }
                        }
                    }
                    else if (CurrentMode == Mode.MELON) {
                        String LblockId = BuiltInRegistries.BLOCK.getKey(SideBlock(true).getBlock()).toString();
                        String RblockId = BuiltInRegistries.BLOCK.getKey(SideBlock(false).getBlock()).toString();

                        if  (LblockId.equals("minecraft:snow_block")) MacroDirection = MoveDirection.RIGHT;

                        if  (RblockId.equals("minecraft:snow_block")) MacroDirection = MoveDirection.LEFT;
                        else if (RblockId.equals("minecraft:lime_terracotta")) {
                            if (currentTime - lastWarped >= WARP_COOLDOWN) {
                                client.player.connection.sendCommand("warp garden");
                                pitytimer = 20;
                                lastWarped = System.currentTimeMillis();
                            }
                        }
                    }
                    else if (CurrentMode == Mode.SUN)
                    {
                        switch (UblockId) {
                            case "minecraft:brown_terracotta" -> MacroDirection = MoveDirection.BACK;
                            case "minecraft:terracotta" -> MacroDirection = MoveDirection.FORWARD;
                        }
                    }
                    if (!wasMacroActive) {
                        client.player.setYRot(Mth.wrapDegrees(YAW));
                        client.player.setXRot(Mth.clamp(PITCH, -90.0F, 90.0F));
                    }

                    if (forward != null) {
                        if (CurrentMode == Mode.CANE || CurrentMode == Mode.SUN) {
                            switch (MacroDirection) {
                                case FORWARD -> {
                                    forward.setDown(true);
                                    left.setDown(false);
                                    right.setDown(false);
                                    back.setDown(false);
                                }
                                case BACK -> {
                                    back.setDown(true);
                                    forward.setDown(false);
                                    right.setDown(false);
                                    left.setDown(false);

                                }
                                case LEFT -> {
                                    left.setDown(true);
                                    right.setDown(false);
                                    back.setDown(false);
                                    forward.setDown(false);
                                }
                                case RIGHT -> {
                                    right.setDown(true);
                                    left.setDown(false);
                                    back.setDown(false);
                                    forward.setDown(false);
                                }
                            }
                        }
                        else if (CurrentMode == Mode.MELON)
                            {
                                switch (MacroDirection) {
                                    case LEFT -> {
                                        forward.setDown(true);
                                        left.setDown(true);
                                        right.setDown(false);
                                        back.setDown(false);

                                    }
                                    case RIGHT -> {
                                        forward.setDown(true);
                                        right.setDown(true);
                                        left.setDown(false);
                                        back.setDown(false);

                                    }
                            }
                        }
                        attackKey.setDown(true);
                        wasMacroActive = true;

                        if (pitytimer <= 0) {
                            if (getAverageDistance() < 0.01){
                                isActive = false;
                                writer.printf("%s%n","0 distance crash");
                                crash();
                            }
                            if (lastLaneSwap < 1200
                                    && abs(tz-lz) > 0.01
                            ){
                                isActive = false;
                                writer.printf("%s%n","laneswap crash");
                                crash();

                            }
                            else if (lastLaneSwap >= 1200){
                                pitytimer = 70;
                                lastLaneSwap = 0;
                            }
                            if (distance > 0.4 && pitytimer <=0){
                                isActive = false;
                                writer.printf("%s%n","speed crash");
                                crash();
                            }


                            if (abs(startY - ty) > 0.25){
                                isActive = false;
                                writer.printf("%s%n","Y change crash");
                                crash();
                            }
                            if (abs(Mth.wrapDegrees(client.player.getYRot() - YAW)) >= 0.1
                                || abs(client.player.getXRot() - PITCH) >= 0.1
                            ) {
                                isActive = false;
                                writer.printf("%s%n","ROT crash");
                                crash();
                                /*
                                boolean blink = (System.currentTimeMillis() / 200) % 2 == 0;
                                ChatFormatting color = blink ? ChatFormatting.RED : ChatFormatting.WHITE;
                                sendBigMessage(
                                        Component.literal("ОПАСНО").withStyle(color).withStyle(ChatFormatting.BOLD),
                                        null,
                                        0,
                                        20,
                                        10
                                );

                                client.level.playLocalSound(
                                        client.player.getX(),
                                        client.player.getY(),
                                        client.player.getZ(),
                                        SoundEvents.ENDER_DRAGON_GROWL,
                                        SoundSource.HOSTILE,
                                        0.7F,
                                        0.7F,
                                        false
                                );*/
                            }
                        }
                    }

                } else {
                    if (wasMacroActive) {
                        if (forward != null) {
                            forward.setDown(false);
                            back.setDown(false);
                            left.setDown(false);
                            right.setDown(false);
                            attackKey.setDown(false);
                            wasMacroActive = false;
                        }

                    }

                }
            lx = tx;
            ly = ty;
            lz = tz;
        });





    }
    public void crash(){ crash();}

    public double getAverageDistance() {
        if (lastDistances.isEmpty()) return 0.0;
        double sum = 0.0;
        for (double d : lastDistances) {
            sum += d;
        }
        return sum / lastDistances.size();
    }
    public static void sendBigMessage(Component title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (client.gui != null) {
            client.gui.setTimes(fadeIn, stay, fadeOut);
            client.gui.setTitle(title);

            if (subtitle != null) {
                client.gui.setSubtitle(Component.literal(subtitle));
            }
        }
    }
    private static BlockState BlockUnder() {
        if (client.player == null || client.level == null) return null;

        double x = client.player.getX();
        double y = client.player.getY();
        double z = client.player.getZ();

        int blockX = (int) Math.floor(x);
        int blockY = (int) Math.floor(y - 1);
        int blockZ = (int) Math.floor(z);

        BlockPos pos = new BlockPos(blockX, blockY, blockZ);

        return client.level.getBlockState(pos);
    }

    private static BlockState SideBlock(boolean isLeft) {
        if (client.player == null || client.level == null) return null;


        Direction facing = Direction.fromYRot(YAW);
        BlockPos blockPos= isLeft
                ? client.player.blockPosition().relative(facing.getCounterClockWise(Direction.Axis.Y))
                : client.player.blockPosition().relative(facing.getClockWise(Direction.Axis.Y));


        return client.level.getBlockState(blockPos);
    }
    private static int changeMode(CommandContext<FabricClientCommandSource> context) {
        if (client.player == null) return 0;
        switch (StringArgumentType.getString(context,"mode"))
        {
            case "melon" ->{
                CurrentMode = Mode.MELON;
                MacroDirection = MoveDirection.LEFT;
                YAW = 180F;
                PITCH = -58.5F;
                return 1;
            }
            case "cane" ->
            {
                CurrentMode = Mode.CANE;
                MacroDirection = MoveDirection.FORWARD;
                YAW = 135F;
                PITCH = -36F;
                return 1;
            }
            case "sun" ->
            {
                CurrentMode = Mode.SUN;
                MacroDirection = MoveDirection.FORWARD;
                YAW = -106.88F;
                PITCH = 0F;
                return 1;
            }

        }
        return 1;
    }
    private static int settings(CommandContext<FabricClientCommandSource> context) {

        if (client.player == null) return 0;
        YAW = FloatArgumentType.getFloat(context, "yaw");
        PITCH = FloatArgumentType.getFloat(context, "pitch");
        context.getSource().sendFeedback(Component.literal("changed yaw: " + YAW + " pitch: " + PITCH));
        return 1;
    }
}

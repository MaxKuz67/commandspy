package com.avilix.commandspy;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.StringReader;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.fml.common.Mod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.Locale;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;

import net.minecraft.commands.Commands;


@Mod(CommandSpyMod.MODID)
public class CommandSpyMod {
    public static final String MODID = "commandspy";
    private static final Logger LOGGER = LogManager.getLogger(MODID);
    private final Set<UUID> spies = new HashSet<>();


    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        LOGGER.info("CommandSpy avilix start");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("spy")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            CommandSourceStack src = ctx.getSource();
                            ServerPlayer player = (ServerPlayer) src.getEntity();
                            UUID id = player.getUUID();
                            if (spies.remove(id)) {
                                player.sendSystemMessage(Component.literal("§7[CommandSpy] §coff"));
                            } else {
                                spies.add(id);
                                player.sendSystemMessage(Component.literal("§7[CommandSpy] §aon"));
                            }
                            return 1;
                        })
        );
    }

    public CommandSpyMod() {
        NeoForge.EVENT_BUS.register(this);

        LOGGER.info("[CommandSpy] CommandSpyMod v{} loaded successfully",
                this.getClass().getPackage().getImplementationVersion());
    }

    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        // Получаем полный ввод (включая слэш)
        ParseResults<CommandSourceStack> parse = event.getParseResults();
        String input = parse.getReader().getString();
        CommandSourceStack src = parse.getContext().getSource();

        // Определяем отправителя и координаты
        String senderName;
        boolean isPlayer = false;
        double x = 0, y = 0, z = 0;

        if (src.getEntity() instanceof ServerPlayer ply) {
            senderName = ply.getGameProfile().getName();
            isPlayer = true;
            x = ply.getX();
            y = ply.getY();
            z = ply.getZ();
        } else {
            senderName = "CONSOLE";
        }

        // Логируем в консоль / latest.log
        String coordLog = isPlayer
                ? String.format(Locale.ROOT, "[%.1f, %.1f, %.1f]", x, y, z)
                : "[N/A]";
        LOGGER.info("[CommandSpy] {} {} → /{}", senderName, coordLog, input);

        // Рассылаем spy-игрокам с кликабельными координатами
        MinecraftServer server = src.getServer();
        for (UUID uuid : spies) {
            ServerPlayer spy = server.getPlayerList().getPlayer(uuid);
            if (spy == null || (src.getEntity() instanceof ServerPlayer sp && sp.getUUID().equals(uuid))) {
                continue;
            }

            Component coordsComponent;
            if (isPlayer) {
                // Команда для телепорта при клике
                String tpCommand = String.format(Locale.ROOT, "/tp %.1f %.1f %.1f", x, y, z);
                coordsComponent = Component.literal(
                                String.format(Locale.ROOT, "§b[%.1f, %.1f, %.1f]", x, y, z))
                        .withStyle(Style.EMPTY
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, tpCommand))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Component.literal("§eClick to teleport"))));
            } else {
                coordsComponent = Component.literal("§b[CONSOLE]");
            }

            Component message = Component.literal("§6[Spy] §f")
                    .append(Component.literal(senderName))
                    .append(Component.literal(" "))
                    .append(coordsComponent)
                    .append(Component.literal(" §7/" + input));

            spy.sendSystemMessage(message);
        }
    }

}

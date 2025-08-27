package com.avilix.commandspy;

import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.query.QueryOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import com.mojang.brigadier.ParseResults;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.fml.common.Mod;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.minecraft.server.MinecraftServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;
import java.util.Locale;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import net.minecraft.commands.Commands;


@Mod(CommandSpyMod.MODID)
public class CommandSpyMod {
    public static final String MODID = "commandspy";
    private static final Logger LOGGER = LogManager.getLogger(MODID);


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
                            User user = LuckPermsProvider.get().getUserManager().getUser(id);
                            QueryOptions emptyOptions = LuckPermsProvider.get().getContextManager().getStaticQueryOptions();
                            if (LuckPermsProvider.get().getGroupManager().getGroup("spyViewer") == null){
                                LuckPermsProvider.get().getGroupManager().createAndLoadGroup("spyViewer").join();
                            }
                            Group group = LuckPermsProvider.get().getGroupManager().getGroup("spyViewer");
                            boolean isSpy = user.getInheritedGroups(emptyOptions).contains(group);
                            if (isSpy) {
                                user.data().remove(Node.builder("group.spyViewer").build());
                                player.sendSystemMessage(Component.literal("§7[CommandSpy] §coff"));
                            } else {
                                user.data().add(Node.builder("group.spyViewer").build());
                                player.sendSystemMessage(Component.literal("§7[CommandSpy] §aon"));
                            }
                            LuckPermsProvider.get().getUserManager().saveUser(user);
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
        String senderName = src.getDisplayName().getString();
        boolean hasCords = false;
        BlockPos cords = null;
        String cordLogs;

        if (src.getEntity() instanceof ServerPlayer ply){
            hasCords = true;
            cords = ply.getOnPos();
            cordLogs = "[" + cords.toString() + "]";
        } else if (senderName.equals("@")) {
            senderName = "CommandBlock";
            hasCords = true;
            Vec3 pos = src.getPosition();
            cords = new BlockPos((int)pos.x(), (int)pos.y(), (int)pos.z());
            cordLogs = "[" + cords.toString() + "]";
        } else {
            cordLogs = "[N/A]";
        }

        LOGGER.info("[CommandSpy] {} {} → /{}", senderName, cordLogs, input);

        // Рассылаем spy-игрокам с кликабельными координатами
        MinecraftServer server = src.getServer();
        for (ServerPlayer spy : server.getPlayerList().getPlayers()) {
            if (spy == null || (src.getEntity() instanceof ServerPlayer sp && sp.is(spy))) {
                continue;
            }
            UUID id = spy.getUUID();
            User user = LuckPermsProvider.get().getUserManager().getUser(id);
            QueryOptions emptyOptions = LuckPermsProvider.get().getContextManager().getStaticQueryOptions();
            Group group = LuckPermsProvider.get().getGroupManager().getGroup("spyViewer");
            boolean isSpy = user.getInheritedGroups(emptyOptions).contains(group);
            if (!isSpy)continue;

            Component coordsComponent;
            if (hasCords) {
                // Команда для телепорта при клике
                String tpCommand = String.format(Locale.ROOT, "/tp %d %d %d", cords.getX(), cords.getY(), cords.getZ());
                coordsComponent = Component.literal(
                                String.format(Locale.ROOT, "§b[%d, %d, %d]", cords.getX(), cords.getY(), cords.getZ()))
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

package io.github.skydynamic.quickbakcupmulti.utils.permission;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class PermissionManager {
    private static final Path configPath = QuickbakcupmultiReforged.getModContainer().getConfigPath();
    private static final File config = configPath.resolve(QuickbakcupmultiReforged.MOD_NAME + "-Permission.json").toFile();
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private PermissionConfig permissionConfig;

    public PermissionManager() {
        if (!config.exists()) {
            initPermission();
        } else {
            loadPermissionByFile();
        }
    }

    public void setPermissionByPermissionLevelInt(int level, String playerName) {
        this.permissionConfig.setByPermissionType(PermissionType.getByLevelInt(level), playerName);
    }

    public void setPermissionByPermissionType(PermissionType permission, String playerName) {
        this.permissionConfig.setByPermissionType(permission, playerName);
    }

    public PermissionType getPlayerPermission(String name) {
        return permissionConfig.perm.getOrDefault(name, PermissionType.USER);
    }

    public int getPlayerPermissionLevel(String player) {
        return getPlayerPermission(player).level;
    }

    private void loadPermissionByFile() {
        try {
            FileReader reader = new FileReader(config);
            this.permissionConfig = gson.fromJson(reader, PermissionConfig.class);
            reader.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void savePermissionToFile() {
        try {
            if (config.exists()) config.delete();
            if (!config.exists()) config.createNewFile();
            FileWriter writer = new FileWriter(config);
            gson.toJson(this.permissionConfig, writer);
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void reloadPermission() {
        loadPermissionByFile();
    }

    public void initPermission() {
        try {
            this.permissionConfig = new PermissionConfig();
            config.createNewFile();
            FileWriter writer = new FileWriter(config);
            gson.toJson(this.permissionConfig, writer);
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean hasPermission(
        @NotNull CommandSourceStack source,
        int mcPermission,
        PermissionType modPermission
    ) {
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            if (checkLocalGamePermission(source)) {
                return true;
            } else {
                return source.hasPermission(mcPermission)
                    || QuickbakcupmultiReforged.getModContainer()
                    .getPermissionManager()
                    .getPlayerPermissionLevel(player.getName().getString()) >= modPermission.level;
            }
        }
        return true;
    }

    public static boolean checkLocalGamePermission(@NotNull CommandSourceStack source) {
        try {
            return getPermission(source);
        } catch (CommandSyntaxException e) {
            return false;
        }
    }

    private static boolean getPermission(CommandSourceStack source) throws CommandSyntaxException {
        boolean flag = source.hasPermission(4);
        ServerPlayer player;
        MinecraftServer server;
        if (!flag && (server = source.getServer()).isSingleplayer() && (player = source.getPlayer()) != null && source.isPlayer()) {
            flag = server.isSingleplayerOwner(new NameAndId(player.getGameProfile()));
        }
        return flag;
    }

    static class PermissionConfig {
        private final Map<String, PermissionType> perm = new HashMap<>();
        public void setByPermissionType(PermissionType type, String name) {
            perm.put(name, type);
            QuickbakcupmultiReforged.getModContainer()
                .getPermissionManager()
                .savePermissionToFile();
        }
    }
}

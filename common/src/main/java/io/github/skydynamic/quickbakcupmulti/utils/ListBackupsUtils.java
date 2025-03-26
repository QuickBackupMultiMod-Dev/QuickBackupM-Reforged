package io.github.skydynamic.quickbakcupmulti.utils;

import io.github.skydynamic.increment.storage.lib.database.StorageInfo;
import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static io.github.skydynamic.quickbakcupmulti.translate.Translate.tr;

public class ListBackupsUtils {
    private static final Logger logger = LoggerFactory.getLogger("Qbm-ListBackupsUtil");
    private static final Path backupPath = BackupManager.getBackupPath();
    private static final int BACKUPS_PER_PAGE = 5;

    private static long getDirSize(File dir) {
        return FileUtils.sizeOf(dir);
    }

    public static String truncateString(String str, int maxLength) {
        if (str.length() > maxLength) {
            return str.substring(0, maxLength - 3) + "...";
        } else {
            return str;
        }
    }

    private static int getPageCount(List<?> backupsDirList, int page) {
        int size = backupsDirList.size();
        int start = (page - 1) * BACKUPS_PER_PAGE;
        return Math.min(BACKUPS_PER_PAGE, size - start);
    }

    private static int getTotalPage(List<?> backupsList) {
        return (int) Math.ceil(backupsList.size() / (double) BACKUPS_PER_PAGE);
    }

    private static MutableComponent getPageNavigationText(String direction, int page, int totalPage, int offset) {
        MutableComponent text = Component.literal(direction);
        text.withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.nullToEmpty(direction))));
        if (page != offset && totalPage > 1) {
            text.withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/qb list " + (page + offset)))).withStyle(style -> style.withColor(ChatFormatting.AQUA));
        } else {
            text.withStyle(style -> style.withColor(ChatFormatting.DARK_GRAY));
        }
        return text;
    }

    private static MutableComponent getBackPageText(int page, int totalPage) {
        return getPageNavigationText("[<-]", page, totalPage, -1);
    }

    private static MutableComponent getNextPageText(int page, int totalPage) {
        return getPageNavigationText("[->]", page, totalPage, 1);
    }

    private static MutableComponent getSlotText(Map.Entry<String, StorageInfo> entry, int page, int num, long backupSizeB) throws IOException {
        String name = entry.getKey();
        MutableComponent backText = Component.literal("§2[▷] ");
        MutableComponent deleteText = Component.literal("§c[×] ");
        MutableComponent nameText = Component.literal("§6" + truncateString(name, 8) + "§r ");
        MutableComponent resultText = Component.literal("");
        StorageInfo result = entry.getValue();

        backText.withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/qb restore \"%s\"".formatted(name)))).withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.nullToEmpty(tr("quickbackupmulti.list_backup.slot.restore", name)))));

        deleteText.withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/qb delete %s".formatted(name)))).withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(tr("quickbackupmulti.list_backup.slot.delete", name)))));

        nameText.withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/qb show %s".formatted(name)))).withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.nullToEmpty(tr("quickbackupmulti.list_backup.slot.show", name)))));

        String desc = result.getDesc();
        if (desc.isEmpty()) desc = "Empty";
        double backupSizeMB = (double) backupSizeB / FileUtils.ONE_MB;
        double backupSizeGB = (double) backupSizeB / FileUtils.ONE_GB;
        String sizeString = (backupSizeMB >= 1000) ? String.format("%.2fGB", backupSizeGB) : String.format("%.2fMB", backupSizeMB);
        resultText.append("\n" + tr("quickbackupmulti.list_backup.slot.header", num + (5 * (page - 1))) + " ").append(nameText).append(backText).append(deleteText).append("§a" + sizeString).append(String.format(" §b%s§7: §r%s", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(result.getTimestamp()), truncateString(desc, 10)));
        return resultText;
    }

    public static MutableComponent list(int page) {
        long totalBackupSizeB = 0;
        List<Map.Entry<String, StorageInfo>> backupsInfoList = BackupManager.getBackupsList().stream().map(name -> Map.entry(name, Objects.requireNonNull(QuickbakcupmultiReforged.getDatabase().getStorageInfoWithName(name)))).sorted((c1, c2) -> -Long.compare(c1.getValue().getTimestamp(), c2.getValue().getTimestamp())).collect(Collectors.toList());
        if (backupsInfoList.isEmpty() || getPageCount(backupsInfoList, page) == 0) {
            return Component.literal(tr("quickbackupmulti.list_empty"));
        }
        int totalPage = getTotalPage(backupsInfoList);

        MutableComponent resultText = Component.literal(tr("quickbackupmulti.list_backup.title", page));
        MutableComponent backPageText = getBackPageText(page, totalPage);
        MutableComponent nextPageText = getNextPageText(page, totalPage);
        resultText.append("\n").append(backPageText).append("  ").append(tr("quickbackupmulti.list_backup.page_msg", page, totalPage)).append("  ").append(nextPageText);
        for (int j = 1; j <= getPageCount(backupsInfoList, page); j++) {
            try {
                Map.Entry<String, StorageInfo> entry = backupsInfoList.get(((j - 1) + BACKUPS_PER_PAGE * (page - 1)));
                String name = entry.getKey();
                long backupSizeB = getDirSize(backupPath.resolve(name).toFile());
                totalBackupSizeB += backupSizeB;
                resultText.append(getSlotText(entry, page, j, backupSizeB));
            } catch (IOException e) {
                logger.error("Error while listing backups", e);
                return Component.literal("Error while listing backups").withStyle(ChatFormatting.RED);
            }
        }
        double totalBackupSizeMB = (double) totalBackupSizeB / FileUtils.ONE_MB;
        double totalBackupSizeGB = (double) totalBackupSizeB / FileUtils.ONE_GB;
        String sizeString = (totalBackupSizeMB >= 1000) ? String.format("%.2fGB", totalBackupSizeGB) : String.format("%.2fMB", totalBackupSizeMB);
        resultText.append("\n" + tr("quickbackupmulti.list_backup.slot.total_space", sizeString));
        return resultText;
    }

    public static MutableComponent search(List<String> searchResultList) {
        MutableComponent resultText = Component.literal(tr("quickbackupmulti.search.success"));
        for (int i = 1; i <= searchResultList.size(); i++) {
            try {
                String name = searchResultList.get(i - 1);
                StorageInfo result = QuickbakcupmultiReforged.getDatabase().getStorageInfoWithName(name);
                long backupSizeB = getDirSize(backupPath.resolve(name).toFile());
                resultText.append(getSlotText(Map.entry(name, result), 1, i, backupSizeB));
            } catch (IOException e) {
                logger.error("Error while searching backups", e);
                return Component.literal("Error while searching backups").withStyle(ChatFormatting.RED);
            }
        }
        return resultText;
    }

    public static MutableComponent show(String name) {
        MutableComponent resultText;
        if (QuickbakcupmultiReforged.getStorager().storageExists(name)) {
            StorageInfo backupInfo = QuickbakcupmultiReforged.getDatabase().getStorageInfoWithName(name);
            resultText = Component.literal(tr("quickbackupmulti.show.header"));
            String desc = backupInfo.getDesc();
            if (desc.isEmpty()) desc = tr("quickbackupmulti.empty_comment");

            MutableComponent backText = Component.literal(tr("quickbackupmulti.show.back_button"));
            MutableComponent deleteText = Component.literal(tr("quickbackupmulti.show.delete_button"));
            backText.withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/qb restore \"%s\"".formatted(name)))).withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.nullToEmpty(tr("quickbackupmulti.list_backup.slot.restore", name)))));
            deleteText.withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/qb delete \"%s\"".formatted(name)))).withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.nullToEmpty(tr("quickbackupmulti.list_backup.slot.delete", name)))));

            resultText.append("\n").append(tr("quickbackupmulti.show.name") + ": §r" + backupInfo.getName() + "\n").append(tr("quickbackupmulti.show.desc") + ": §r" + desc + "\n").append(tr("quickbackupmulti.show.time") + ": §r" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(backupInfo.getTimestamp())).append("\n").append(backText).append(" ").append(deleteText);

        } else {
            resultText = Component.literal(tr("quickbackupmulti.show.fail"));
            resultText.withStyle(style -> style.withColor(ChatFormatting.RED));
        }
        return resultText;
    }
}
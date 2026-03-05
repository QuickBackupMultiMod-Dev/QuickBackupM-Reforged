package io.github.skydynamic.quickbackupmulti.utils;

import io.github.skydynamic.increment.storage.lib.database.StorageInfo;
import io.github.skydynamic.quickbackupmulti.QuickbackupmultiReforged;
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
import java.text.SimpleDateFormat;
import java.util.List;

import static io.github.skydynamic.quickbackupmulti.translate.Translate.tr;

public class ListBackupsUtils {
    private static final Logger logger = LoggerFactory.getLogger("Qbm-ListBackupsUtil");
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
        text.withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(Component.nullToEmpty(direction))));
        int targetPage = page + offset;
        if (targetPage >= 1 && targetPage <= totalPage) {
            text.withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand("/qb list " + (page + offset))))
                .withStyle(style -> style.withColor(ChatFormatting.AQUA));
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

    private static MutableComponent getSlotText(StorageInfo info, int page, int num, int globalIndex) throws IOException {
        String name = info.getName();
        MutableComponent backText = Component.literal("§2[▷] ");
        MutableComponent deleteText = Component.literal("§c[×] ");
        MutableComponent nameText = Component.literal("§6" + truncateString(name, 8) + "§r ");
        MutableComponent indexText = Component.literal("#" + globalIndex + " ")
            .withStyle(style -> style.withColor(ChatFormatting.GRAY));
        MutableComponent resultText = Component.literal("");

        backText.withStyle(style -> style.withClickEvent(new ClickEvent.SuggestCommand("/qb restore " + globalIndex)))
            .withStyle(style -> style.withHoverEvent(
                new HoverEvent.ShowText(Component.nullToEmpty(tr("quickbackupmulti.list_backup.slot.restore", name)))));

        deleteText.withStyle(style -> style.withClickEvent(new ClickEvent.SuggestCommand("/qb delete \"%s\"".formatted(name))))
            .withStyle(style -> style.withHoverEvent(
                new HoverEvent.ShowText(Component.literal(tr("quickbackupmulti.list_backup.slot.delete", name)))));

        nameText.withStyle(style -> style.withClickEvent(new ClickEvent.SuggestCommand("/qb show \"%s\"".formatted(name))))
            .withStyle(style -> style.withHoverEvent(
                new HoverEvent.ShowText(Component.nullToEmpty(tr("quickbackupmulti.list_backup.slot.show", name)))));

        String desc = info.getDesc();
        if (desc.isEmpty()) desc = "Empty";
        resultText.append("\n" + tr("quickbackupmulti.list_backup.slot.header", num + (5 * (page - 1))) + " ")
            .append(indexText)
            .append(nameText)
            .append(backText)
            .append(deleteText)
            .append(String.format(" §b%s§7: §r%s", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(info.getTimestamp()), truncateString(desc, 10)));
        return resultText;
    }

    public static MutableComponent list(int page) {
        long totalBackupSizeB = 0;
        List<StorageInfo> backupsInfoList = BackupManager.getSortedBackups();
        if (backupsInfoList.isEmpty() || getPageCount(backupsInfoList, page) == 0) {
            return Component.literal(tr("quickbackupmulti.list_empty"));
        }
        int totalPage = getTotalPage(backupsInfoList);
        File blogsDir = BackupManager.getBackupPath().resolve("blogs").toFile();
        if (blogsDir.exists()) {
            totalBackupSizeB = getDirSize(blogsDir);
        }

        MutableComponent resultText = Component.literal(tr("quickbackupmulti.list_backup.title", page));
        MutableComponent backPageText = getBackPageText(page, totalPage);
        MutableComponent nextPageText = getNextPageText(page, totalPage);
        resultText.append("\n")
            .append(backPageText)
            .append("  ")
            .append(tr("quickbackupmulti.list_backup.page_msg", page, totalPage))
            .append("  ")
            .append(nextPageText);
        for (int j = 1; j <= getPageCount(backupsInfoList, page); j++) {
            try {
                StorageInfo info = backupsInfoList.get(((j - 1) + BACKUPS_PER_PAGE * (page - 1)));
                int globalIndex = (page - 1) * BACKUPS_PER_PAGE + j;
                resultText.append(getSlotText(info, page, j, globalIndex));
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
                StorageInfo result = QuickbackupmultiReforged.getDatabase().getStorageInfoWithName(name);
                int globalIndex = BackupManager.getBackupIndex(name);
                if (globalIndex <= 0) {
                    globalIndex = i;
                }
                resultText.append(getSlotText(result, 1, i, globalIndex));
            } catch (IOException e) {
                logger.error("Error while searching backups", e);
                return Component.literal("Error while searching backups").withStyle(ChatFormatting.RED);
            }
        }
        return resultText;
    }

    public static MutableComponent show(String name) {
        MutableComponent resultText;
        if (QuickbackupmultiReforged.getDatabase().storageExists(name)) {
            StorageInfo backupInfo = QuickbackupmultiReforged.getDatabase().getStorageInfoWithName(name);
            resultText = Component.literal(tr("quickbackupmulti.show.header"));
            String desc = backupInfo.getDesc();
            if (desc.isEmpty()) desc = tr("quickbackupmulti.empty_comment");

            MutableComponent backText = Component.literal(tr("quickbackupmulti.show.back_button"));
            MutableComponent deleteText = Component.literal(tr("quickbackupmulti.show.delete_button"));
            backText.withStyle(style -> style.withClickEvent(new ClickEvent.SuggestCommand("/qb restore \"%s\"".formatted(name))))
                .withStyle(style -> style.withHoverEvent(
                    new HoverEvent.ShowText(Component.nullToEmpty(tr("quickbackupmulti.list_backup.slot.restore", name)))));
            deleteText.withStyle(style -> style.withClickEvent(new ClickEvent.SuggestCommand("/qb delete \"%s\"".formatted(name))))
                .withStyle(style -> style.withHoverEvent(
                    new HoverEvent.ShowText(Component.nullToEmpty(tr("quickbackupmulti.list_backup.slot.delete", name)))));

            resultText.append("\n")
                .append(tr("quickbackupmulti.show.name") + ": §r" + backupInfo.getName() + "\n")
                .append(tr("quickbackupmulti.show.desc") + ": §r" + desc + "\n")
                .append(tr("quickbackupmulti.show.time") + ": §r" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(backupInfo.getTimestamp()))
                .append("\n")
                .append(backText)
                .append(" ")
                .append(deleteText);

        } else {
            resultText = Component.literal(tr("quickbackupmulti.show.fail"));
            resultText.withStyle(style -> style.withColor(ChatFormatting.RED));
        }
        return resultText;
    }
}

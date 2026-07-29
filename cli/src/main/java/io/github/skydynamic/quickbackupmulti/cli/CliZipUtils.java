package io.github.skydynamic.quickbackupmulti.cli;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Standalone copy of the mod's {@code utils.ZipUtils} (the CLI does not share source with {@code common}).
 */
public final class CliZipUtils {
    private CliZipUtils() {
    }

    public static void zipDirectory(Path srcDir, Path zipFile) throws IOException {
        if (zipFile.getParent() != null) {
            Files.createDirectories(zipFile.getParent());
        }
        try (OutputStream os = Files.newOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(os);
             Stream<Path> walk = Files.walk(srcDir)) {
            walk.filter(Files::isRegularFile).forEach(path -> {
                String entryName = srcDir.relativize(path).toString().replace('\\', '/');
                try {
                    zos.putNextEntry(new ZipEntry(entryName));
                    Files.copy(path, zos);
                    zos.closeEntry();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw e;
        }
    }
}

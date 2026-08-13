package io.github.skydynamic.quickbackupmulti.database;

import org.h2.mvstore.db.NullValueDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class H2ClassNameCompatTest {
    @Test
    void resolvesCanonicalH2ClassName() throws ClassNotFoundException {
        assertSame(
            NullValueDataType.class,
            H2ClassNameCompat.forName("org.h2.mvstore.db.NullValueDataType")
        );
    }

    @Test
    void remapsRelocatedH2ClassName() throws ClassNotFoundException {
        assertSame(
            NullValueDataType.class,
            H2ClassNameCompat.forName(
                "io.github.skydynamic.quickbackupmulti.repack.org.h2.mvstore.db.NullValueDataType"
            )
        );
    }

    @Test
    void unknownClassNameStillFails() {
        assertThrows(
            ClassNotFoundException.class,
            () -> H2ClassNameCompat.forName("com.example.DoesNotExist")
        );
    }

    @Test
    void opensUnrelocatedH2Database(@TempDir Path tempDir) throws Exception {
        String url = "jdbc:h2:" + tempDir.resolve("qbm").toAbsolutePath();
        try (Connection connection = DriverManager.getConnection(url);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE qbm_probe(id INT PRIMARY KEY)");
            statement.execute("INSERT INTO qbm_probe VALUES (1)");
        }
        try (Connection connection = DriverManager.getConnection(url);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT id FROM qbm_probe")) {
            resultSet.next();
            assertEquals(1, resultSet.getInt(1));
        }
    }
}

package io.github.skydynamic.quickbackupmulti.harness;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LibraryClasspathTest {
    @Test
    void laterArtifactVersionWins() {
        Path asm93 = Path.of("libraries/org/ow2/asm/asm/9.3/asm-9.3.jar");
        Path asm910 = Path.of("libraries/org/ow2/asm/asm/9.10.1/asm-9.10.1.jar");
        Path lwjgl = Path.of("libraries/org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar");

        List<Path> out = LibraryClasspath.dedupe(List.of(asm93, lwjgl, asm910));

        assertEquals(List.of(lwjgl, asm910), out);
        assertFalse(out.contains(asm93));
    }

    @Test
    void nativeClassifierIsKeptBesideTheMainJar() {
        Path main = Path.of("libraries/com/mojang/jtracy/1.0.29/jtracy-1.0.29.jar");
        Path natives = Path.of("libraries/com/mojang/jtracy/1.0.29/jtracy-1.0.29-natives-windows.jar");

        List<Path> out = LibraryClasspath.dedupe(List.of(main, natives));

        assertEquals(List.of(main, natives), out);
    }
}

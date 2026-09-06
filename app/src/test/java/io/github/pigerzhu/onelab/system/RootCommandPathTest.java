package io.github.pigerzhu.onelab.system;

import static org.junit.Assert.assertEquals;

import java.io.File;
import org.junit.Test;

public final class RootCommandPathTest {
    @Test
    public void selectsFirstExecutableAbsoluteSuPath() {
        assertEquals("/product/bin/su", RootCommandPath.select(
                path -> new File(path).getName().equals("su") && path.startsWith("/product")));
    }
}

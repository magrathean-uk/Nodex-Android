package com.nodex.client.branding

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

class LauncherIconAssetTest {

    @Test
    fun canonicalStoreAndOnboardingIconsUseSameSourceBytes() {
        val canonical = projectFile("branding/nodex-android-logo.png", "../branding/nodex-android-logo.png").readBytes()

        assertArrayEquals(canonical, projectFile("branding/play-store-icon.png", "../branding/play-store-icon.png").readBytes())
        assertArrayEquals(canonical, appFile("src/main/res/drawable-nodpi/ic_onboarding_logo.png").readBytes())
    }

    @Test
    fun launcherIconAssetsHaveExpectedAndroidDensities() {
        val legacySizes = mapOf(
            "mdpi" to 48,
            "hdpi" to 72,
            "xhdpi" to 96,
            "xxhdpi" to 144,
            "xxxhdpi" to 192
        )
        val foregroundSizes = mapOf(
            "mdpi" to 108,
            "hdpi" to 162,
            "xhdpi" to 216,
            "xxhdpi" to 324,
            "xxxhdpi" to 432
        )

        legacySizes.forEach { (density, size) ->
            assertPngSize("src/main/res/mipmap-$density/ic_launcher.png", size)
            assertPngSize("src/main/res/mipmap-$density/ic_launcher_round.png", size)
        }
        foregroundSizes.forEach { (density, size) ->
            assertPngSize("src/main/res/mipmap-$density/ic_launcher_foreground.png", size)
        }
    }

    @Test
    fun staleLauncherDrawableVectorsAreNotPresent() {
        assertTrue(appFile("src/main/res/drawable/ic_launcher_background.xml").exists().not())
        assertTrue(appFile("src/main/res/drawable/ic_launcher_foreground.xml").exists().not())
    }

    private fun assertPngSize(path: String, expectedSize: Int) {
        val image = ImageIO.read(appFile(path))
        assertEquals(path, expectedSize, image.width)
        assertEquals(path, expectedSize, image.height)
    }

    private fun appFile(path: String): File {
        val moduleRelative = File(path)
        if (moduleRelative.exists()) return moduleRelative
        return File("app/$path")
    }

    private fun projectFile(rootPath: String, modulePath: String): File {
        val rootRelative = File(rootPath)
        if (rootRelative.exists()) return rootRelative
        return File(modulePath)
    }
}

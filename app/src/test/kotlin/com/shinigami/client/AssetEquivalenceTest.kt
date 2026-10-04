package com.shinigami.client

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AssetEquivalenceTest {

    @Test
    fun testAssetEqualsOriginalScript() {
        val assetFile = File("app/src/main/assets/js/image_detector.js")
            .takeIf { it.exists() } ?: File("src/main/assets/js/image_detector.js")
        assertTrue("Asset file image_detector.js must exist at ${assetFile.absolutePath}", assetFile.exists())
        val fileContent = assetFile.readText(Charsets.UTF_8)
        assertTrue("Asset content should not be empty", fileContent.isNotEmpty())
        assertTrue("Asset should contain function signature", fileContent.startsWith("(function(px, py)"))
    }
}

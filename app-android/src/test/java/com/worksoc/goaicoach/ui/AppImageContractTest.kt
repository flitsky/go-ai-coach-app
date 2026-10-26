package com.worksoc.goaicoach.ui

import com.worksoc.goaicoach.architecture.RepoPaths
import com.worksoc.goaicoach.architecture.readContractSource
import com.worksoc.goaicoach.ui.designsystem.AppImage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 그림 목록([AppImage])과 두 플랫폼의 대응이 어긋나지 않는가(백로그 #294 — S9). 항목 이름이 곧 안드로이드 리소스 이름이다.
 *  - 안드로이드: `AppImageAndroid.kt`가 항목마다 `AppImage.x -> R.drawable.x`를 적었다(이름으로 찾지 않는다 — R8, #47).
 *  - 리소스: `res/drawable*`에 그 이름의 파일이 있다 — iOS는 비트맵을 같은 이름으로 앱 묶음에서 읽고, 벡터(XML)는
 *    `scripts/ios-vector-drawables.py`가 만든 코드를 쓴다.
 */
class AppImageContractTest {
    private val res = File(RepoPaths.root, "app-android/src/main/res")

    @Test
    fun androidMapsEveryImageToTheSameNamedDrawable() {
        val source = RepoPaths.appAndroid("ui/designsystem/AppImageAndroid.kt").readContractSource()
        val missing = AppImage.entries.filterNot { image -> source.contains("AppImage.${image.name} -> R.drawable.${image.name}") }
        assertEquals("AppImageAndroid.kt에 빠진 그림", emptyList<AppImage>(), missing)
    }

    @Test
    fun everyImageHasAResourceFileAndEveryVectorHasIosCode() {
        val files = res.listFiles { dir -> dir.isDirectory && dir.name.startsWith("drawable") }.orEmpty().flatMap { it.listFiles().orEmpty().toList() }
        val iosVectors = File(RepoPaths.root, "app-android/src/iosMain/kotlin/com/worksoc/goaicoach/ui/designsystem/IosVectorDrawables.kt").readText()
        for (image in AppImage.entries) {
            val file = files.firstOrNull { it.nameWithoutExtension == image.name }
            assertTrue("리소스가 없다: ${image.name}", file != null)
            if (file!!.extension == "xml") {
                assertTrue("iOS 벡터 코드가 없다(scripts/ios-vector-drawables.py): ${image.name}", iosVectors.contains("name = \"${image.name}\""))
            }
        }
    }
}

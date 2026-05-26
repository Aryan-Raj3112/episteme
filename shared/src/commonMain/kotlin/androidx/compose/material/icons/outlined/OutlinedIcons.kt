@file:Suppress("ObjectPropertyName", "unused")

package androidx.compose.material.icons.outlined

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

val Icons.Outlined.AccountCircle: ImageVector
    get() = EpistemeOutlinedIcons.accountCircle

val Icons.Outlined.Email: ImageVector
    get() = EpistemeOutlinedIcons.email

val Icons.Outlined.FavoriteBorder: ImageVector
    get() = EpistemeOutlinedIcons.favoriteBorder

val Icons.Outlined.Feedback: ImageVector
    get() = EpistemeOutlinedIcons.feedback

val Icons.Outlined.FileOpen: ImageVector
    get() = EpistemeOutlinedIcons.fileOpen

val Icons.Outlined.Gavel: ImageVector
    get() = EpistemeOutlinedIcons.gavel

val Icons.Outlined.Policy: ImageVector
    get() = EpistemeOutlinedIcons.policy

private object EpistemeOutlinedIcons {
    val accountCircle: ImageVector by lazy {
        materialIcon(
            name = "AccountCircle",
            defaultWidth = 24f,
            defaultHeight = 24f,
            viewportWidth = 960f,
            viewportHeight = 960f,
            autoMirror = false,
            paths = listOf(
                """M234,684Q285,645 348,622.5Q411,600 480,600Q549,600 612,622.5Q675,645 726,684Q761,643 780.5,591Q800,539 800,480Q800,347 706.5,253.5Q613,160 480,160Q347,160 253.5,253.5Q160,347 160,480Q160,539 179.5,591Q199,643 234,684ZM380.5,479.5Q340,439 340,380Q340,321 380.5,280.5Q421,240 480,240Q539,240 579.5,280.5Q620,321 620,380Q620,439 579.5,479.5Q539,520 480,520Q421,520 380.5,479.5ZM480,880Q397,880 324,848.5Q251,817 197,763Q143,709 111.5,636Q80,563 80,480Q80,397 111.5,324Q143,251 197,197Q251,143 324,111.5Q397,80 480,80Q563,80 636,111.5Q709,143 763,197Q817,251 848.5,324Q880,397 880,480Q880,563 848.5,636Q817,709 763,763Q709,817 636,848.5Q563,880 480,880ZM580,784.5Q627,769 666,740Q627,711 580,695.5Q533,680 480,680Q427,680 380,695.5Q333,711 294,740Q333,769 380,784.5Q427,800 480,800Q533,800 580,784.5ZM523,423Q540,406 540,380Q540,354 523,337Q506,320 480,320Q454,320 437,337Q420,354 420,380Q420,406 437,423Q454,440 480,440Q506,440 523,423ZM480,380Q480,380 480,380Q480,380 480,380Q480,380 480,380Q480,380 480,380Q480,380 480,380Q480,380 480,380Q480,380 480,380Q480,380 480,380ZM480,740Q480,740 480,740Q480,740 480,740Q480,740 480,740Q480,740 480,740Q480,740 480,740Q480,740 480,740Q480,740 480,740Q480,740 480,740Z"""
            )
        )
    }

    val email: ImageVector by lazy {
        materialIcon(
            name = "Email",
            defaultWidth = 24f,
            defaultHeight = 24f,
            viewportWidth = 960f,
            viewportHeight = 960f,
            autoMirror = false,
            paths = listOf(
                """M160,800Q127,800 103.5,776.5Q80,753 80,720L80,240Q80,207 103.5,183.5Q127,160 160,160L800,160Q833,160 856.5,183.5Q880,207 880,240L880,720Q880,753 856.5,776.5Q833,800 800,800L160,800ZM480,520L160,320L160,720Q160,720 160,720Q160,720 160,720L800,720Q800,720 800,720Q800,720 800,720L800,320L480,520ZM480,440L800,240L160,240L480,440ZM160,320L160,240L160,240L160,320L160,720Q160,720 160,720Q160,720 160,720L160,720Q160,720 160,720Q160,720 160,720L160,320Z"""
            )
        )
    }

    val favoriteBorder: ImageVector by lazy {
        materialIcon(
            name = "FavoriteBorder",
            defaultWidth = 24f,
            defaultHeight = 24f,
            viewportWidth = 960f,
            viewportHeight = 960f,
            autoMirror = false,
            paths = listOf(
                """M480,840L422,788Q321,697 255,631Q189,565 150,512.5Q111,460 95.5,416Q80,372 80,326Q80,232 143,169Q206,106 300,106Q352,106 399,128Q446,150 480,190Q514,150 561,128Q608,106 660,106Q754,106 817,169Q880,232 880,326Q880,372 864.5,416Q849,460 810,512.5Q771,565 705,631Q639,697 538,788L480,840ZM480,732Q576,646 638,584.5Q700,523 736,477.5Q772,432 786,396.5Q800,361 800,326Q800,266 760,226Q720,186 660,186Q613,186 573,212.5Q533,239 518,280L518,280L442,280L442,280Q427,239 387,212.5Q347,186 300,186Q240,186 200,226Q160,266 160,326Q160,361 174,396.5Q188,432 224,477.5Q260,523 322,584.5Q384,646 480,732ZM480,459Q480,459 480,459Q480,459 480,459Q480,459 480,459Q480,459 480,459Q480,459 480,459Q480,459 480,459Q480,459 480,459Q480,459 480,459L480,459L480,459L480,459Q480,459 480,459Q480,459 480,459Q480,459 480,459Q480,459 480,459Q480,459 480,459Q480,459 480,459Q480,459 480,459Q480,459 480,459Z"""
            )
        )
    }

    val feedback: ImageVector by lazy {
        materialIcon(
            name = "Feedback",
            defaultWidth = 24f,
            defaultHeight = 24f,
            viewportWidth = 960f,
            viewportHeight = 960f,
            autoMirror = false,
            paths = listOf(
                """M480,600Q497,600 508.5,588.5Q520,577 520,560Q520,543 508.5,531.5Q497,520 480,520Q463,520 451.5,531.5Q440,543 440,560Q440,577 451.5,588.5Q463,600 480,600ZM440,440L520,440L520,200L440,200L440,440ZM80,880L80,160Q80,127 103.5,103.5Q127,80 160,80L800,80Q833,80 856.5,103.5Q880,127 880,160L880,640Q880,673 856.5,696.5Q833,720 800,720L240,720L80,880ZM206,640L800,640Q800,640 800,640Q800,640 800,640L800,160Q800,160 800,160Q800,160 800,160L160,160Q160,160 160,160Q160,160 160,160L160,685L206,640ZM160,640L160,640L160,160Q160,160 160,160Q160,160 160,160L160,160Q160,160 160,160Q160,160 160,160L160,640Q160,640 160,640Q160,640 160,640Z"""
            )
        )
    }

    val fileOpen: ImageVector by lazy {
        materialIcon(
            name = "FileOpen",
            defaultWidth = 24f,
            defaultHeight = 24f,
            viewportWidth = 960f,
            viewportHeight = 960f,
            autoMirror = false,
            paths = listOf(
                """M240,880Q207,880 183.5,856.5Q160,833 160,800L160,160Q160,127 183.5,103.5Q207,80 240,80L560,80L800,320L800,560L720,560L720,360L520,360L520,160L240,160Q240,160 240,160Q240,160 240,160L240,800Q240,800 240,800Q240,800 240,800L600,800L600,880L240,880ZM878,895L760,777L760,866L680,866L680,640L906,640L906,720L816,720L934,838L878,895ZM240,800L240,560L240,560L240,360L240,160L240,160Q240,160 240,160Q240,160 240,160L240,800Q240,800 240,800Q240,800 240,800Z"""
            )
        )
    }

    val gavel: ImageVector by lazy {
        materialIcon(
            name = "Gavel",
            defaultWidth = 24f,
            defaultHeight = 24f,
            viewportWidth = 960f,
            viewportHeight = 960f,
            autoMirror = false,
            paths = listOf(
                """M160,840L160,760L640,760L640,840L160,840ZM386,646L160,420L244,334L472,560L386,646ZM640,392L414,164L500,80L726,306L640,392ZM824,800L302,278L358,222L880,744L824,800Z"""
            )
        )
    }

    val policy: ImageVector by lazy {
        materialIcon(
            name = "Policy",
            defaultWidth = 24f,
            defaultHeight = 24f,
            viewportWidth = 960f,
            viewportHeight = 960f,
            autoMirror = false,
            paths = listOf(
                """M480,880Q341,845 250.5,720.5Q160,596 160,444L160,200L480,80L800,200L800,444Q800,529 771,607.5Q742,686 688,746L560,618Q542,629 521.5,634.5Q501,640 480,640Q414,640 367,593Q320,546 320,480Q320,414 367,367Q414,320 480,320Q546,320 593,367Q640,414 640,480Q640,502 634.5,522.5Q629,543 618,562L678,622Q698,581 709,536Q720,491 720,444L720,255L480,165L240,255L240,444Q240,565 308,664Q376,763 480,796Q506,788 529.5,775.5Q553,763 576,746L632,802Q599,829 560.5,849Q522,869 480,880ZM536.5,536.5Q560,513 560,480Q560,447 536.5,423.5Q513,400 480,400Q447,400 423.5,423.5Q400,447 400,480Q400,513 423.5,536.5Q447,560 480,560Q513,560 536.5,536.5ZM488,483L488,483Q488,483 488,483Q488,483 488,483L488,483Q488,483 488,483Q488,483 488,483L488,483L488,483L488,483L488,483Q488,483 488,483Q488,483 488,483Q488,483 488,483Q488,483 488,483Z"""
            )
        )
    }

}

private fun materialIcon(
    name: String,
    defaultWidth: Float,
    defaultHeight: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    autoMirror: Boolean,
    paths: List<String>
): ImageVector {
    return ImageVector.Builder(
        name = name,
        defaultWidth = defaultWidth.dp,
        defaultHeight = defaultHeight.dp,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        autoMirror = autoMirror
    ).apply {
        paths.forEach { pathData ->
            addPath(
                pathData = PathParser().parsePathString(pathData).toNodes(),
                fill = SolidColor(Color.Black)
            )
        }
    }.build()
}


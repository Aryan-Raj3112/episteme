@file:Suppress("ObjectPropertyName", "unused")

package androidx.compose.material.icons.automirrored.filled

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

val Icons.AutoMirrored.Filled.ArrowBack: ImageVector
    get() = EpistemeAutoMirroredFilledIcons.arrowBack

val Icons.AutoMirrored.Filled.ArrowForward: ImageVector
    get() = EpistemeAutoMirroredFilledIcons.arrowForward

val Icons.AutoMirrored.Filled.KeyboardArrowRight: ImageVector
    get() = EpistemeAutoMirroredFilledIcons.keyboardArrowRight

val Icons.AutoMirrored.Filled.LibraryBooks: ImageVector
    get() = EpistemeAutoMirroredFilledIcons.libraryBooks

val Icons.AutoMirrored.Filled.List: ImageVector
    get() = EpistemeAutoMirroredFilledIcons.list

val Icons.AutoMirrored.Filled.MenuBook: ImageVector
    get() = EpistemeAutoMirroredFilledIcons.menuBook

val Icons.AutoMirrored.Filled.NavigateBefore: ImageVector
    get() = EpistemeAutoMirroredFilledIcons.navigateBefore

val Icons.AutoMirrored.Filled.NavigateNext: ImageVector
    get() = EpistemeAutoMirroredFilledIcons.navigateNext

val Icons.AutoMirrored.Filled.OpenInNew: ImageVector
    get() = EpistemeAutoMirroredFilledIcons.openInNew

val Icons.AutoMirrored.Filled.Redo: ImageVector
    get() = EpistemeAutoMirroredFilledIcons.redo

val Icons.AutoMirrored.Filled.Sort: ImageVector
    get() = EpistemeAutoMirroredFilledIcons.sort

val Icons.AutoMirrored.Filled.Undo: ImageVector
    get() = EpistemeAutoMirroredFilledIcons.undo

val Icons.AutoMirrored.Filled.VolumeUp: ImageVector
    get() = EpistemeAutoMirroredFilledIcons.volumeUp

private object EpistemeAutoMirroredFilledIcons {
    val arrowBack: ImageVector by lazy {
        materialIcon(
            name = "ArrowBack",
            defaultWidth = 24f,
            defaultHeight = 24f,
            viewportWidth = 960f,
            viewportHeight = 960f,
            autoMirror = true,
            paths = listOf(
                """M313,520L537,744L480,800L160,480L480,160L537,216L313,440L800,440L800,520L313,520Z"""
            )
        )
    }

    val arrowForward: ImageVector by lazy {
        materialIcon(
            name = "ArrowForward",
            defaultWidth = 24f,
            defaultHeight = 24f,
            viewportWidth = 960f,
            viewportHeight = 960f,
            autoMirror = true,
            paths = listOf(
                """M647,520L160,520L160,440L647,440L423,216L480,160L800,480L480,800L423,744L647,520Z"""
            )
        )
    }

    val keyboardArrowRight: ImageVector by lazy {
        materialIcon(
            name = "KeyboardArrowRight",
            defaultWidth = 24f,
            defaultHeight = 24f,
            viewportWidth = 960f,
            viewportHeight = 960f,
            autoMirror = true,
            paths = listOf(
                """M504,480L320,296L376,240L616,480L376,720L320,664L504,480Z"""
            )
        )
    }

    val libraryBooks: ImageVector by lazy {
        materialIcon(
            name = "LibraryBooks",
            defaultWidth = 24f,
            defaultHeight = 24f,
            viewportWidth = 960f,
            viewportHeight = 960f,
            autoMirror = true,
            paths = listOf(
                """M400,560L560,560L560,480L400,480L400,560ZM400,440L720,440L720,360L400,360L400,440ZM400,320L720,320L720,240L400,240L400,320ZM320,720Q287,720 263.5,696.5Q240,673 240,640L240,160Q240,127 263.5,103.5Q287,80 320,80L800,80Q833,80 856.5,103.5Q880,127 880,160L880,640Q880,673 856.5,696.5Q833,720 800,720L320,720ZM320,640L800,640Q800,640 800,640Q800,640 800,640L800,160Q800,160 800,160Q800,160 800,160L320,160Q320,160 320,160Q320,160 320,160L320,640Q320,640 320,640Q320,640 320,640ZM160,880Q127,880 103.5,856.5Q80,833 80,800L80,240L160,240L160,800Q160,800 160,800Q160,800 160,800L720,800L720,880L160,880ZM320,160L320,160Q320,160 320,160Q320,160 320,160L320,640Q320,640 320,640Q320,640 320,640L320,640Q320,640 320,640Q320,640 320,640L320,160Q320,160 320,160Q320,160 320,160Z"""
            )
        )
    }

    val list: ImageVector by lazy {
        materialIcon(
            name = "List",
            defaultWidth = 24f,
            defaultHeight = 24f,
            viewportWidth = 960f,
            viewportHeight = 960f,
            autoMirror = true,
            paths = listOf(
                """M280,360L280,280L840,280L840,360L280,360ZM280,520L280,440L840,440L840,520L280,520ZM280,680L280,600L840,600L840,680L280,680ZM160,360Q143,360 131.5,348.5Q120,337 120,320Q120,303 131.5,291.5Q143,280 160,280Q177,280 188.5,291.5Q200,303 200,320Q200,337 188.5,348.5Q177,360 160,360ZM160,520Q143,520 131.5,508.5Q120,497 120,480Q120,463 131.5,451.5Q143,440 160,440Q177,440 188.5,451.5Q200,463 200,480Q200,497 188.5,508.5Q177,520 160,520ZM160,680Q143,680 131.5,668.5Q120,657 120,640Q120,623 131.5,611.5Q143,600 160,600Q177,600 188.5,611.5Q200,623 200,640Q200,657 188.5,668.5Q177,680 160,680Z"""
            )
        )
    }

    val menuBook: ImageVector by lazy {
        materialIcon(
            name = "MenuBook",
            defaultWidth = 24f,
            defaultHeight = 24f,
            viewportWidth = 960f,
            viewportHeight = 960f,
            autoMirror = true,
            paths = listOf(
                """M560,396L560,328Q593,314 627.5,307Q662,300 700,300Q726,300 751,304Q776,308 800,314L800,378Q776,369 751.5,364.5Q727,360 700,360Q662,360 627,369.5Q592,379 560,396ZM560,616L560,548Q593,534 627.5,527Q662,520 700,520Q726,520 751,524Q776,528 800,534L800,598Q776,589 751.5,584.5Q727,580 700,580Q662,580 627,589Q592,598 560,616ZM560,506L560,438Q593,424 627.5,417Q662,410 700,410Q726,410 751,414Q776,418 800,424L800,488Q776,479 751.5,474.5Q727,470 700,470Q662,470 627,479.5Q592,489 560,506ZM260,640Q307,640 351.5,650.5Q396,661 440,682L440,288Q399,264 353,252Q307,240 260,240Q224,240 188.5,247Q153,254 120,268Q120,268 120,268Q120,268 120,268L120,664Q120,664 120,664Q120,664 120,664Q155,652 189.5,646Q224,640 260,640ZM520,682Q564,661 608.5,650.5Q653,640 700,640Q736,640 770.5,646Q805,652 840,664Q840,664 840,664Q840,664 840,664L840,268Q840,268 840,268Q840,268 840,268Q807,254 771.5,247Q736,240 700,240Q653,240 607,252Q561,264 520,288L520,682ZM480,800Q432,762 376,741Q320,720 260,720Q218,720 177.5,731Q137,742 100,762Q79,773 59.5,761Q40,749 40,726L40,244Q40,233 45.5,223Q51,213 62,208Q108,184 158,172Q208,160 260,160Q318,160 373.5,175Q429,190 480,220Q531,190 586.5,175Q642,160 700,160Q752,160 802,172Q852,184 898,208Q909,213 914.5,223Q920,233 920,244L920,726Q920,749 900.5,761Q881,773 860,762Q823,742 782.5,731Q742,720 700,720Q640,720 584,741Q528,762 480,800ZM280,466Q280,466 280,466Q280,466 280,466L280,466Q280,466 280,466Q280,466 280,466Q280,466 280,466Q280,466 280,466Q280,466 280,466Q280,466 280,466L280,466Q280,466 280,466Q280,466 280,466Q280,466 280,466Q280,466 280,466Z"""
            )
        )
    }

    val navigateBefore: ImageVector by lazy {
        materialIcon(
            name = "NavigateBefore",
            defaultWidth = 24f,
            defaultHeight = 24f,
            viewportWidth = 960f,
            viewportHeight = 960f,
            autoMirror = true,
            paths = listOf(
                """M560,720L320,480L560,240L616,296L432,480L616,664L560,720Z"""
            )
        )
    }

    val navigateNext: ImageVector by lazy {
        materialIcon(
            name = "NavigateNext",
            defaultWidth = 24f,
            defaultHeight = 24f,
            viewportWidth = 960f,
            viewportHeight = 960f,
            autoMirror = true,
            paths = listOf(
                """M504,480L320,296L376,240L616,480L376,720L320,664L504,480Z"""
            )
        )
    }

    val openInNew: ImageVector by lazy {
        materialIcon(
            name = "OpenInNew",
            defaultWidth = 24f,
            defaultHeight = 24f,
            viewportWidth = 960f,
            viewportHeight = 960f,
            autoMirror = true,
            paths = listOf(
                """M200,840Q167,840 143.5,816.5Q120,793 120,760L120,200Q120,167 143.5,143.5Q167,120 200,120L480,120L480,200L200,200Q200,200 200,200Q200,200 200,200L200,760Q200,760 200,760Q200,760 200,760L760,760Q760,760 760,760Q760,760 760,760L760,480L840,480L840,760Q840,793 816.5,816.5Q793,840 760,840L200,840ZM388,628L332,572L704,200L560,200L560,120L840,120L840,400L760,400L760,256L388,628Z"""
            )
        )
    }

    val redo: ImageVector by lazy {
        materialIcon(
            name = "Redo",
            defaultWidth = 24f,
            defaultHeight = 24f,
            viewportWidth = 960f,
            viewportHeight = 960f,
            autoMirror = true,
            paths = listOf(
                """M396,760Q299,760 229.5,697Q160,634 160,540Q160,446 229.5,383Q299,320 396,320L648,320L544,216L600,160L800,360L600,560L544,504L648,400L396,400Q333,400 286.5,440Q240,480 240,540Q240,600 286.5,640Q333,680 396,680L680,680L680,760L396,760Z"""
            )
        )
    }

    val sort: ImageVector by lazy {
        materialIcon(
            name = "Sort",
            defaultWidth = 24f,
            defaultHeight = 24f,
            viewportWidth = 960f,
            viewportHeight = 960f,
            autoMirror = true,
            paths = listOf(
                """M120,720L120,640L360,640L360,720L120,720ZM120,520L120,440L600,440L600,520L120,520ZM120,320L120,240L840,240L840,320L120,320Z"""
            )
        )
    }

    val undo: ImageVector by lazy {
        materialIcon(
            name = "Undo",
            defaultWidth = 24f,
            defaultHeight = 24f,
            viewportWidth = 960f,
            viewportHeight = 960f,
            autoMirror = true,
            paths = listOf(
                """M280,760L280,680L564,680Q627,680 673.5,640Q720,600 720,540Q720,480 673.5,440Q627,400 564,400L312,400L416,504L360,560L160,360L360,160L416,216L312,320L564,320Q661,320 730.5,383Q800,446 800,540Q800,634 730.5,697Q661,760 564,760L280,760Z"""
            )
        )
    }

    val volumeUp: ImageVector by lazy {
        materialIcon(
            name = "VolumeUp",
            defaultWidth = 24f,
            defaultHeight = 24f,
            viewportWidth = 960f,
            viewportHeight = 960f,
            autoMirror = true,
            paths = listOf(
                """M560,829L560,747Q650,721 705,647Q760,573 760,479Q760,385 705,311Q650,237 560,211L560,129Q684,157 762,254.5Q840,352 840,479Q840,606 762,703.5Q684,801 560,829ZM120,600L120,360L280,360L480,160L480,800L280,600L120,600ZM560,640L560,318Q607,340 633.5,384Q660,428 660,480Q660,531 633.5,574.5Q607,618 560,640ZM400,354L314,440L200,440L200,520L314,520L400,606L400,354ZM300,480L300,480L300,480L300,480L300,480L300,480Z"""
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


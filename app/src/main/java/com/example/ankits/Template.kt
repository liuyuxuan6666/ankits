package com.example.ankits

import android.graphics.Color
import android.graphics.Typeface

data class SectionStyle(
    val bgColor: Int = Color.WHITE,
    val textColor: Int = Color.parseColor("#333333"),
    val titleSizeSp: Float = 22f,
    val bodySizeSp: Float = 15f,
    val paddingLeftDp: Float = 20f,
    val paddingTopDp: Float = 16f,
    val paddingRightDp: Float = 20f,
    val paddingBottomDp: Float = 16f,
    val titleBold: Boolean = true,
    val cornerRadiusDp: Float = 0f,
    val dividerColor: Int = 0,
    val accentWidthDp: Float = 0f,
    val accentColor: Int = 0,
    val titleBgColor: Int = 0,
    val titleTextColor: Int = 0,
    val titleCornerRadiusDp: Float = 0f
)

data class Template(
    val name: String,
    val canvasBg: Int,
    val heroStyle: SectionStyle,
    val mainStyle: SectionStyle,
    val subStyle: SectionStyle,
    val sectionGapDp: Float = 12f,
    val outerPaddingDp: Float = 16f,
    val canvasCornerRadiusDp: Float = 0f,
    val bodyLineSpacing: Float = 1.3f
)

object Templates {

    val SIMPLE = Template(
        name = "简约白",
        canvasBg = Color.parseColor("#FCFCFC"),
        outerPaddingDp = 20f,
        sectionGapDp = 0f,
        heroStyle = SectionStyle(
            bgColor = Color.parseColor("#FFFFFF"),
            textColor = Color.parseColor("#161616"),
            titleSizeSp = 24f,
            bodySizeSp = 16f,
            paddingLeftDp = 36f,
            paddingTopDp = 30f,
            paddingRightDp = 36f,
            paddingBottomDp = 30f,
            titleBold = true
        ),
        mainStyle = SectionStyle(
            bgColor = Color.parseColor("#FCFCFC"),
            textColor = Color.parseColor("#666666"),
            titleSizeSp = 20f,
            bodySizeSp = 14f,
            paddingLeftDp = 36f,
            paddingTopDp = 20f,
            paddingRightDp = 36f,
            paddingBottomDp = 20f,
            titleBold = true,
            dividerColor = Color.parseColor("#E8E8E8")
        ),
        subStyle = SectionStyle(
            bgColor = Color.parseColor("#FCFCFC"),
            textColor = Color.parseColor("#666666"),
            titleSizeSp = 18f,
            bodySizeSp = 14f,
            paddingLeftDp = 36f,
            paddingTopDp = 16f,
            paddingRightDp = 36f,
            paddingBottomDp = 16f,
            titleBold = true,
            dividerColor = Color.parseColor("#E8E8E8")
        )
    )

    val TECH = Template(
        name = "科技蓝",
        canvasBg = Color.parseColor("#CCEDFF"),
        outerPaddingDp = 12f,
        sectionGapDp = 12f,
        heroStyle = SectionStyle(
            bgColor = Color.TRANSPARENT,
            textColor = Color.parseColor("#333333"),
            titleSizeSp = 26f,
            bodySizeSp = 16f,
            paddingLeftDp = 13f,
            paddingTopDp = 12f,
            paddingRightDp = 13f,
            paddingBottomDp = 12f,
            titleBold = true
        ),
        mainStyle = SectionStyle(
            bgColor = Color.parseColor("#B3FFFFFF"),
            textColor = Color.parseColor("#333333"),
            titleSizeSp = 16f,
            bodySizeSp = 14f,
            paddingLeftDp = 18f,
            paddingTopDp = 14f,
            paddingRightDp = 18f,
            paddingBottomDp = 14f,
            titleBold = true,
            cornerRadiusDp = 12f,
            titleBgColor = Color.parseColor("#3CA0FF"),
            titleTextColor = Color.WHITE,
            titleCornerRadiusDp = 8f
        ),
        subStyle = SectionStyle(
            bgColor = Color.parseColor("#B3FFFFFF"),
            textColor = Color.parseColor("#333333"),
            titleSizeSp = 14f,
            bodySizeSp = 13f,
            paddingLeftDp = 18f,
            paddingTopDp = 12f,
            paddingRightDp = 18f,
            paddingBottomDp = 12f,
            titleBold = true,
            cornerRadiusDp = 10f
        )
    )

    val CARTOON = Template(
        name = "活泼黄",
        canvasBg = Color.parseColor("#FFE97F"),
        outerPaddingDp = 12f,
        sectionGapDp = 16f,
        heroStyle = SectionStyle(
            bgColor = Color.TRANSPARENT,
            textColor = Color.parseColor("#333333"),
            titleSizeSp = 30f,
            bodySizeSp = 17f,
            paddingLeftDp = 13f,
            paddingTopDp = 12f,
            paddingRightDp = 13f,
            paddingBottomDp = 12f,
            titleBold = true
        ),
        mainStyle = SectionStyle(
            bgColor = Color.WHITE,
            textColor = Color.parseColor("#000000"),
            titleSizeSp = 18f,
            bodySizeSp = 14f,
            paddingLeftDp = 16f,
            paddingTopDp = 16f,
            paddingRightDp = 16f,
            paddingBottomDp = 16f,
            titleBold = true,
            cornerRadiusDp = 8f,
            titleBgColor = Color.parseColor("#FFCF4D"),
            titleTextColor = Color.parseColor("#000000"),
            titleCornerRadiusDp = 9999f
        ),
        subStyle = SectionStyle(
            bgColor = Color.WHITE,
            textColor = Color.parseColor("#000000"),
            titleSizeSp = 16f,
            bodySizeSp = 13f,
            paddingLeftDp = 16f,
            paddingTopDp = 12f,
            paddingRightDp = 16f,
            paddingBottomDp = 12f,
            titleBold = true,
            cornerRadiusDp = 8f
        )
    )

    val ALL = listOf(SIMPLE, TECH, CARTOON)
}

package com.android.wallpaper.picker.preview.ui.view

import android.content.Context
import android.graphics.Point
import android.util.AttributeSet
import android.view.SurfaceView
import com.android.wallpaper.picker.preview.ui.util.CropSizeUtil.findMaxRectWithRatioIn
import com.android.wallpaper.util.WallpaperCropUtils

/**
 * A [SurfaceView] scales to largest possible on current display with the size preserving target
 * display aspect ratio.
 *
 * Acts as [SurfaceView] if the current and target display size were not set by the time of
 * [onMeasure].
 */
class FullPreviewSurfaceView(context: Context, attrs: AttributeSet? = null) :
    SurfaceView(context, attrs) {

    // Current display size represents the size of the currently used display. There is only one
    // size for handheld and tablet devices, but there are 2 sizes for foldable devices.
    private var currentDisplaySize: Point? = null

    // Target display size represents the size of the display that a wallpaper aims to be set to.
    private var targetDisplaySize: Point? = null
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        if (currentDisplaySize == null || targetDisplaySize == null) {
            setMeasuredDimension(widthMeasureSpec, heightMeasureSpec)
            return
        }

        targetDisplaySize?.findMaxRectWithRatioIn(checkNotNull(currentDisplaySize))?.let {
            val scale = WallpaperCropUtils.getSystemWallpaperMaximumScale(context)
            setMeasuredDimension((it.x * scale).toInt(), (it.y * scale).toInt())
        }
    }

    /**
     * Sets the target display size and the current display size.
     *
     * The view size is maxed out within current display size while preserving the aspect ratio of
     * the target display size. On a single display device the current display size is always the
     * target display size.
     *
     * @param currentSize current display size used as the max bound of this view.
     * @param targetSize target display size to get and preserve it's aspect ratio.
     */
    fun setCurrentAndTargetDisplaySize(currentSize: Point, targetSize: Point) {
        currentDisplaySize = currentSize
        targetDisplaySize = targetSize
    }
}
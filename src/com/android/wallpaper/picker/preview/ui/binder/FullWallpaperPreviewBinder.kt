/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.wallpaper.picker.preview.ui.binder

import android.content.Context
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.wallpaper.R
import com.android.wallpaper.dispatchers.MainDispatcher
import com.android.wallpaper.model.wallpaper.WallpaperModel
import com.android.wallpaper.module.WallpaperPersister
import com.android.wallpaper.picker.TouchForwardingLayout
import com.android.wallpaper.picker.preview.ui.util.FullResImageViewUtil.getCropRect
import com.android.wallpaper.picker.preview.ui.util.SurfaceViewUtil.attachView
import com.android.wallpaper.picker.preview.ui.view.FullPreviewSurfaceView
import com.android.wallpaper.picker.preview.ui.viewmodel.WallpaperPreviewViewModel
import com.android.wallpaper.util.wallpaperconnection.WallpaperConnectionUtils
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.OnStateChangedListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Binds wallpaper preview surface view and its view models. */
object FullWallpaperPreviewBinder {

    fun bind(
        applicationContext: Context,
        surfaceView: FullPreviewSurfaceView,
        surfaceTouchForwardingLayout: TouchForwardingLayout,
        viewModel: WallpaperPreviewViewModel,
        currentDisplaySize: Point,
        viewLifecycleOwner: LifecycleOwner,
        @MainDispatcher mainScope: CoroutineScope,
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
                val previewConfig = viewModel.selectedSmallPreviewConfig ?: return@repeatOnLifecycle
                surfaceView.setCurrentAndTargetDisplaySize(
                    currentSize = currentDisplaySize,
                    targetSize = previewConfig.displaySize,
                )
                surfaceView.setZOrderMediaOverlay(true)
                surfaceView.holder.addCallback(
                    object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            val wallpaper = viewModel.editingWallpaperModel ?: return
                            if (wallpaper is WallpaperModel.LiveWallpaperModel) {
                                viewLifecycleOwner.lifecycleScope.launch {
                                    WallpaperConnectionUtils.connect(
                                        applicationContext,
                                        mainScope,
                                        checkNotNull(viewModel.editingWallpaper).wallpaperComponent,
                                        // TODO b/301088528(giolin): Pass correspondent
                                        //                           destination for live
                                        //                           wallpaper preview
                                        WallpaperPersister.DEST_LOCK_SCREEN,
                                        surfaceView,
                                    )
                                }
                            } else if (wallpaper is WallpaperModel.StaticWallpaperModel) {
                                val preview =
                                    LayoutInflater.from(applicationContext)
                                        .inflate(R.layout.fullscreen_wallpaper_preview, null)
                                surfaceView.attachView(preview)
                                val fullResImageView =
                                    preview.requireViewById<SubsamplingScaleImageView>(
                                        R.id.full_res_image
                                    )
                                surfaceTouchForwardingLayout.initTouchForwarding(fullResImageView)

                                // Initially assign the current crop to view model and listen to
                                // any new crops created by user gesture scaling and translation.
                                viewModel.getStaticWallpaperPreviewViewModel().fullPreviewCrop =
                                    fullResImageView.getCropRect()
                                fullResImageView.setOnNewCropListener {
                                    viewModel.getStaticWallpaperPreviewViewModel().fullPreviewCrop =
                                        it
                                }

                                // Bind static wallpaper
                                StaticWallpaperPreviewBinder.bind(
                                    preview.requireViewById(R.id.low_res_image),
                                    fullResImageView,
                                    viewModel.getStaticWallpaperPreviewViewModel(),
                                    previewConfig.screenOrientation,
                                    viewLifecycleOwner,
                                )
                            }
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int
                        ) {}

                        override fun surfaceDestroyed(holder: SurfaceHolder) {}
                    }
                )
                // TODO (b/300979155): Clean up surface when no longer needed, e.g. onDestroyed
            }
        }
    }

    private fun TouchForwardingLayout.initTouchForwarding(targetView: View) {
        setForwardingEnabled(true)
        setTargetView(targetView)
    }

    private fun SubsamplingScaleImageView.setOnNewCropListener(onNewCrop: (crop: Rect) -> Unit) {
        setOnStateChangedListener(
            object : OnStateChangedListener {
                override fun onScaleChanged(p0: Float, p1: Int) {
                    onNewCrop.invoke(getCropRect())
                }

                override fun onCenterChanged(p0: PointF?, p1: Int) {
                    onNewCrop.invoke(getCropRect())
                }
            }
        )
    }
}
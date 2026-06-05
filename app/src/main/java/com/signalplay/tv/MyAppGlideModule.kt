package com.signalplay.tv

import android.content.Context
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.bumptech.glide.module.AppGlideModule

@GlideModule
class MyAppGlideModule : AppGlideModule() {
    override fun applyOptions(context: Context, builder: GlideBuilder) {
        // TRAVA 1: Máximo de 50 MB de imagens salvas no armazenamento da TV Box
        val diskCacheSizeBytes = 50 * 1024 * 1024L 
        builder.setDiskCache(InternalCacheDiskCacheFactory(context, "signalplay_image_cache", diskCacheSizeBytes))
        
        // TRAVA 2: Máximo de 15 MB de imagens na memória RAM simultaneamente
        val memoryCacheSizeBytes = 15 * 1024 * 1024L 
        builder.setMemoryCache(LruResourceCache(memoryCacheSizeBytes))
    }
}

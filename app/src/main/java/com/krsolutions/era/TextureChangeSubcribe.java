package com.krsolutions.era;



import android.graphics.SurfaceTexture;
import android.view.TextureView;

import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;

public class TextureChangeSubcribe implements ObservableOnSubscribe<AutoFitTextureView.SurfaceTextureListener> {

    private final AutoFitTextureView mTextureView;

    public TextureChangeSubcribe(AutoFitTextureView mTextureView) {
        this.mTextureView = mTextureView;
    }

    @Override
    public void subscribe(ObservableEmitter<AutoFitTextureView.SurfaceTextureListener> emitter) throws Exception {
        AutoFitTextureView.SurfaceTextureListener listener = new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        };
    }

}

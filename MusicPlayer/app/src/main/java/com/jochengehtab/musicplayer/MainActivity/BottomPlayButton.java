package com.jochengehtab.musicplayer.MainActivity;

import android.content.Context;
import android.util.AttributeSet;

public class BottomPlayButton extends androidx.appcompat.widget.AppCompatImageButton {

    private boolean isPlayIconShowing = false;

    public BottomPlayButton(Context context) {
        super(context);
    }

    public BottomPlayButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public BottomPlayButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void toggleIsPlayIconShowing() {
        isPlayIconShowing = !isPlayIconShowing;
    }

    public boolean isPlayIconShowing() {
        return isPlayIconShowing;
    }

    public void setPlayIconShowing(boolean playIconShowing) {
        isPlayIconShowing = playIconShowing;
    }
}

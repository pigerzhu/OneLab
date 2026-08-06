package io.github.pigerzhu.onelab.feature.window;

import io.github.pigerzhu.onelab.MainActivity;
import io.github.pigerzhu.onelab.R;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;


import androidx.core.util.Consumer;
import androidx.window.area.WindowAreaCapability;
import androidx.window.area.WindowAreaController;
import androidx.window.area.WindowAreaInfo;
import androidx.window.area.WindowAreaPresentationSessionCallback;
import androidx.window.area.WindowAreaSessionPresenter;
import androidx.window.java.area.WindowAreaControllerCallbackAdapter;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;

/**
 * Lights the cover (outer) display and draws custom content on it while the inner display keeps
 * running, exactly the way Samsung Camera does its cover-screen preview: through the public Jetpack
 * WindowManager {@code WindowAreaController} "rear display presentation" API. No root, no hooks.
 *
 * <p>The controller internally requests the concurrent device state (both panels on) and hands back
 * a {@link WindowAreaSessionPresenter} whose context targets the cover display; whatever view we set
 * on it shows there. We draw a ticking clock as the default demo content.
 */
public final class CoverDisplayPresenter {
    private static final String TAG = "OneLab";

    interface StatusListener {
        void onStatus(String text, boolean canPresent, boolean active);
    }

    static final int MODE_CLOCK = 0;
    static final int MODE_TEXT = 1;
    static final int MODE_IMAGE = 2;

    private final Activity activity;
    private final Executor executor;
    private final WindowAreaController controller;
    private final WindowAreaControllerCallbackAdapter adapter;
    private final Consumer<List<WindowAreaInfo>> infoListener = this::onWindowAreaInfos;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private WindowAreaInfo rearInfo;
    private WindowAreaSessionPresenter session;
    private TextView clockView;
    private StatusListener statusListener;
    private boolean listening;

    private int contentMode = MODE_CLOCK;
    private String customText = "";
    private Uri imageUri;

    public CoverDisplayPresenter(Activity activity) {
        this.activity = activity;
        this.executor = activity.getMainExecutor();
        this.controller = WindowAreaController.getOrCreate();
        this.adapter = new WindowAreaControllerCallbackAdapter(controller);
    }

    void setStatusListener(StatusListener listener) {
        this.statusListener = listener;
        notifyStatus();
    }

    int contentMode() {
        return contentMode;
    }

    void setContentMode(int mode) {
        contentMode = mode;
        applyContent();
    }

    void setCustomText(String text) {
        customText = text == null ? "" : text;
        if (contentMode == MODE_TEXT) {
            applyContent();
        }
    }

    void setImageUri(Uri uri) {
        imageUri = uri;
        if (contentMode == MODE_IMAGE) {
            applyContent();
        }
    }

    private void applyContent() {
        if (session != null) {
            session.setContentView(buildCoverContent(session.getContext()));
        }
    }

    void startListening() {
        if (listening) {
            return;
        }
        listening = true;
        adapter.addWindowAreaInfoListListener(executor, infoListener);
    }

    void stopListening() {
        if (!listening) {
            return;
        }
        listening = false;
        adapter.removeWindowAreaInfoListListener(infoListener);
    }

    boolean isActive() {
        return session != null;
    }

    void present() {
        if (session != null) {
            return;
        }
        if (rearInfo == null) {
            toastStatus(activity.getString(R.string.cover_presentation_unavailable));
            return;
        }
        try {
            controller.presentContentOnWindowArea(
                    rearInfo.getToken(),
                    activity,
                    executor,
                    presentationCallback
            );
        } catch (Throwable t) {
            Log.e(TAG, "presentContentOnWindowArea failed", t);
            toastStatus(activity.getString(R.string.cover_presentation_start_failed,
                    String.valueOf(t.getMessage())));
        }
    }

    void end() {
        WindowAreaSessionPresenter current = session;
        if (current != null) {
            current.close();
        }
    }

    private final WindowAreaPresentationSessionCallback presentationCallback =
            new WindowAreaPresentationSessionCallback() {
                @Override
                public void onSessionStarted(WindowAreaSessionPresenter presenter) {
                    session = presenter;
                    presenter.setContentView(buildCoverContent(presenter.getContext()));
                    notifyStatus();
                    Log.i(TAG, "Cover presentation session started");
                }

                @Override
                public void onContainerVisibilityChanged(boolean isVisible) {
                    // Nothing to do; the clock keeps ticking while the session is alive.
                }

                @Override
                public void onSessionEnded(Throwable t) {
                    session = null;
                    clockView = null;
                    stopClock();
                    notifyStatus();
                    if (t != null) {
                        Log.e(TAG, "Cover presentation session ended with error", t);
                    } else {
                        Log.i(TAG, "Cover presentation session ended");
                    }
                }
            };

    private void onWindowAreaInfos(List<WindowAreaInfo> infos) {
        WindowAreaInfo rear = null;
        for (WindowAreaInfo info : infos) {
            if (info.getType() == WindowAreaInfo.Type.TYPE_REAR_FACING) {
                rear = info;
            }
        }
        rearInfo = rear;
        notifyStatus();
    }

    private WindowAreaCapability.Status presentStatus() {
        if (rearInfo == null) {
            return WindowAreaCapability.Status.WINDOW_AREA_STATUS_UNSUPPORTED;
        }
        return rearInfo
                .getCapability(WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA)
                .getStatus();
    }

    private void notifyStatus() {
        if (statusListener == null) {
            return;
        }
        if (session != null) {
            statusListener.onStatus("", false, true);
            return;
        }
        WindowAreaCapability.Status status = presentStatus();
        boolean available = status == WindowAreaCapability.Status.WINDOW_AREA_STATUS_AVAILABLE;
        String text;
        if (available) {
            text = "";
        } else if (status == WindowAreaCapability.Status.WINDOW_AREA_STATUS_UNSUPPORTED) {
            text = activity.getString(R.string.cover_presentation_unsupported);
        } else {
            text = activity.getString(R.string.cover_presentation_status,
                    String.valueOf(status));
        }
        statusListener.onStatus(text, available, false);
    }

    private void toastStatus(String message) {
        if (statusListener != null) {
            statusListener.onStatus(message, false, session != null);
        }
    }

    private View buildCoverContent(Context context) {
        stopClock();
        clockView = null;

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Color.BLACK);

        if (contentMode == MODE_IMAGE && imageUri != null) {
            ImageView image = new ImageView(context);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            try {
                image.setImageURI(imageUri);
            } catch (Throwable t) {
                Log.e(TAG, "load cover image failed", t);
            }
            root.addView(image, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else if (contentMode == MODE_TEXT) {
            TextView textView = new TextView(context);
            textView.setTextColor(Color.WHITE);
            textView.setTextSize(40);
            textView.setGravity(Gravity.CENTER);
            int pad = Math.round(context.getResources().getDisplayMetrics().density * 24);
            textView.setPadding(pad, 0, pad, 0);
            textView.setText(customText);
            root.addView(textView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER));
        } else {
            clockView = new TextView(context);
            clockView.setTextColor(Color.WHITE);
            clockView.setTextSize(64);
            clockView.setGravity(Gravity.CENTER);
            root.addView(clockView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER));
            updateClock();
            startClock();
        }
        return root;
    }

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            handler.postDelayed(this, 1000L);
        }
    };

    private void startClock() {
        handler.removeCallbacks(clockTick);
        handler.post(clockTick);
    }

    private void stopClock() {
        handler.removeCallbacks(clockTick);
    }

    private void updateClock() {
        if (clockView == null) {
            return;
        }
        String now = new SimpleDateFormat("HH:mm:ss\nyyyy-MM-dd EEE", Locale.getDefault())
                .format(new Date());
        clockView.setText(now);
    }
}

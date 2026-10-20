package io.github.pigerzhu.onelab.feature.support;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.pigerzhu.onelab.MainActivity;
import io.github.pigerzhu.onelab.R;
import io.github.pigerzhu.onelab.system.DonationImageSaver;
import io.github.pigerzhu.onelab.ui.Ui;

/** Voluntary project support entry and WeChat donation page. */
public final class DonationScreen {
    private static final int MAXIMUM_PREVIEW_BYTES = 1_000_000;

    private final MainActivity host;
    private final Ui ui;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile Bitmap previewBitmap;
    private volatile WeakReference<ImageView> waitingPreview = new WeakReference<>(null);

    public DonationScreen(MainActivity host, Ui ui) {
        this.host = host;
        this.ui = ui;
        executor.execute(this::loadPreview);
    }

    public View entryCard(Runnable openPage) {
        MaterialCardView card = ui.card();
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(view -> openPage.run());

        LinearLayout body = ui.cardBody();
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(body);

        LinearLayout copy = new LinearLayout(host);
        copy.setOrientation(LinearLayout.VERTICAL);
        body.addView(copy, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        copy.addView(ui.text(host.getString(R.string.donation_entry_title),
                20, true, ui.colorOnSurface));
        copy.addView(ui.text(host.getString(R.string.donation_entry_summary),
                14, false, ui.colorOnSurfaceVariant));

        TextView arrow = ui.text(">", 28, false, ui.colorOnSurfaceVariant);
        arrow.setGravity(Gravity.CENTER);
        arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        body.addView(arrow, new LinearLayout.LayoutParams(ui.dp(32), ui.dp(48)));
        return card;
    }

    public View content() {
        MaterialCardView card = ui.card();
        LinearLayout body = ui.cardBody();
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        card.addView(body);

        body.addView(ui.text(host.getString(R.string.donation_thanks_title),
                22, true, ui.colorOnSurface));
        ui.addSpace(body, 8);
        TextView introduction = ui.text(host.getString(R.string.donation_introduction),
                15, false, ui.colorOnSurfaceVariant);
        introduction.setGravity(Gravity.CENTER);
        body.addView(introduction, ui.matchWrap());
        ui.addSpace(body, 18);

        ImageView qrImage = new ImageView(host);
        Bitmap readyPreview = previewBitmap;
        if (readyPreview != null) {
            qrImage.setImageBitmap(readyPreview);
        } else {
            waitingPreview = new WeakReference<>(qrImage);
        }
        qrImage.setAdjustViewBounds(true);
        qrImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        qrImage.setContentDescription(host.getString(R.string.donation_qr_description));
        body.addView(qrImage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        qrImage.setMaxWidth(ui.dp(420));

        ui.addSpace(body, 14);
        TextView scanHint = ui.text(host.getString(R.string.donation_scan_hint),
                14, false, ui.colorOnSurfaceVariant);
        scanHint.setGravity(Gravity.CENTER);
        body.addView(scanHint, ui.matchWrap());
        ui.addSpace(body, 12);

        MaterialButton saveButton = ui.actionButton(
                host.getString(R.string.donation_save_action));
        body.addView(saveButton, ui.matchWrap());
        saveButton.setOnClickListener(view -> saveImage(saveButton));

        ui.addSpace(body, 18);
        TextView disclaimer = ui.text(host.getString(R.string.donation_disclaimer),
                13, false, ui.colorOnSurfaceVariant);
        disclaimer.setGravity(Gravity.CENTER);
        body.addView(disclaimer, ui.matchWrap());
        return card;
    }

    public void onDestroy() {
        executor.shutdownNow();
    }

    private void saveImage(MaterialButton button) {
        button.setEnabled(false);
        android.content.Context applicationContext = host.getApplicationContext();
        executor.execute(() -> {
            try {
                DonationImageSaver.save(applicationContext);
                host.runOnUiThread(() -> {
                    if (!canUpdateUi()) return;
                    button.setEnabled(true);
                    Toast.makeText(host,
                            host.getString(R.string.donation_saved,
                                    DonationImageSaver.DISPLAY_PATH),
                            Toast.LENGTH_LONG).show();
                });
            } catch (Exception ignored) {
                host.runOnUiThread(() -> {
                    if (!canUpdateUi()) return;
                    button.setEnabled(true);
                    Toast.makeText(host, R.string.donation_save_failed,
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void loadPreview() {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(host.getResources(), R.raw.wechat_donation, bounds);

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        options.inSampleSize = DonationPreviewSampling.inSampleSizeForRgb565(
                bounds.outWidth, bounds.outHeight, MAXIMUM_PREVIEW_BYTES);
        Bitmap decoded = BitmapFactory.decodeResource(
                host.getResources(), R.raw.wechat_donation, options);
        if (decoded == null) return;
        previewBitmap = decoded;

        ImageView waiting = waitingPreview.get();
        if (waiting == null) return;
        host.runOnUiThread(() -> {
            if (canUpdateUi()) {
                waiting.setImageBitmap(decoded);
            }
        });
    }

    private boolean canUpdateUi() {
        return !host.isFinishing() && !host.isDestroyed();
    }
}

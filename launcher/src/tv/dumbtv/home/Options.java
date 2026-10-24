package tv.dumbtv.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.graphics.drawable.StateListDrawable;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Every setting is a row: left/right or OK cycles it, and the change is saved at once. */
public final class Options extends Activity {
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private final List<Runnable> refreshers = new ArrayList<>();
    private Config cfg;
    private static final int PICK_WALLPAPER = 100;
    private boolean importing;
    private AlertDialog progress;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        cfg = Config.get(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setFocusable(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackgroundColor(0xFF0C0C0E);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(56), dp(40), dp(56), dp(40));
        scroll.addView(list);

        TextView title = Ui.text(this, 30, Ui.TEXT, Ui.LIGHT);
        title.setText("Options");
        title.setPadding(dp(16), 0, 0, dp(20));
        list.addView(title);

        for (Config.Choice c : Config.CHOICES) list.addView(choice(c));
        list.addView(action("Import wallpaper", this::importWallpaper));
        list.addView(action("Next wallpaper", () -> {
            cfg.nextWallpaper();
            refresh();
        }));
        list.addView(gap());
        list.addView(action("Apps on home screen", () -> pick(Catalog.APPS)));
        list.addView(action("Inputs on home screen", () -> pick(Catalog.INPUTS)));
        list.addView(action("Reset order, names and hidden tiles", this::reset));
        list.addView(action("Open Amazon's home screen", () -> {
            Guard.amazon(this);
            finish();
        }));

        TextView about = Ui.text(this, 13, Ui.FAINT, Ui.MEDIUM);
        about.setLineSpacing(0, 1.4f);
        about.setPadding(dp(16), dp(28), 0, 0);
        about.setText("Settings file: " + cfg.dir + "/" + Config.FILE
                + "\nImport wallpaper: choose an image file or copy one into the shared transfer folder"
                + "\nOne wallpaper: name it exactly wallpaper.jpg (or .png) and put it in that folder"
                + "\nSeveral wallpapers: put them in " + Config.WALLPAPERS + "/ with any names, and pick a rotation"
                + "\nThen pick Background › Image file or Adaptive wallpaper tint. No permission is needed"
                + "\nAdaptive wallpaper tint shows graphite until you add a wallpaper"
                + "\nNo internet permission. No tracking, no ads, no accounts.");
        list.addView(about);

        setContentView(scroll);
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cfg.refresh();
        refresh();
    }

    private void refresh() {
        for (Runnable r : refreshers) r.run();
    }

    private View choice(Config.Choice c) {
        LinearLayout row = row();
        TextView name = label(c.title);
        TextView value = Ui.text(this, 16, Ui.MUTED, Ui.MEDIUM);
        value.setGravity(Gravity.END);
        row.addView(name, new LinearLayout.LayoutParams(0, WRAP, 1f));
        row.addView(value, new LinearLayout.LayoutParams(WRAP, WRAP));
        row.setOnClickListener(v -> step(c, 1));
        row.setOnKeyListener((v, code, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) return step(c, -1);
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) return step(c, 1);
            return false;
        });
        Runnable show = () -> {
            boolean focused = row.isFocused();
            String text = c.name(cfg.get(c));
            if (c == Config.ACCENT) {
                SpannableString s = new SpannableString("●  " + text);
                s.setSpan(new ForegroundColorSpan(cfg.accent()), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                value.setText(focused ? "‹  " : "");
                value.append(s);
                if (focused) value.append("  ›");
            } else {
                value.setText(focused ? "‹  " + text + "  ›" : text);
            }
            value.setTextColor(focused ? Ui.TEXT : Ui.MUTED);
            boolean off = (c == Config.DIM || c == Config.ROTATE)
                    && !"image".equals(cfg.get(Config.BACKGROUND))
                    && !"adaptive-tint".equals(cfg.get(Config.BACKGROUND))
                    || c == Config.SWITCH && "off".equals(cfg.get(Config.INPUTS));
            row.setAlpha(off ? 0.4f : 1f);
        };
        row.setOnFocusChangeListener((v, has) -> show.run());
        refreshers.add(show);
        refreshers.add(() -> style(row));
        return row;
    }

    private boolean step(Config.Choice c, int by) {
        int i = Arrays.asList(c.values).indexOf(cfg.get(c));
        int n = c.values.length;
        cfg.set(c.key, c.values[((i + by) % n + n) % n]);
        refresh();
        return true;
    }

    private View action(String text, Runnable run) {
        LinearLayout row = row();
        row.addView(label(text), new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView chevron = Ui.text(this, 16, Ui.MUTED, Ui.MEDIUM);
        chevron.setText("›");
        row.addView(chevron);
        row.setOnClickListener(v -> run.run());
        refreshers.add(() -> style(row));
        return row;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(600), WRAP);
        lp.bottomMargin = dp(4);
        row.setLayoutParams(lp);
        return row;
    }

    private void style(View row) {
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] {android.R.attr.state_focused},
                Ui.round((cfg.accent() & 0x00FFFFFF) | 0x33000000, dp(10)));
        bg.addState(new int[0], Ui.round(0x00000000, dp(10)));
        row.setBackground(bg);
    }

    private TextView label(String text) {
        TextView v = Ui.text(this, 16, Ui.TEXT, Ui.MEDIUM);
        v.setText(text);
        return v;
    }

    private View gap() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(18)));
        return v;
    }

    private void pick(String kind) {
        List<Item> items = Catalog.APPS.equals(kind)
                ? Catalog.apps(this)
                : Catalog.inputs(this, (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE));
        CharSequence[] names = new CharSequence[items.size()];
        boolean[] on = new boolean[items.size()];
        for (int i = 0; i < items.size(); i++) {
            names[i] = Catalog.label(cfg, items.get(i));
            on[i] = Catalog.shown(cfg, kind, items.get(i));
        }
        new AlertDialog.Builder(this, Ui.DIALOG)
                .setTitle(Catalog.APPS.equals(kind) ? "Apps on home screen" : "Inputs on home screen")
                .setMultiChoiceItems(names, on, (d, which, checked) ->
                        Catalog.setShown(cfg, kind, items.get(which), checked))
                .setPositiveButton("Done", null)
                .show();
    }

    private void reset() {
        new AlertDialog.Builder(this, Ui.DIALOG)
                .setTitle("Reset the home screen layout?")
                .setMessage("Order, names and hidden tiles go back to default. Colors and sizes stay.")
                .setPositiveButton("Reset", (d, w) -> cfg.removePrefix("name.", "apps.", "inputs."))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void importWallpaper() {
        if (importing) return;
        new AlertDialog.Builder(this, Ui.DIALOG)
                .setTitle("Import wallpaper")
                .setItems(new String[] {"Choose image file", "From shared transfer folder"}, (d, which) -> {
                    if (which == 0) chooseImage();
                    else sharedImages();
                }).setNegativeButton("Cancel", null).show();
    }

    private void chooseImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"image/jpeg", "image/png", "image/webp"});
        try {
            startActivityForResult(intent, PICK_WALLPAPER);
        } catch (ActivityNotFoundException e) {
            new AlertDialog.Builder(this, Ui.DIALOG).setTitle("No file picker on this TV")
                    .setMessage("Use Import wallpaper › From shared transfer folder instead. "
                            + "It works without a file manager or storage permissions.")
                    .setPositiveButton("Transfer folder", (d, w) -> sharedImages())
                    .setNegativeButton("Cancel", null).show();
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_WALLPAPER || result != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri != null) importImage(() -> getContentResolver().openInputStream(uri));
    }

    private void sharedImages() {
        File[] media = getExternalMediaDirs();
        File directory = null;
        for (File folder : media) {
            if (folder != null) { directory = new File(folder, "wallpapers"); break; }
        }
        if (directory == null || (!directory.isDirectory() && !directory.mkdirs())) {
            message("Shared storage is unavailable. Try again after connecting storage.");
            return;
        }
        File[] files = directory.listFiles(f -> {
            String n = f.getName().toLowerCase(java.util.Locale.ROOT);
            return f.isFile() && (n.endsWith(".jpg") || n.endsWith(".jpeg")
                    || n.endsWith(".png") || n.endsWith(".webp"));
        });
        if (files == null || files.length == 0) {
            new AlertDialog.Builder(this, Ui.DIALOG).setTitle("Shared transfer folder")
                    .setMessage("Copy JPG, PNG or WebP images here from your computer, then return:\n\n"
                            + directory + "\n\nUse a landscape image, ideally 3840 × 2160 or 1920 × 1080. "
                            + "Up to 20 MB per image. Your original files are kept.")
                    .setPositiveButton("Done", null).show();
            return;
        }
        Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        String[] names = new String[files.length];
        for (int i = 0; i < files.length; i++) names[i] = files[i].getName();
        new AlertDialog.Builder(this, Ui.DIALOG).setTitle("Choose a wallpaper")
                .setItems(names, (d, which) -> importImage(() -> new FileInputStream(files[which])))
                .setNegativeButton("Cancel", null).show();
    }

    private interface ImageSource { InputStream open() throws IOException; }

    private void importImage(ImageSource source) {
        if (importing) return;
        importing = true;
        progress = new AlertDialog.Builder(this, Ui.DIALOG).setMessage("Importing wallpaper…")
                .setCancelable(false).create();
        progress.show();
        File destination = new File(cfg.dir, Config.WALLPAPERS);
        new Thread(() -> {
            try (InputStream in = source.open()) {
                if (in == null) throw new IOException("The selected file is unavailable");
                File image = WallpaperImport.save(in, destination);
                runOnUiThread(() -> importFinished(image, null));
            } catch (IOException | SecurityException e) {
                runOnUiThread(() -> importFinished(null, e.getMessage()));
            }
        }, "wallpaper-import").start();
    }

    private void importFinished(File image, String error) {
        importing = false;
        if (isFinishing() || isDestroyed()) return;
        if (progress != null) { progress.dismiss(); progress = null; }
        if (image == null) { message(error == null ? "Could not import that image" : error); return; }
        new AlertDialog.Builder(this, Ui.DIALOG).setTitle("Wallpaper added")
                .setMessage("Use it with adaptive wallpaper tint? This selects the new image "
                        + "and turns rotation off. You can enable rotation again in Options.")
                .setPositiveButton("Use with tint", (d, w) -> {
                    cfg.set(Config.ROTATE.key, "off");
                    cfg.set("wallpaper.offset", String.valueOf(cfg.wallpapers().indexOf(image)));
                    cfg.set(Config.BACKGROUND.key, "adaptive-tint");
                    refresh();
                }).setNegativeButton("Keep current background", null).show();
    }

    private void message(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        if (progress != null) { progress.dismiss(); progress = null; }
        super.onDestroy();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}

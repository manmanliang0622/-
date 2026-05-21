package catcatch;

import java.io.*;
import java.util.ArrayDeque;
import java.util.Deque;
import javafx.scene.image.*;

/**
 * Crops animal images to head area, removes background via BFS flood-fill,
 * then applies a hard circular mask so the result is always a clean circle
 * regardless of whether the flood-fill succeeded.
 */
final class ImageProcessor {

    private ImageProcessor() {}

    /**
     * Load an image from a file path as a synchronous InputStream read,
     * then crop to the head area and remove the background.
     */
    static WritableImage loadAndProcess(File file) {
        if (file == null || !file.exists()) return null;
        try (InputStream is = new FileInputStream(file)) {
            Image src = new Image(is);   // InputStream constructor → synchronous
            return processHead(src);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Process an already-loaded Image.
     */
    static WritableImage processHead(Image src) {
        if (src == null) return null;
        int W = (int) src.getWidth();
        int H = (int) src.getHeight();
        if (W < 4 || H < 4) return null;

        PixelReader pr = src.getPixelReader();

        // ── 1. Head-crop: square from top-centre ─────────────────────────────
        // Portrait subjects (dogs): top 46%; near-square (cats): top 62%
        float frac = (H > W * 1.25f) ? 0.46f : 0.62f;
        int cropH = (int)(H * frac);
        int cropW = Math.min(W, cropH);
        int offX  = (W - cropW) / 2;

        // ── 2. Read into flat int[] ────────────────────────────────────────────
        int[] px = new int[cropW * cropH];
        for (int y = 0; y < cropH; y++)
            for (int x = 0; x < cropW; x++)
                px[y * cropW + x] = pr.getArgb(offX + x, y);

        // ── 3. Estimate background colour (all four border strips) ────────────
        int margin = Math.max(4, cropW / 25);
        long sr = 0, sg = 0, sb = 0;
        int cnt = 0;
        for (int x = 0; x < cropW; x++) {
            for (int m = 0; m < margin; m++) {
                int a = px[m * cropW + x];
                sr += r(a); sg += g(a); sb += b(a); cnt++;
                int c = px[(cropH - 1 - m) * cropW + x];
                sr += r(c); sg += g(c); sb += b(c); cnt++;
            }
        }
        for (int y = margin; y < cropH - margin; y++) {
            for (int m = 0; m < margin; m++) {
                int a = px[y * cropW + m];
                sr += r(a); sg += g(a); sb += b(a); cnt++;
                int c = px[y * cropW + (cropW - 1 - m)];
                sr += r(c); sg += g(c); sb += b(c); cnt++;
            }
        }
        int bgR = cnt > 0 ? (int)(sr / cnt) : 220;
        int bgG = cnt > 0 ? (int)(sg / cnt) : 220;
        int bgB = cnt > 0 ? (int)(sb / cnt) : 220;

        // ── 4. BFS flood-fill from all border pixels ──────────────────────────
        boolean[] isBg = new boolean[cropW * cropH];
        Deque<Integer> queue = new ArrayDeque<>();
        int threshold = 82;   // higher → more aggressive removal

        for (int x = 0; x < cropW; x++) {
            tryAdd(px, isBg, queue, x, 0,        cropW, cropH, bgR, bgG, bgB, threshold);
            tryAdd(px, isBg, queue, x, cropH - 1, cropW, cropH, bgR, bgG, bgB, threshold);
        }
        for (int y = 1; y < cropH - 1; y++) {
            tryAdd(px, isBg, queue, 0,       y, cropW, cropH, bgR, bgG, bgB, threshold);
            tryAdd(px, isBg, queue, cropW-1, y, cropW, cropH, bgR, bgG, bgB, threshold);
        }
        while (!queue.isEmpty()) {
            int idx = queue.poll();
            int nx = idx % cropW, ny = idx / cropW;
            if (nx > 0)        tryAdd(px, isBg, queue, nx-1, ny,   cropW, cropH, bgR, bgG, bgB, threshold);
            if (nx < cropW-1)  tryAdd(px, isBg, queue, nx+1, ny,   cropW, cropH, bgR, bgG, bgB, threshold);
            if (ny > 0)        tryAdd(px, isBg, queue, nx,   ny-1, cropW, cropH, bgR, bgG, bgB, threshold);
            if (ny < cropH-1)  tryAdd(px, isBg, queue, nx,   ny+1, cropW, cropH, bgR, bgG, bgB, threshold);
        }

        // ── 5. Write result with hard circular mask ───────────────────────────
        // The circular mask guarantees a clean circle even when flood-fill fails.
        double cx = cropW / 2.0, cy = cropH / 2.0;
        double maxR = Math.min(cropW, cropH) / 2.0 - 1;

        WritableImage out = new WritableImage(cropW, cropH);
        PixelWriter pw = out.getPixelWriter();

        for (int y = 0; y < cropH; y++) {
            for (int x = 0; x < cropW; x++) {
                int idx = y * cropW + x;
                double dist = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy));
                if (isBg[idx] || dist > maxR) {
                    pw.setArgb(x, y, 0);  // transparent
                } else {
                    pw.setArgb(x, y, px[idx] | 0xFF000000);
                }
            }
        }
        return out;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void tryAdd(int[] px, boolean[] isBg, Deque<Integer> q,
                                int x, int y, int W, int H,
                                int bgR, int bgG, int bgB, int thr) {
        if (x < 0 || y < 0 || x >= W || y >= H) return;
        int idx = y * W + x;
        if (isBg[idx]) return;
        int v = px[idx];
        double d = Math.sqrt(sq(r(v) - bgR) + sq(g(v) - bgG) + sq(b(v) - bgB));
        if (d < thr) { isBg[idx] = true; q.add(idx); }
    }

    private static int    r(int argb)  { return (argb >> 16) & 0xFF; }
    private static int    g(int argb)  { return (argb >>  8) & 0xFF; }
    private static int    b(int argb)  { return  argb        & 0xFF; }
    private static double sq(double v) { return v * v; }
}

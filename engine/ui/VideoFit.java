package local.materialui;

/** Fit the complete decoded image, including non-square pixels, inside its bounds. */
public final class VideoFit {
    private VideoFit() {}
    public static int[] measure(int widthSpec, int heightSpec, int width, int height,
            int sarNum, int sarDen, int rotation) {
        int maxWidth = widthSpec & 0x3fffffff, maxHeight = heightSpec & 0x3fffffff;
        if ((widthSpec & 0xc0000000) == 0) maxWidth = Math.max(1, width);
        if ((heightSpec & 0xc0000000) == 0) maxHeight = Math.max(1, height);
        boolean sideways = Math.abs(rotation % 180) == 90;
        if (sideways) { int swap = maxWidth; maxWidth = maxHeight; maxHeight = swap; }
        double aspect = width > 0 && height > 0 ? (double) width / height : 16.0 / 9;
        if (sarNum > 0 && sarDen > 0) aspect *= (double) sarNum / sarDen;
        int fittedWidth = Math.max(1, Math.min(maxWidth, (int) Math.floor(maxHeight * aspect)));
        int fittedHeight = Math.max(1, Math.min(maxHeight, (int) Math.floor(fittedWidth / aspect)));
        return new int[]{fittedWidth, fittedHeight};
    }
}

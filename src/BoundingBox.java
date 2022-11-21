public class BoundingBox {
    int startX;
    int startY;
    int size;
    int[] avg;

    private static final int colorRange = 30;

    public BoundingBox(int startX, int startY, int size, int[] avg) {
        this.startX = startX;
        this.startY = startY;
        this.size = size;
        this.avg = avg;
    }

    // returns true if box does not overlap with *too many* background pixels
    public static boolean isBoxClean(int[] imgData, int imgWidth, int startX, int startY, int boxSize) {
        int startIndex = 3 * (startX + (imgWidth * startY));
        int amountOfForbiddenPixels = 0;
        int maxAmountOfForbiddenPixels = boxSize / 4;
        for (int y = startIndex; y < startIndex + 3 * imgWidth * boxSize; y += 3 * imgWidth) {
            for (int x = y; x < y + boxSize * 3; x += 3) {
                if ((imgData[x] - imgData[x + 1]) > 50 // red - green (should be far apart)
                        && (imgData[x] - imgData[x + 2]) < 110 && (imgData[x] - imgData[x + 2]) > 0 // red - blue
                                                                                                    // (should be close
                                                                                                    // together)
                        && (imgData[x + 2] - imgData[x + 1]) > 19) { // blue - green (should be far apart)
                    amountOfForbiddenPixels++;
                    if (amountOfForbiddenPixels > maxAmountOfForbiddenPixels) {
                        return false;
                    }
                }
                // if ( (imgData[x] > Math.max(0, color[0] - colorRange) && imgData[x] <
                // Math.min(255, color[0] + colorRange))
                // && (imgData[x+1] > Math.max(0, color[1] - colorRange*3) && imgData[x+1] <
                // Math.min(255, color[1] + colorRange*3))
                // && (imgData[x+2] > Math.max(0, color[2] - colorRange) && imgData[x+2] <
                // Math.min(255, color[2] + colorRange)))
                // {
                //
                // }
            }
        }
        return true;
    }

    // returns [avgR, avgG, avgB]
    public static int[] calcAvgRGB(int[] imgData, int imgWidth, int startX, int startY, int boxSize) {
        double r = 0;
        double g = 0;
        double b = 0;
        // adding imgWidth to index effectively increases y-coordinate by 1 and leaves x
        // the same
        int startIndex = 3 * (startX + (imgWidth * startY));
        for (int y = startIndex; y < startIndex + 3 * imgWidth * boxSize; y += 3 * imgWidth) {
            for (int x = y; x < y + boxSize * 3; x += 3) {
                r += imgData[x];
                g += imgData[x + 1];
                b += imgData[x + 2];
            }
        }
        // divide by amount of pixels in the box
        return new int[] {
                (int) Math.floor(r / (boxSize * boxSize)),
                (int) Math.floor(g / (boxSize * boxSize)),
                (int) Math.floor(b / (boxSize * boxSize))
        };
    }

    public static BoundingBox getBoundingBox(int[] imgData, int imgWidth, int startX, int startY, int size) {
        if (isBoxClean(imgData, imgWidth, startX, startY, size)) {
            return new BoundingBox(startX, startY, size, calcAvgRGB(imgData, imgWidth, startX, startY, size));
        }
        return null;
    }

    public String toString() {
        return String.format("x: %d, y: %d, size: %d, avg: [%d, %d, %d]", startX, startY, size, avg[0], avg[1], avg[2]);
    }
}

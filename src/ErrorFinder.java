public class ErrorFinder implements Runnable {
    int[] data;
    int width;
    BoundingBox[] boxes;
    int boxSize;
    long[][] results; // to put our result in
    int index; // to know in which index to place the result
    int startX;
    int endX;
    int startY;
    int endY;

    public ErrorFinder(int[] data, int width, BoundingBox[] boxes, int boxSize, long[][] results, int index, int startX,
            int endX, int startY, int endY) {
        this.data = data;
        this.width = width;
        this.boxes = boxes;
        this.boxSize = boxSize;
        this.results = results;
        this.index = index;
        this.startX = startX;
        this.endX = endX;
        this.startY = startY;
        this.endY = endY;
    }

    @Override
    public void run() {
        long minError = Long.MAX_VALUE;
        long minErrorX = 0;
        long minErrorY = 0;
        for (int y = startY; y < endY; y += 5) {
            for (int x = startX; x < endX; x += 5) {
                long totalError = 0;
                for (BoundingBox box : boxes) {
                    long boxError = 0;
                    int[] avg = BoundingBox.calcAvgRGB(data, width, x + box.startX, y + box.startY, boxSize);
                    for (int i = 0; i < avg.length; i++) {
                        boxError += Math.abs(avg[i] - box.avg[i]);
                    }
                    // System.out.println(boxError);
                    boxError = (long) Math.pow(boxError, 2);
                    totalError += boxError;
                    // System.out.println(boxError);
                }
                if (totalError < minError) {
                    minError = totalError;
                    minErrorX = x;
                    minErrorY = y;
                }
            }
        }
        System.out.println(String.format("result %d: %d, (%d, %d)", index, minError, minErrorX, minErrorY));
        results[index] = new long[] { minError, minErrorX, minErrorY };
    }
}

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;

public class Main {
    private static final double backgroundRealWidth = 4.9; // in cm
    private static final double ratioMap = 5639d / 48d; // px / cm
    private static int[] map;
    private static BufferedImage mapImage;
    private static int mapWidth;
    private static int mapHeight;
    private static final int[] scannedBackgroundColor = new int[] { 138, 78, 117 };
    private static final int[] SPYDERCUBE = new int[] { 110, 110, 110 };

    private static final boolean DEBUG = false;

    public static void main(String[] args) throws IOException, InterruptedException {
        // map = ImageIO.read(new File("images/map/map.jpg"));
        // BufferedImage croppedImage = ImageIO.read(new
        // File("images/received/croppedWorkingExample.jpg"));
        // correctColour(piece, SPYDERCUBE);
        // BufferedImage croppedImage = cropImage(piece);
        // BoundingBox[] boxes = getBoundingBoxes(croppedImage);
        // findMinimumErrorCoordinates(boxes);

        int maxAmountOfClients = 6;
        boolean[] clients = new boolean[maxAmountOfClients];

        mapImage = ImageIO.read(new File("images/map/left-half-combined_SLQ.jpg"));
        mapWidth = mapImage.getWidth();
        mapHeight = mapImage.getHeight();
        map = new int[3 * mapWidth * mapHeight];
        mapImage.getData().getPixels(0, 0, mapImage.getWidth(), mapImage.getHeight(), map);

        ServerSocket serverSocket = new ServerSocket(9487);
        System.out.println("Running");
        while (true) {
            Socket clientSocket = serverSocket.accept();
            System.out.println("Connected");
            boolean isClientWelcome = false;
            for (int i = 0; i < maxAmountOfClients; i++) {
                if (!clients[i]) {
                    clients[i] = true;
                    isClientWelcome = true;
                    new Thread(new ProcessingThread(clientSocket, map, i, clients)).start();
                    break;
                }
            }
            if (!isClientWelcome) {
                clientSocket.close();
            }
        }
    }

    // crop image to background of specified color.
    public static BufferedImage cropImage(BufferedImage piece) throws IOException {
        int width = piece.getWidth();
        int height = piece.getHeight();
        System.out.println(width);
        int[] data = new int[3 * width * height];
        piece.getData().getPixels(0, 0, width, height, data);

        int[] rangeX = new int[] { width - 1, 0 }; // [min, max]
        int[] rangeY = new int[] { height - 1, 0 };
        int boxSize = 20;
        // from top to bottom, from left to right, search for unclean boxes
        for (int y = 0; y < height - boxSize * 3; y += 3) {
            for (int x = 0; x < width - boxSize * 3; x += 3) {
                if (!BoundingBox.isBoxClean(data, width, x, y, boxSize)) {
                    // For left-most boundary, we need to add the amount of pixels in the box,
                    // because the box's origin is top-left, and would leave a gap of boxSize
                    // pixels.
                    rangeX[0] = Math.min(rangeX[0], x + boxSize);
                    rangeX[1] = Math.max(rangeX[1], x);
                    // Same for top boundary
                    rangeY[0] = Math.min(rangeY[0], y + boxSize);
                    rangeY[1] = Math.max(rangeY[1], y);
                }
            }
        }
        int dx = rangeX[1] - rangeX[0];
        int dy = rangeY[1] - rangeY[0];
        BufferedImage cropped = new BufferedImage(dx, dy, BufferedImage.TYPE_3BYTE_BGR);
        WritableRaster raster = cropped.getRaster();
        int[] temp = new int[3 * dx * dy];
        piece.getData().getPixels(rangeX[0], rangeY[0], dx, dy, temp);
        raster.setPixels(0, 0, dx, dy, temp);
        ImageIO.write(cropped, "jpg", new File("images/received/cropped.jpg"));
        return cropped;
    }

    public static BoundingBox[] getBoundingBoxes(BufferedImage croppedPieceImage) throws IOException {
        int width = croppedPieceImage.getWidth();
        int height = croppedPieceImage.getHeight();
        int[] data = new int[3 * width * height];
        croppedPieceImage.getData().getPixels(0, 0, width, height, data);

        // generate boxes
        int boundingBoxSize = (int) Math.floor((double) width / 20d); // in pixels
        System.out.println("boxSize: " + boundingBoxSize);
        ArrayList<BoundingBox> boxes = new ArrayList<>();
        for (int y = 0; y < height - boundingBoxSize; y += boundingBoxSize / 2) {
            for (int x = 0; x < width - boundingBoxSize; x += boundingBoxSize / 2) {
                BoundingBox box = BoundingBox.getBoundingBox(data, width, x, y, boundingBoxSize);
                if (box != null) {
                    boxes.add(box);
                }
            }
        }

        System.out.println("amount of boxes: " + boxes.size());
        if (DEBUG) {
            visualizeBoundingBoxes(boxes.toArray(new BoundingBox[0]),
                    croppedPieceImage, "boxesPiece", 0, 0);
        }
        double factor = (ratioMap / (width / backgroundRealWidth)) / 1.73d;
        for (BoundingBox box : boxes) {
            // Scale boxes to better fit size of reference map.
            box.startX = (int) Math.floor((double) box.startX * factor);
            box.startY = (int) Math.floor((double) box.startY * factor);
            box.size = (int) Math.floor((double) box.size * factor);
        }
        return boxes.toArray(new BoundingBox[0]);
    }

    // returns [x, y] coordinates of minimum error
    public static int[] findMinimumErrorCoordinates(BoundingBox[] boxes) throws IOException, InterruptedException {
        // Calculate boundary coordinates for the piece using the boxes.
        int boxSize = boxes[0].size;
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = 0;
        int maxY = 0;
        for (BoundingBox box : boxes) {
            minX = Math.min(minX, box.startX);
            minY = Math.min(minY, box.startY);
            maxX = Math.max(maxX, box.startX + boxSize);
            maxY = Math.max(maxY, box.startY + boxSize);
        }
        // Calculate the center coordinate
        int centerX = (int) Math.floor(minX + ((double) maxX - (double) minX) / 2d);
        int centerY = (int) Math.floor(minY + ((double) maxY - (double) minY) / 2d);

        for (BoundingBox box : boxes) {
            // Translate every box so that the new (0, 0) is in the center of the piece
            box.startX = box.startX - minX;// - centerX;
            box.startY = box.startY - minY;// - centerY;
        }
        int amountOfThreads = 4;
        Thread[] threads = new Thread[amountOfThreads];
        long[][] results = new long[amountOfThreads][3];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(new ErrorFinder(map, mapWidth, boxes, boxSize, results, i,
                    0, mapWidth - maxX,
                    i * (mapHeight - maxY) / amountOfThreads, (i + 1) * (mapHeight - maxY) / amountOfThreads));
            threads[i].start();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        long minError = Long.MAX_VALUE;
        long minErrorX = 0;
        long minErrorY = 0;
        for (long[] result : results) {
            if (result[0] < minError) {
                minError = result[0];
                minErrorX = result[1];
                minErrorY = result[2];
            }
        }
        if (DEBUG) {
            visualizeBoundingBoxes(boxes, mapImage, "mapWithBoxes", (int) minErrorX, (int) minErrorY);
        }
        System.out.println(String.format("Error: %d, (x: %d, y: %d)", minError, minErrorX, minErrorY));
        return new int[] { (int) minErrorX, (int) minErrorY };
    }

    // for debugging
    public static void visualizeBoundingBoxes(BoundingBox[] boxes, BufferedImage img, String fileName, int offsetX,
            int offsetY) throws IOException {
        ColorModel colorModel = img.getColorModel();
        WritableRaster raster = img.copyData(null);
        boolean isAlphaPremultiplied = colorModel.isAlphaPremultiplied();
        BufferedImage copy = new BufferedImage(colorModel, raster, isAlphaPremultiplied, null);

        int boundingBoxSize = boxes[0].size;
        WritableRaster writableRaster = copy.getRaster();
        int[] whiteLine = new int[boundingBoxSize * 3];
        for (int i = 0; i < whiteLine.length; i += 3) {
            whiteLine[i] = 255;
            whiteLine[i + 1] = 0;
            whiteLine[i + 2] = 0;
        }
        for (BoundingBox box : boxes) {
            int[] avg = new int[3 * box.size * box.size];
            for (int i = 0; i < avg.length; i++) {
                avg[i] = box.avg[i % 3];
            }
            // writableRaster.setPixels(box.startX + offsetX, box.startY + offsetY,
            // box.size, box.size, avg);
            // horizontal lines
            writableRaster.setPixels(box.startX + offsetX, box.startY + offsetY, box.size, 1, whiteLine);
            writableRaster.setPixels(box.startX + offsetX, box.startY + offsetY + boundingBoxSize, box.size, 1,
                    whiteLine);
            // vertical lines
            writableRaster.setPixels(box.startX + offsetX, box.startY + offsetY, 1, box.size, whiteLine);
            writableRaster.setPixels(box.startX + offsetX + boundingBoxSize, box.startY + offsetY, 1, box.size,
                    whiteLine);
        }
        ImageIO.write(copy, "jpg", new File("images/debug/" + fileName + ".jpg"));
    }

    public static void correctColour(BufferedImage img, int[] color) throws IOException {
        int width = img.getWidth();
        int height = img.getHeight();
        int[] data = new int[3 * width * height];
        img.getData().getPixels(0, 0, width, height, data);
        int[] correction = new int[3];
        for (int i = 0; i < 3; i++) {
            correction[i] = SPYDERCUBE[i] - color[i];
        }
        for (int i = 0; i < data.length; i += 3) {
            // add correction to data, bounded to 0 and 255
            data[i] = Math.max(0, Math.min(255, data[i] + correction[0]));
            data[i + 1] = Math.max(0, Math.min(255, data[i + 1] + correction[1]));
            data[i + 2] = Math.max(0, Math.min(255, data[i + 2] + correction[2]));
        }

        WritableRaster r = img.getRaster();
        r.setPixels(0, 0, width, height, data);
        ImageIO.write(img, "jpg", new File("images/received/croppedCorrected.jpg"));
    }
}

class Error {
    int error, startX, startY, endX, endY;

    public Error(int error, int startX, int startY, int endX, int endY) {
        this.error = error;
        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;
    }

    public String toString() {
        return String.format("error: %d, (%d, %d) to (%d, %d)", error, startX, startY, endX, endY);
    }
}

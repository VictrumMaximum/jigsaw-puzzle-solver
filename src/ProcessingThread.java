import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;
import java.util.Arrays;

public class ProcessingThread implements Runnable {
    private Socket clientSocket;
    private int id;
    private boolean[] clients;

    public ProcessingThread(Socket clientSocket, int[] map, int id, boolean[] clients) {
        this.clientSocket = clientSocket;
        this.id = id;
        this.clients = clients;
    }

    @Override
    public void run() {
        byte[] buffer = new byte[4096*2];
        int[] color = new int[3];
        InputStream is;
        OutputStream os;
        try {
            is = clientSocket.getInputStream();
            os = clientSocket.getOutputStream();

            // First receive image file size.
            long fileSize = 0;
            for (int i = 0; i < 8; i++) {
                int data = is.read();
                fileSize |= data << i * 8;
            }
            System.out.println("Filesize: " + fileSize);
            String outputFileName = getOutputFileName(id);
            // Write file to disk.
            FileOutputStream fos = new FileOutputStream(new File(outputFileName));
            int count;
            System.out.println("Writing to file...");
            while (fileSize > 0 && (count = is.read(buffer)) > 0) {
                fos.write(buffer, 0, count);
                fileSize -= count;
            }
            fos.flush();
            fos.close();
            System.out.println("Done");

            // Indicate to client that the file has been received successfully.
            clientSocket.getOutputStream().write(0);

            // we will now receive the other part of the data: the selected color
            for (int i = 0; i < 3; i++) {
                color[i] = is.read();
            }
            // indicate to client that the color has been received successfully
            clientSocket.getOutputStream().write(0);
            System.out.println("Color: " + Arrays.toString(color));

            // Perform the search...
            int[] coordinate;
            try {
                BufferedImage piece = ImageIO.read(new File(outputFileName));
//                correctColour(piece, color);
                BufferedImage croppedImage = Main.cropImage(piece);
                BoundingBox[] boxes = Main.getBoundingBoxes(croppedImage);
                coordinate = Main.findMinimumErrorCoordinates(boxes);

            } catch (Exception e) {
                e.printStackTrace();
                coordinate = new int[]{0, 0};
            }
            // Split both 32-bit integers into 4 bytes and add to buffer
            for (int i = 0; i < 4; i++) {
                buffer[i] = (byte)(coordinate[0] >> i*8);
                buffer[i+4] = (byte)(coordinate[1] >> i*8);
            }
            // Send back coordinates
            os.write(buffer, 0, 8);

            System.out.println("Closing client connection");
            clientSocket.close();
            clients[id] = false;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getOutputFileName(int id) {
        return "images/received/piece"+id+".jpg";
    }
}

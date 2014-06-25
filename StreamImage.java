import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Math.*;

/**
 * A simple image class that is compatible with Java 8 Stream API.
 * You can access and manipulate pixel streams of this image by
 * calling pixels() or parallelPixels() methods. The image can
 * be constructed either from a file or from another stream of
 * pixels.
 */
public class StreamImage {
    private List<Pixel> pixels;
    private double offsetX, offsetY;
    private double width, height;
    private BufferedImage image;

    /**
     * Create a new image from file
     * @param path path to the image file
     * @throws IOException when something goes wrong
     */
    public StreamImage(String path) throws IOException {
        init(ImageIO.read(new File(path)));
    }

    /**
     * Create a new image from a pixel stream
     * @param source source of pixels
     */
    public StreamImage(Stream<Pixel> source) {
        pixels = source.collect(Collectors.toList());
        createImage();
    }

    /**
     * Internal initialization from an existing buffered image.
     * @param img image to use for initialization
     */
    private void init(BufferedImage img) {
        image = img;
        width = img.getWidth();
        height = img.getHeight();
        Pixel[] pixelArray = new Pixel[img.getWidth() * img.getHeight()];
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int color = img.getRGB(x, y);
                pixelArray[y * img.getWidth() + x] = new Pixel(x, y, color);
            }
        }

        this.pixels = Arrays.asList(pixelArray);
    }

    /**
     * Put a pixel to the raster. Performs bilinear interpolation
     * to produce smooth results.
     * @param img image to write to
     * @param pixel pixel to put on the canvas
     */
    private void putPixel(BufferedImage img, Pixel pixel) {
        double x = pixel.x - offsetX;
        double y = pixel.y - offsetY;

        for (int i = (int)x; i <= min(x + 1, (int)width - 1); i++) {
            for (int j = (int)y; j <= min(y + 1, (int)height - 1); j++) {
                Pixel bgpixel = new Pixel(i, j, img.getRGB(i, j));
                double weight = 1 - abs((x - i) * (y - j));
                img.setRGB(i, j, bgpixel.blend(pixel, weight).getRGB());
            }
        }
    }

    /**
     * Recalculate image dimensions based on the image coordinates.
     */
    private void recalculateDimensions() {
        double[] bounds = pixels().map(px -> new double[] {px.x, px.y, px.x, px.y})
                .reduce(new double[4], (a, b) -> new double[] { min(a[0], b[0]), min(a[1], b[1]), max(a[2], b[2]), max(a[3], b[3]) });

        offsetX = bounds[0];
        offsetY = bounds[1];
        width = bounds[2] - offsetX + 1;
        height = bounds[3] - offsetY + 1;
    }

    /**
     * Create a buffered image cache of the image for rendering to a window
     */
    private void createImage() {
        recalculateDimensions();

        image = new BufferedImage((int)width, (int)height, BufferedImage.TYPE_INT_ARGB);
        image.getGraphics().clearRect(0, 0, image.getWidth(), image.getHeight());
        pixels.parallelStream().forEach(px -> putPixel(image, px));
    }

    /**
     * Returns a stream of image pixels. You can manipulate the pixels
     * using map, filter, etc. and later create a new image from the
     * stream.
     * @return pixel stream
     */
    public Stream<Pixel> pixels() {
        return pixels.stream();
    }

    /**
     * Returns a parallel stream of image pixels. Operations such
     * as map, filter etc. are performed in parallel on all the pixels.
     * @return parallel pixel stream
     */
    public Stream<Pixel> parallelPixels() {
        return pixels.parallelStream();
    }

    public double getHeight() {
        return height;
    }

    public double getWidth() {
        return width;
    }

    /**
     * Image area (width * height), also approximate size in pixels
     * @return image area
     */
    public double size() {
        return width * height;
    }

    /**
     * Display the image in a window
     */
    public StreamImage display() {
        new JDialog() {
            {
                setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                setModal(true);
                add(new JPanel() {
                    {
                        setPreferredSize(new Dimension((int)width, (int)height));
                    }
                    @Override
                    public void paint(Graphics g) {
                        super.paint(g);
                        g.drawImage(image, 0, 0, null);
                    }
                });
                pack();
            }
        }.setVisible(true);
        return this;
    }

    /**
     * Save the image to the given file.
     * @param path path to the image file
     * @return this
     * @throws IOException
     */
    public StreamImage save(String path) throws IOException {
        ImageIO.write(image, "png", new File(path));
        return this;
    }

    /**
     * Represents one image pixel. R, G, B values are in the range [0, 1],
     * x, y values depend on image width/height.
     */
    public static class Pixel {
        public double x, y, r, g, b;

        /**
         * Create a new color pixel at the given coordinates
         * @param x x coordinate
         * @param y y coordinate
         * @param r red component [0, 1]
         * @param g green component [0, 1]
         * @param b blue component [0, 1]
         */
        public Pixel(double x, double y, double r, double g, double b) {
            this.x = x;
            this.y = y;
            this.r = r;
            this.g = g;
            this.b = b;
        }

        /**
         * Create a new grayscale pixel at the given coordinates
         * @param x x coordinate
         * @param y y coordinate
         * @param brightness brightness of the pixel [0, 1]
         */
        public Pixel(double x, double y, double brightness) {
            this.x = x;
            this.y = y;
            r = g = b = brightness;
        }

        /**
         * Create from a packed RGB value.
         * @param x x coordinate
         * @param y y coordinate
         * @param rgb RGB value
         */
        public Pixel(int x, int y, int rgb) {
            this.x = x;
            this.y = y;
            r = ((rgb >> 16) & 0xff) / 255.0;
            g = ((rgb >> 8) & 0xff) / 255.0;
            b = (rgb & 0xff) / 255.0;
        }

        public Pixel(double x, double y, Pixel color) {
            this.x = x;
            this.y = y;
            this.r = color.r;
            this.g = color.g;
            this.b = color.b;
        }

        /**
         * Convert to packed color for buffered image
         * @return packed color
         */
        private int getRGB() {
            int ir = Math.max(0, Math.min(255, (int)(r * 255)));
            int ig = Math.max(0, Math.min(255, (int)(g * 255)));
            int ib = Math.max(0, Math.min(255, (int)(b * 255)));
            return 0xff000000 | (ir << 16) | (ig << 8) | ib;
        }

        /**
         * Convert the pixel to grayscale
         * @return grayscale pixel
         */
        public Pixel gray() {
            double avg = (r + g + b) / 3;
            return new Pixel(x, y, avg, avg, avg);
        }

        /**
         * Make the pixel brighter by the given amount
         * @param amount brightness amount
         * @return brighter pixel
         */
        public Pixel brighter(double amount) {
            return mapRGB(component -> component + amount);
        }

        /**
         * Make the pixel darker by the given amount
         * @param amount darkness amount
         * @return darker pixel
         */
        public Pixel darker(double amount) {
            return brighter(-amount);
        }

        /**
         * Runs a function on each of the three RGB components
         * and returns a new pixel with the altered RGB values.
         * X and Y stay intact.
         * @param mapper mapping function for color components
         * @return pixel with an altered color
         */
        public Pixel mapRGB(Function<Double, Double> mapper) {
            return new Pixel(x, y, mapper.apply(r), mapper.apply(g), mapper.apply(b));
        }

        /**
         * Runs a function on each XY coordinate and returns
         * a new pixel with the altered XY coordinates.
         * RGB stays intact.
         * @param mapper mapping function for XY
         * @return pixel with an altered position
         */
        public Pixel mapXY(Function<Double, Double> mapper) {
            return new Pixel(mapper.apply(x), mapper.apply(y), r, g, b);
        }

        /**
         * Alpha blend this pixel with another
         * @param pixel pixel to blend with
         * @param alpha opacity of the pixel from the pixel parameter
         * @return alpha blended pixel
         */
        public Pixel blend(Pixel pixel, double alpha) {
            Pixel outpixel = new Pixel(x, y, 0, 0, 0);
            outpixel.r = pixel.r * alpha + r * (1 - alpha);
            outpixel.g = pixel.g * alpha + g * (1 - alpha);
            outpixel.b = pixel.b * alpha + b * (1 - alpha);
            return outpixel;
        }

        /**
         * Rotate the pixel by the given angle (in degrees).
         * @param angleDeg angle (in degrees) to rotate by
         * @return rotated pixel
         */
        public Pixel rotated(double angleDeg) {
            double angleRad = Math.PI * angleDeg / 180;
            double cosa = cos(angleRad);
            double sina = sin(angleRad);
            return new Pixel(x * cosa - y * sina, x * sina + y * cosa, r, g, b);
        }

        /**
         * Adds RGB values of another pixel to RGB values of this pixel
         * @param another RGB values to add
         * @return pixel with RGB values summed from this and another
         */
        public Pixel addRGB(Pixel another) {
            return new Pixel(x, y, r + another.r, g + another.g, b + another.b);
        }

        /**
         * Averages RGB values of another pixel with RGB values of this pixel
         * @param another RGB values to average with
         * @return pixel with average RGB values
         */
        public Pixel averageRGB(Pixel another) {
            return new Pixel(x, y, (r + another.r) / 2, (g + another.g) / 2, (b + another.b) / 2);
        }

        /**
         * Compute euclidean distance of this pixel to the given point.
         * @return euclidean distance
         */
        public double distance(double ox, double oy) {
            return sqrt((x - ox) * (x - ox) + (y - oy) * (y - oy));
        }
    }
}

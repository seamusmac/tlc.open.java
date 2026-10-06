/*******************************************************************************
 * Copyright (c) 2009-2015 The Last Check, LLC, All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.thelastcheck.commons.base.utils;

import com.thelastcheck.commons.buffer.ByteArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import javax.swing.*;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.AffineTransformOp;
import java.awt.image.BandCombineOp;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.ConvolveOp;
import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;
import java.awt.image.Kernel;
import java.awt.image.RenderedImage;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Hashtable;

public abstract class ImageUtils {

    private static Logger log = LoggerFactory.getLogger(ImageUtils.class);

    public static RenderedImage convertToRenderedImage(byte[] data) throws IOException {
        ByteArrayInputStream stream = new ByteArrayInputStream(data);
        ImageInputStream iis = ImageIO.createImageInputStream(stream);
		RenderedImage im = ImageIO.read(iis);

        if (im == null) {
            ByteArray ba = new ByteArray(data);
            int len = ba.getLength() < 24 ? ba.getLength() : 24;
            log.warn("Unable to find codec for data: " + ba.readPns(0, len));
        }
        return im;
    }

    public static void saveImage(byte[] data, String fileName) throws IOException {
        File file = new File(fileName);
        saveImage(data, file);
    }

    public static void saveImage(byte[] data, File file) throws IOException {
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(data);
        fos.close();
    }

    public static BufferedImage convertToBufferedImage(RenderedImage im) {
        if (im instanceof BufferedImage) {
            return (BufferedImage) im;
        }
        ColorModel cm = im.getColorModel();
        WritableRaster raster = cm.createCompatibleWritableRaster(im.getWidth(), im.getHeight());
        im.copyData(raster.createWritableTranslatedChild(im.getMinX(), im.getMinY()));
        Hashtable<String, Object> properties = new Hashtable<String, Object>();
        String[] names = im.getPropertyNames();
        if (names != null) {
            for (String name : names) {
                properties.put(name, im.getProperty(name));
            }
        }
        return new BufferedImage(cm, raster, cm.isAlphaPremultiplied(), properties);
    }

    public static BufferedImage convertToBufferedImage(byte[] data) throws IOException {
        RenderedImage im = convertToRenderedImage(data);
        BufferedImage image = convertToBufferedImage(im);
        return image;
    }

    public static BufferedImage scaleImage(RenderedImageWrapper wrapper, double scale) {
        BufferedImage bi = convertToBufferedImage(wrapper.image());
        return scaleImage(bi, scale);
    }

    public static BufferedImage scaleImage(BufferedImage bi, double scale) {
        BufferedImage biNew = new BufferedImage((int) (bi.getWidth() * scale), (int) (bi.getHeight() * scale),
                BufferedImage.TYPE_INT_RGB);
        AffineTransform at = AffineTransform.getScaleInstance(scale, scale);
        // AffineTransformOp op = new AffineTransformOp(at, null);
        Graphics2D g = biNew.createGraphics();
        g.drawImage(bi, at, null);
        // op.filter(bi, biNew);
        return biNew;
    }

    public static ImageIcon convertToImageIcon(BufferedImage bi) {
        ImageIcon ii = new ImageIcon(bi);
        return ii;
    }

    public static BufferedImage rotateImage(BufferedImage bi, int rotations) {
        rotations = rotations % 4;
        int newWidth = bi.getWidth();
        int newHeight = bi.getHeight();
        int moveX = 0;
        int moveY = 0;
        if (rotations % 2 != 0) {
            newHeight = bi.getWidth();
            newWidth = bi.getHeight();
        }
        if (rotations > 1)
            moveY = newHeight;
        if (rotations > 0 && rotations < 3)
            moveX = newWidth;
        Graphics2D g2d = bi.createGraphics();
        AffineTransform af = new AffineTransform();
        af.concatenate(AffineTransform.getTranslateInstance(moveX, moveY));
        af.concatenate(AffineTransform.getRotateInstance(rotations * 0.5 * Math.PI));
        g2d.drawImage((Image) bi, af, null);
        return bi;
    }

    public static RenderedImage scale(RenderedImage image, float scale) {
        return scale(image, scale, AffineTransformOp.TYPE_BICUBIC);
    }

    /**
     * @param interpolation one of the {@link AffineTransformOp} interpolation types
     */
    public static RenderedImage scale(RenderedImage image, float scale, int interpolation) {
        AffineTransformOp op = new AffineTransformOp(AffineTransform.getScaleInstance(scale, scale), interpolation);
        return op.filter(convertToBufferedImage(image), null);
    }

    public static RenderedImage grayscale(RenderedImage image) {
        int numBands = image.getSampleModel().getNumBands();
        if (numBands == 1)
            return image;
        // a lot of sample code uses 0.114, 0.587, 0.299 instead of 1/3
        float[][] matrix = new float[1][numBands];
        for (int i = 0; i < Math.min(numBands, 3); i++)
            matrix[0][i] = 1f / 3;
        WritableRaster gray = new BandCombineOp(matrix, null).filter(convertToBufferedImage(image).getRaster(), null);
        ColorModel cm = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_GRAY), false, false,
                Transparency.OPAQUE, gray.getTransferType());
        return new BufferedImage(cm, gray, false, null);
    }

    public static RenderedImage invert(RenderedImage image) {
        BufferedImage bi = convertToBufferedImage(image);
        int dataType = bi.getRaster().getTransferType();
        if (dataType == DataBuffer.TYPE_FLOAT || dataType == DataBuffer.TYPE_DOUBLE)
            throw new IllegalArgumentException("Only integral image data can be inverted");
        WritableRaster raster = bi.copyData(null);
        int width = raster.getWidth();
        int[] samples = new int[width];
        for (int b = 0; b < raster.getNumBands(); b++) {
            int max = (1 << raster.getSampleModel().getSampleSize(b)) - 1;
            for (int y = 0; y < raster.getHeight(); y++) {
                raster.getSamples(0, y, width, 1, b, samples);
                for (int x = 0; x < width; x++)
                    samples[x] = max - samples[x];
                raster.setSamples(0, y, width, 1, b, samples);
            }
        }
        return new BufferedImage(bi.getColorModel(), raster, bi.isAlphaPremultiplied(), null);
    }

    public static RenderedImage binarize(RenderedImage image) {
        if (image.getSampleModel().getNumBands() > 1)
            image = grayscale(image);
        return binarize(image, getBinarizationThreshold(image));
    }

    /**
     * Produces a bi-level image where samples greater than or equal to the threshold are 1 (white), others 0 (black).
     */
    public static RenderedImage binarize(RenderedImage image, double threshold) {
        if (image.getSampleModel().getNumBands() > 1)
            image = grayscale(image);
        WritableRaster src = convertToBufferedImage(image).getRaster();
        int width = src.getWidth();
        int height = src.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        WritableRaster dst = result.getRaster();
        double[] samples = new double[width];
        int[] bits = new int[width];
        for (int y = 0; y < height; y++) {
            src.getSamples(0, y, width, 1, 0, samples);
            for (int x = 0; x < width; x++)
                bits[x] = samples[x] >= threshold ? 1 : 0;
            dst.setSamples(0, y, width, 1, 0, bits);
        }
        return result;
    }

    /**
     * Computes a threshold for band 0 from a 256 bin histogram using the minimum fuzziness method (Huang and Wang,
     * Shannon entropy), as JAI's Histogram.getMinFuzzinessThreshold() did.
     */
    public static double getBinarizationThreshold(RenderedImage image) {
        WritableRaster raster = convertToBufferedImage(image).getRaster();
        int width = raster.getWidth();
        long[] bins = new long[256];
        double[] samples = new double[width];
        for (int y = 0; y < raster.getHeight(); y++) {
            raster.getSamples(0, y, width, 1, 0, samples);
            for (int x = 0; x < width; x++) {
                if (samples[x] >= 0 && samples[x] < 256)
                    bins[(int) samples[x]]++;
            }
        }

        int minIndex = 0;
        while (minIndex < 255 && bins[minIndex] == 0)
            minIndex++;
        int maxIndex = 255;
        while (maxIndex > minIndex && bins[maxIndex] == 0)
            maxIndex--;
        if (minIndex == maxIndex)
            return minIndex;

        double c = maxIndex - minIndex;
        double minFuzziness = Double.MAX_VALUE;
        int threshold = minIndex + 1;
        // class 0 is [minIndex, t - 1], class 1 is [t, maxIndex]
        for (int t = minIndex + 1; t <= maxIndex; t++) {
            double mu0 = mean(bins, minIndex, t - 1);
            double mu1 = mean(bins, t, maxIndex);
            double fuzziness = 0;
            for (int j = minIndex; j <= maxIndex; j++) {
                if (bins[j] == 0)
                    continue;
                double mu = 1.0 / (1.0 + Math.abs(j - (j < t ? mu0 : mu1)) / c);
                fuzziness += bins[j] * shannonEntropy(mu);
            }
            if (fuzziness < minFuzziness) {
                minFuzziness = fuzziness;
                threshold = t;
            }
        }
        return threshold;
    }

    private static double mean(long[] bins, int from, int to) {
        double sum = 0;
        long count = 0;
        for (int i = from; i <= to; i++) {
            sum += (double) i * bins[i];
            count += bins[i];
        }
        return count == 0 ? 0 : sum / count;
    }

    private static double shannonEntropy(double mu) {
        if (mu <= 0 || mu >= 1)
            return 0;
        return -mu * Math.log(mu) - (1 - mu) * Math.log(1 - mu);
    }

    public static RenderedImage crop(RenderedImage image, Rectangle2D rectangle) {
        return crop(image, (float) rectangle.getX(), (float) rectangle.getY(), (float) rectangle.getWidth(),
                (float) rectangle.getHeight());
    }

    /**
     * The returned image shares its data with the source image and is located at the origin.
     */
    public static RenderedImage crop(RenderedImage image, float x, float y, float width, float height) {
        BufferedImage bi = convertToBufferedImage(image);
        Rectangle area = new Rectangle2D.Float(x - image.getMinX(), y - image.getMinY(), width, height).getBounds()
                .intersection(new Rectangle(0, 0, bi.getWidth(), bi.getHeight()));
        if (area.isEmpty())
            throw new IllegalArgumentException("Crop area is outside the image bounds");
        return bi.getSubimage(area.x, area.y, area.width, area.height);
    }

    public static RenderedImage blur(RenderedImage image, int radius) {
        int klen = Math.max(radius, 2);
        int ksize = klen * klen;
        float f = 1f / ksize;
        float[] kern = new float[ksize];
        for (int i = 0; i < ksize; i++)
            kern[i] = f;
        BufferedImage src = convertToBufferedImage(image);
        if (src.getColorModel() instanceof IndexColorModel) {
            // convolving palette indexes is meaningless, expand to RGB first
            BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(),
                    src.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.drawImage(src, 0, 0, null);
            g.dispose();
            src = rgb;
        }
        // pad by copying the edge pixels so the border is blurred too
        int left = (klen - 1) / 2;
        int top = (klen - 1) / 2;
        BufferedImage padded = padWithEdgeCopy(src, left, top, klen - 1 - left, klen - 1 - top);
        ConvolveOp op = new ConvolveOp(new Kernel(klen, klen, kern), ConvolveOp.EDGE_NO_OP, null);
        return op.filter(padded, null).getSubimage(left, top, src.getWidth(), src.getHeight());
    }

    private static BufferedImage padWithEdgeCopy(BufferedImage src, int left, int top, int right, int bottom) {
        ColorModel cm = src.getColorModel();
        int width = src.getWidth();
        int height = src.getHeight();
        int paddedWidth = width + left + right;
        WritableRaster srcRaster = src.getRaster();
        WritableRaster raster = cm.createCompatibleWritableRaster(paddedWidth, height + top + bottom);
        int bands = srcRaster.getNumBands();
        double[] row = new double[width * bands];
        double[] paddedRow = new double[paddedWidth * bands];
        for (int y = 0; y < raster.getHeight(); y++) {
            int srcY = Math.min(Math.max(y - top, 0), height - 1);
            srcRaster.getPixels(0, srcY, width, 1, row);
            for (int x = 0; x < paddedWidth; x++) {
                int srcX = Math.min(Math.max(x - left, 0), width - 1);
                System.arraycopy(row, srcX * bands, paddedRow, x * bands, bands);
            }
            raster.setPixels(0, y, paddedWidth, 1, paddedRow);
        }
        return new BufferedImage(cm, raster, src.isAlphaPremultiplied(), null);
    }
}

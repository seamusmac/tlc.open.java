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

import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.plugins.tiff.BaselineTIFFTagSet;
import javax.imageio.plugins.tiff.TIFFDirectory;
import javax.imageio.plugins.tiff.TIFFField;
import javax.imageio.plugins.tiff.TIFFTag;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

public class RenderedImageWrapper {

	public static final int			TAG_YRESOLUTION					= BaselineTIFFTagSet.TAG_Y_RESOLUTION;
	public static final int			TAG_XRESOLUTION					= BaselineTIFFTagSet.TAG_X_RESOLUTION;
	public static final int			TAG_RESOLUTION_UNIT				= BaselineTIFFTagSet.TAG_RESOLUTION_UNIT;
	public static final int			TAG_PHOTOMETRIC_INTERPRETATION	= BaselineTIFFTagSet.TAG_PHOTOMETRIC_INTERPRETATION;

	private static final String		TIFF_METADATA_FORMAT			= "javax_imageio_tiff_image_1.0";

	private RenderedImage			image;
	private TIFFDirectory			directory;
	private Map<Integer, TIFFField>	fieldMap						= new HashMap<Integer, TIFFField>();
	private boolean					isTiff							= false;

	@SuppressWarnings("unused")
	private RenderedImageWrapper() {
	}

	public RenderedImageWrapper(RenderedImage image) {
		this(image, null);
	}

	/**
	 * @param metadata
	 *            the image metadata returned by the ImageIO reader; if it is TIFF metadata the TIFF fields are made
	 *            available through {@link #tiffField(int)}.
	 */
	public RenderedImageWrapper(RenderedImage image, IIOMetadata metadata) {
		this.image = image;
		if (metadata != null && TIFF_METADATA_FORMAT.equals(metadata.getNativeMetadataFormatName())) {
			try {
				directory = TIFFDirectory.createFromMetadata(metadata);
			} catch (IOException e) {
				throw new IllegalArgumentException("Unable to read TIFF metadata", e);
			}
			isTiff = true;
			for (TIFFField tiffField : directory.getTIFFFields()) {
				fieldMap.put(tiffField.getTagNumber(), tiffField);
			}
		}
	}

	/**
	 * Reads image data with ImageIO, keeping the TIFF metadata when the data is a TIFF image.
	 */
	public static RenderedImageWrapper read(byte[] data) throws IOException {
		try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
			if (!readers.hasNext()) {
				throw new IOException("Unable to find codec for image data");
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(iis, true, false);
				BufferedImage image = reader.read(0);
				IIOMetadata metadata = reader.getImageMetadata(0);
				return new RenderedImageWrapper(image, metadata);
			} finally {
				reader.dispose();
			}
		}
	}

	public TIFFField tiffField(int tag) {
		TIFFField field = null;
		if (isTiff) {
			if (fieldMap.containsKey(tag)) {
				field = fieldMap.get(tag);
			}
		}
		return field;
	}

	public boolean isTiff() {
		return isTiff;
	}

	public RenderedImage image() {
		return image;
	}

	public long resolution() {
		long[] values = resolutionValues(image);
		long res = values[0];
		return res;
	}

	public long[] resolutionValues(RenderedImage im) {
		TIFFField xresField = fieldMap.get(TAG_XRESOLUTION);
		TIFFField yresField = fieldMap.get(TAG_YRESOLUTION);
		long xres = getFieldValue(xresField);
		long yres = getFieldValue(yresField);
		long[] values = new long[2];
		values[0] = xres;
		values[1] = yres;
		return values;
	}

	private long getFieldValue(TIFFField field) {
		long value;
		if (field.getType() == TIFFTag.TIFF_DOUBLE) {
			value = (int) field.getAsDouble(0);
		} else if (field.getType() == TIFFTag.TIFF_FLOAT) {
			value = (int) field.getAsFloat(0);
		} else if (field.getType() == TIFFTag.TIFF_RATIONAL) {
			long[] values = field.getAsRational(0);
			value = (int) ((double) values[0] / (double) values[1]);
		} else if (field.getType() == TIFFTag.TIFF_LONG) {
			value = (int) field.getAsLong(0);
		} else {
			value = field.getAsInt(0);
		}
		return value;
	}

	/**
	 * Encodes the image as a little-endian, single strip, CCITT Group 4 TIFF. The image must be bi-level.
	 */
	public byte[] convertToTiff() throws IOException {
		RenderedImage image = this.image;
		BaselineTIFFTagSet tagSet = BaselineTIFFTagSet.getInstance();

		TIFFField xresField = tiffField(RenderedImageWrapper.TAG_XRESOLUTION);
		TIFFField yresField = tiffField(RenderedImageWrapper.TAG_YRESOLUTION);
		TIFFField photoMetricField = tiffField(RenderedImageWrapper.TAG_PHOTOMETRIC_INTERPRETATION);

		TIFFField resUnitField = new TIFFField(tagSet.getTag(TAG_RESOLUTION_UNIT), TIFFTag.TIFF_SHORT, 1,
				new char[] { BaselineTIFFTagSet.RESOLUTION_UNIT_INCH });

		if (xresField == null) {
			long[][] rational = new long[][] { { 240, 1 } };
			xresField = new TIFFField(tagSet.getTag(TAG_XRESOLUTION), TIFFTag.TIFF_RATIONAL, 1, rational);
		}
		if (yresField == null) {
			long[][] rational = new long[][] { { 240, 1 } };
			yresField = new TIFFField(tagSet.getTag(TAG_YRESOLUTION), TIFFTag.TIFF_RATIONAL, 1, rational);
		}
		if (photoMetricField == null) {
			photoMetricField = new TIFFField(tagSet.getTag(TAG_PHOTOMETRIC_INTERPRETATION), TIFFTag.TIFF_SHORT, 1,
					new char[] { BaselineTIFFTagSet.PHOTOMETRIC_INTERPRETATION_WHITE_IS_ZERO });
		}
		// write the whole image as a single strip
		TIFFField rowsPerStripField = new TIFFField(tagSet.getTag(BaselineTIFFTagSet.TAG_ROWS_PER_STRIP),
				TIFFTag.TIFF_LONG, 1, new long[] { image.getHeight() });

		ImageWriter writer = ImageIO.getImageWritersByFormatName("tiff").next();
		try {
			ImageWriteParam param = writer.getDefaultWriteParam();
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionType("CCITT T.6");

			IIOMetadata defaultMetadata = writer.getDefaultImageMetadata(
					ImageTypeSpecifier.createFromRenderedImage(image), param);
			TIFFDirectory dir = TIFFDirectory.createFromMetadata(defaultMetadata);
			dir.addTIFFField(photoMetricField);
			dir.addTIFFField(xresField);
			dir.addTIFFField(yresField);
			dir.addTIFFField(resUnitField);
			dir.addTIFFField(rowsPerStripField);

			int size = ((image.getWidth() * image.getHeight()) / 8) + 2048;
			ByteArrayOutputStream stream = new ByteArrayOutputStream(size);
			try (ImageOutputStream ios = ImageIO.createImageOutputStream(stream)) {
				ios.setByteOrder(ByteOrder.LITTLE_ENDIAN);
				writer.setOutput(ios);
				writer.write(null, new IIOImage(image, null, dir.getAsMetadata()), param);
			}
			return stream.toByteArray();
		} finally {
			writer.dispose();
		}
	}

	public RenderedImage rotateImage(int degree) {
		return rotateImage(image, degree);
	}

	public static RenderedImage rotateImage(RenderedImage image, int degree) {

		// Create the rotation angle and convert to radians.
		double angle = Math.toRadians(degree);

		double centerX = image.getWidth() / 2d;
		double centerY = image.getHeight() / 2d;
		AffineTransform rotate = AffineTransform.getRotateInstance(angle, centerX, centerY);

		// shift the rotated image so its bounds start at the origin
		Rectangle2D bounds = rotate.createTransformedShape(
				new Rectangle(0, 0, image.getWidth(), image.getHeight())).getBounds2D();
		AffineTransform transform = AffineTransform.getTranslateInstance(-bounds.getX(), -bounds.getY());
		transform.concatenate(rotate);

		AffineTransformOp op = new AffineTransformOp(transform, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
		return op.filter(ImageUtils.convertToBufferedImage(image), null);
	}

}

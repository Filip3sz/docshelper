package code.filipesz.docshelper.services;

import org.springframework.ai.chat.messages.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

final class ImageScalerService {

    private static final int MAX_IMAGE_DIMENSION = 1600;
    private static final float JPEG_QUALITY = 0.90f;

    private ImageScalerService() {}

    static Media prepare(MultipartFile file) throws IOException {
        byte[] rawBytes = file.getBytes();
        MimeType mimeType = MimeTypeUtils.parseMimeType(file.getContentType());
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(rawBytes));
        if (source == null || Math.max(source.getWidth(), source.getHeight()) <= MAX_IMAGE_DIMENSION) {
            return new Media(mimeType, new ByteArrayResource(rawBytes));
        }

        double scale = (double) MAX_IMAGE_DIMENSION / Math.max(source.getWidth(), source.getHeight());
        int targetWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int targetHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));
        boolean keepAlpha = source.getColorModel().hasAlpha();

        BufferedImage scaled = new BufferedImage(
                targetWidth, targetHeight, keepAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (keepAlpha) {
            ImageIO.write(scaled, "png", out);
            return new Media(MimeTypeUtils.IMAGE_PNG, new ByteArrayResource(out.toByteArray()));
        }

        writeHighQualityJpeg(scaled, out);
        return new Media(MimeTypeUtils.IMAGE_JPEG, new ByteArrayResource(out.toByteArray()));
    }

    private static void writeHighQualityJpeg(BufferedImage image, ByteArrayOutputStream out) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        try (var output = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(output);
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
    }
}

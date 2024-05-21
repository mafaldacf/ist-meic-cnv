package pt.ulisboa.tecnico.cnv.javassist;

import javax.imageio.ImageWriteParam;
import java.awt.image.BufferedImage;

public class CompressionParameters {

    private float compressionQuality;
    private String compressionType;
    private BufferedImage buferedImage;

    private Long tid;

    public CompressionParameters(float compressionQuality, String compressionType, BufferedImage buferedImage, Long tid) {
        this.compressionQuality = compressionQuality;
        this.compressionType = compressionType;
        this.buferedImage = buferedImage;
        this.tid = tid;
    }

    public float getCompressionQuality() {
        return compressionQuality;
    }

    public void setCompressionQuality(float compressionQuality) {
        this.compressionQuality = compressionQuality;
    }

    public String getCompressionType() {
        return compressionType;
    }

    public void setCompressionType(String compressionType) {
        this.compressionType = compressionType;
    }

    public BufferedImage getBuferedImage() {
        return buferedImage;
    }

    public void setBuferedImage(BufferedImage buferedImage) {
        this.buferedImage = buferedImage;
    }

    public Long getTid() {
        return tid;
    }

    public void setTid(Long tid) {
        this.tid = tid;
    }

    @Override
    public String toString() {
        return "CompressionParameters{" +
                "compressionQuality=" + compressionQuality +
                ";compressionType=" + compressionType +
                ";imageWriteParam=" + buferedImage +
                '}';
    }
}

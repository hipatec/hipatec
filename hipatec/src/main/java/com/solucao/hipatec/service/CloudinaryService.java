package com.solucao.hipatec.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Service
public class CloudinaryService {

    @Autowired
    private Cloudinary cloudinary;

    public String uploadFile(MultipartFile file) {

        if (file.getSize() > 5 * 1024 * 1024)
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Imagem deve ter até 5 MB.");
        try (var input = javax.imageio.ImageIO.createImageInputStream(file.getInputStream())) {
            var readers = javax.imageio.ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException();
            var reader = readers.next();
            try {
                reader.setInput(input);
                String formato = reader.getFormatName();
                if (!(formato.equalsIgnoreCase("png") || formato.equalsIgnoreCase("jpeg"))
                        || reader.getWidth(0) > 4096 || reader.getHeight(0) > 4096)
                    throw new IllegalArgumentException();
            } finally { reader.dispose(); }
        } catch (Exception error) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Envie uma imagem PNG ou JPEG de até 4096 pixels.");
        }
        try {

            Map<?, ?> uploadResult =
                    cloudinary.uploader().upload(
                            file.getBytes(),
                            ObjectUtils.emptyMap()
                    );

            return uploadResult.get("secure_url").toString();

        } catch (Exception e) {

            throw new RuntimeException(
                    "Erro ao enviar imagem",
                    e
            );
        }
    }
}
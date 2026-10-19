package com.solucao.hipatec;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.solucao.hipatec.service.CloudinaryService;
import com.cloudinary.Cloudinary;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class ImagemPerfilTests {
 @Test void rejeitaArquivoDisfarcadoDeImagemAntesDoUpload() {
  var service = new CloudinaryService();
  var cloudinary = mock(Cloudinary.class);
  ReflectionTestUtils.setField(service, "cloudinary", cloudinary);
  var arquivo = new MockMultipartFile("pfp", "foto.png", "image/png", "<script>teste</script>".getBytes());
  assertThrows(ResponseStatusException.class, () -> service.uploadFile(arquivo));
  verifyNoInteractions(cloudinary);
 }
 @Test void rejeitaImagemAcimaDoLimiteAntesDoUpload() {
  var arquivo = new MockMultipartFile("pfp", "foto.jpg", "image/jpeg", new byte[5*1024*1024+1]);
  assertThrows(ResponseStatusException.class, () -> new CloudinaryService().uploadFile(arquivo));
 }
}

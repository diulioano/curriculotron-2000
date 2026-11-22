package dev.curriculotron.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.curriculotron.profile.adapter.in.web.ResumeDocumentExtractor;
import dev.curriculotron.profile.domain.InvalidResumeDocumentException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class ResumeDocumentExtractorTest {

  private final ResumeDocumentExtractor extractor = new ResumeDocumentExtractor();

  @Test
  void extractsTextFromValidPdf() throws IOException {
    byte[] pdf =
        pdfWithLines(
            "RESUMO PROFISSIONAL",
            "Desenvolvedor backend com 4 anos de experiencia em Java e Spring Boot.",
            "HABILIDADES TECNICAS",
            "Java, Spring Boot, Kafka");

    String text = extractor.extract(pdf, "curriculo.pdf", "application/pdf");

    assertThat(text).contains("RESUMO PROFISSIONAL").contains("Java, Spring Boot, Kafka");
  }

  @Test
  void rejectsPdfWithoutSelectableText() throws IOException {
    byte[] blankPdf = pdfWithLines();

    assertThatThrownBy(() -> extractor.extract(blankPdf, "escaneado.pdf", "application/pdf"))
        .isInstanceOf(InvalidResumeDocumentException.class)
        .hasMessageContaining("imagem escaneada");
  }

  @Test
  void rejectsInvalidOrUnsupportedFiles() {
    assertThatThrownBy(() ->
            extractor.extract(
                "not a pdf".getBytes(StandardCharsets.UTF_8), "curriculo.pdf", "application/pdf"))
        .isInstanceOf(InvalidResumeDocumentException.class)
        .hasMessageContaining("PDF válido");

    assertThatThrownBy(() ->
            extractor.extract(
                "texto".getBytes(StandardCharsets.UTF_8),
                "curriculo.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
        .isInstanceOf(InvalidResumeDocumentException.class)
        .hasMessageContaining("Formato não suportado");

    assertThatThrownBy(() -> extractor.extract(new byte[] {0, 1, 2}, "curriculo.txt", "text/plain"))
        .isInstanceOf(InvalidResumeDocumentException.class);
  }

  @Test
  void extractsMarkdownAndPlainTextFilesAndStripsUtf8Bom() {
    byte[] content = "- Cargo: Dev Java".getBytes(StandardCharsets.UTF_8);
    byte[] withBom = "\uFEFF- Cargo: Dev Java".getBytes(StandardCharsets.UTF_8);

    assertThat(extractor.extract(content, "curriculo.md", "text/markdown"))
        .isEqualTo("- Cargo: Dev Java");
    assertThat(extractor.extract(content, "curriculo.txt", "text/plain"))
        .isEqualTo("- Cargo: Dev Java");
    assertThat(extractor.extract(withBom, "curriculo.md", "text/markdown"))
        .isEqualTo("- Cargo: Dev Java");
  }

  @Test
  void rejectsPasswordProtectedCorruptedOversizedAndExcessivePagePdfs() throws IOException {
    byte[] encrypted = encryptedPdf("segredo");
    assertThatThrownBy(() -> extractor.extract(encrypted, "protegido.pdf", "application/pdf"))
        .isInstanceOf(InvalidResumeDocumentException.class)
        .hasMessageContaining("protegido por senha");

    byte[] corrupted = "%PDF-1.7\ncorrompido".getBytes(StandardCharsets.UTF_8);
    assertThatThrownBy(() -> extractor.extract(corrupted, "corrompido.pdf", "application/pdf"))
        .isInstanceOf(InvalidResumeDocumentException.class)
        .hasMessageContaining("Não foi possível ler este PDF");

    byte[] tooManyPages = multiPagePdf(ResumeDocumentExtractor.MAX_PDF_PAGES + 1);
    assertThatThrownBy(() -> extractor.extract(tooManyPages, "grande.pdf", "application/pdf"))
        .isInstanceOf(InvalidResumeDocumentException.class)
        .hasMessageContaining("páginas");

    assertThatThrownBy(() -> extractor.extract(new byte[0], "vazio.pdf", "application/pdf"))
        .isInstanceOf(InvalidResumeDocumentException.class)
        .hasMessageContaining("vazio");

    byte[] overFiveMb = new byte[ResumeDocumentExtractor.MAX_FILE_BYTES + 1];
    assertThatThrownBy(() -> extractor.extract(overFiveMb, "enorme.pdf", "application/pdf"))
        .isInstanceOf(InvalidResumeDocumentException.class)
        .hasMessageContaining("5 MB");

    byte[] overMaxChars =
        "a"
            .repeat(ResumeDocumentExtractor.MAX_EXTRACTED_CHARS + 1)
            .getBytes(StandardCharsets.UTF_8);
    assertThatThrownBy(() -> extractor.extract(overMaxChars, "longo.txt", "text/plain"))
        .isInstanceOf(InvalidResumeDocumentException.class)
        .hasMessageContaining("limite suportado");
  }

  private static byte[] encryptedPdf(String password) throws IOException {
    try (var document = new PDDocument();
        var out = new ByteArrayOutputStream()) {
      document.addPage(new PDPage());
      var policy = new StandardProtectionPolicy(password, password, new AccessPermission());
      document.protect(policy);
      document.save(out);
      return out.toByteArray();
    }
  }

  private static byte[] multiPagePdf(int pages) throws IOException {
    try (var document = new PDDocument();
        var out = new ByteArrayOutputStream()) {
      for (int i = 0; i < pages; i++) {
        document.addPage(new PDPage());
      }
      document.save(out);
      return out.toByteArray();
    }
  }

  static byte[] pdfWithLines(String... lines) throws IOException {
    try (var document = new PDDocument();
        var out = new ByteArrayOutputStream()) {
      var page = new PDPage();
      document.addPage(page);
      if (lines.length > 0) {
        try (var stream = new PDPageContentStream(document, page)) {
          stream.beginText();
          stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
          stream.setLeading(16f);
          stream.newLineAtOffset(50, 720);
          for (String line : lines) {
            stream.showText(line);
            stream.newLine();
          }
          stream.endText();
        }
      }
      document.save(out);
      return out.toByteArray();
    }
  }
}

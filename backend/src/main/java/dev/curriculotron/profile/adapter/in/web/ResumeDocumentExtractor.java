package dev.curriculotron.profile.adapter.in.web;

import dev.curriculotron.profile.domain.InvalidResumeDocumentException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * Extrai texto de arquivos de currículo (PDF, Markdown ou texto puro) enviados para
 * pré-visualização do perfil (US006). Nada é gravado em disco ou banco.
 */
@Component
public final class ResumeDocumentExtractor {

  public static final int MAX_FILE_BYTES = 5 * 1024 * 1024;
  public static final int MAX_EXTRACTED_CHARS = 100_000;
  public static final int MAX_PDF_PAGES = 30;
  private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};

  public String extract(byte[] bytes, String filename, String contentType) {
    if (bytes == null || bytes.length == 0) {
      throw new InvalidResumeDocumentException("O arquivo enviado está vazio.");
    }
    if (bytes.length > MAX_FILE_BYTES) {
      throw new InvalidResumeDocumentException("O arquivo excede o limite de 5 MB.");
    }

    String lowerName = filename == null ? "" : filename.strip().toLowerCase(Locale.ROOT);
    String lowerType = contentType == null ? "" : contentType.strip().toLowerCase(Locale.ROOT);
    boolean hasPdfMagic = startsWithPdfMagic(bytes);
    boolean claimsPdf = lowerName.endsWith(".pdf") || lowerType.equals("application/pdf");

    if (claimsPdf && !hasPdfMagic) {
      throw new InvalidResumeDocumentException("O arquivo não parece ser um PDF válido.");
    }
    if (hasPdfMagic) {
      return extractPdf(bytes);
    }
    if (!isSupportedTextFile(lowerName, lowerType)) {
      throw new InvalidResumeDocumentException(
          "Formato não suportado. Envie um currículo em PDF, Markdown (.md) ou texto (.txt).");
    }
    return extractText(bytes);
  }

  private static String extractPdf(byte[] bytes) {
    try (var document = Loader.loadPDF(bytes)) {
      if (document.getNumberOfPages() > MAX_PDF_PAGES) {
        throw new InvalidResumeDocumentException(
            "O PDF excede o limite de " + MAX_PDF_PAGES + " páginas.");
      }
      var stripper = new PDFTextStripper();
      stripper.setSortByPosition(true);
      String text = stripper.getText(document);
      if (text == null || text.isBlank()) {
        throw new InvalidResumeDocumentException(
            "Não foi possível extrair texto deste PDF. Se ele for uma imagem escaneada, escreva ou cole o resumo abaixo.");
      }
      if (text.length() > MAX_EXTRACTED_CHARS) {
        throw new InvalidResumeDocumentException(
            "O texto extraído do PDF excede o limite suportado.");
      }
      return text;
    } catch (InvalidPasswordException e) {
      throw new InvalidResumeDocumentException(
          "O PDF está protegido por senha. Remova a proteção ou escreva o resumo abaixo.", e);
    } catch (IOException e) {
      throw new InvalidResumeDocumentException(
          "Não foi possível ler este PDF. Verifique o arquivo ou escreva o resumo abaixo.", e);
    }
  }

  private static String extractText(byte[] bytes) {
    for (byte b : bytes) {
      if (b == 0) {
        throw new InvalidResumeDocumentException(
            "O arquivo não parece ser texto legível. Envie um PDF, .md ou .txt.");
      }
    }
    String text = new String(bytes, StandardCharsets.UTF_8);
    if (text.startsWith("\uFEFF")) {
      text = text.substring(1);
    }
    if (text.isBlank()) {
      throw new InvalidResumeDocumentException("O arquivo enviado está vazio.");
    }
    if (text.length() > MAX_EXTRACTED_CHARS) {
      throw new InvalidResumeDocumentException("O conteúdo do arquivo excede o limite suportado.");
    }
    return text;
  }

  private static boolean startsWithPdfMagic(byte[] bytes) {
    if (bytes.length < PDF_MAGIC.length) return false;
    for (int i = 0; i < PDF_MAGIC.length; i++) {
      if (bytes[i] != PDF_MAGIC[i]) return false;
    }
    return true;
  }

  private static boolean isSupportedTextFile(String lowerName, String lowerType) {
    if (lowerName.endsWith(".md")
        || lowerName.endsWith(".markdown")
        || lowerName.endsWith(".txt")) {
      return true;
    }
    if (lowerName.isEmpty() || !lowerName.contains(".")) {
      return lowerType.startsWith("text/plain")
          || lowerType.startsWith("text/markdown")
          || lowerType.isEmpty();
    }
    return false;
  }
}

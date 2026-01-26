package com.bewerbung.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for generating PDF documents according to German DIN 5008 standard
 */
@Service
public class PdfGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(PdfGenerationService.class);
    
    // DIN 5008 margins: 2.5cm top/left/right, 2.0cm bottom
    private static final float MARGIN_LEFT = 72f * 2.5f / 2.54f; // 2.5 cm in points (1 inch = 2.54 cm, 1 inch = 72 points)
    private static final float MARGIN_RIGHT = 72f * 2.5f / 2.54f;
    private static final float MARGIN_TOP = 72f * 2.5f / 2.54f;
    private static final float MARGIN_BOTTOM = 72f * 2.0f / 2.54f; // 2.0 cm bottom
    
    private static final float FONT_SIZE = 11f; // DIN 5008: 11-12pt
    private static final float LINE_HEIGHT = 13.5f; // Single line spacing (1.15 * font size)
    
    /**
     * Gets a font that supports UTF-8 characters (German umlauts)
     * Falls back to Helvetica if no UTF-8 font is available
     */
    private static PDFont getUtf8Font(PDDocument document) throws IOException {
        try {
            // Try to load a TrueType font from the system that supports German characters
            // Common system fonts that support German: Arial, DejaVu Sans, Liberation Sans
            String[] fontPaths = {
                "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                "/usr/share/fonts/truetype/arial.ttf",
                "/System/Library/Fonts/Helvetica.ttc",
                "C:/Windows/Fonts/arial.ttf"
            };
            
            for (String fontPath : fontPaths) {
                try {
                    java.io.File fontFile = new java.io.File(fontPath);
                    if (fontFile.exists()) {
                        logger.info("Loading UTF-8 font from: {}", fontPath);
                        return PDType0Font.load(document, fontFile);
                    }
                } catch (Exception e) {
                    // Try next font
                    continue;
                }
            }
            
            // Fallback: Use built-in font and handle encoding manually
            logger.warn("No UTF-8 font found, using Helvetica with character replacement");
            return new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        } catch (Exception e) {
            logger.warn("Error loading UTF-8 font, falling back to Helvetica", e);
            return new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        }
    }
    
    /**
     * Encodes text to handle German umlauts for Type1 fonts
     * Replaces unsupported characters with ASCII equivalents
     */
    private static String encodeTextForType1Font(String text) {
        if (text == null) return "";
        return text
            .replace("ä", "ae")
            .replace("ö", "oe")
            .replace("ü", "ue")
            .replace("Ä", "Ae")
            .replace("Ö", "Oe")
            .replace("Ü", "Ue")
            .replace("ß", "ss")
            .replace("ẞ", "SS");
    }

    /**
     * Generates a PDF document from the cover letter text according to DIN 5008 standard
     * 
     * @param coverLetterText The cover letter text to convert to PDF
     * @return Byte array containing the PDF document
     * @throws IOException if PDF generation fails
     */
    public byte[] generatePdf(String coverLetterText) throws IOException {
        logger.info("Generating PDF from cover letter text (length: {} chars)", coverLetterText.length());
        
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            
            float pageWidth = page.getMediaBox().getWidth();
            float pageHeight = page.getMediaBox().getHeight();
            float contentWidth = pageWidth - MARGIN_LEFT - MARGIN_RIGHT;
            
            // Get UTF-8 supporting font
            PDFont font = getUtf8Font(document);
            boolean isUtf8Font = font instanceof PDType0Font;
            
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                float yPosition = pageHeight - MARGIN_TOP;
                
                // Parse the cover letter text to extract address and main content
                CoverLetterParts parts = parseCoverLetter(coverLetterText);
                
                // Draw sender address (if exists) - right aligned at top
                if (parts.hasSenderAddress()) {
                    yPosition = drawSenderAddress(contentStream, parts.getSenderAddress(), 
                        pageWidth - MARGIN_RIGHT, yPosition, font, isUtf8Font);
                    yPosition -= LINE_HEIGHT * 2; // Space after address
                }
                
                // Draw date (right aligned)
                String date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
                String dateText = isUtf8Font ? date : encodeTextForType1Font(date);
                float dateWidth = font.getStringWidth(dateText) / 1000f * FONT_SIZE;
                try {
                    contentStream.beginText();
                    contentStream.setFont(font, FONT_SIZE);
                    contentStream.newLineAtOffset(pageWidth - MARGIN_RIGHT - dateWidth, yPosition);
                    if (isUtf8Font) {
                        contentStream.showText(date);
                    } else {
                        contentStream.showText(dateText);
                    }
                    contentStream.endText();
                } catch (IllegalArgumentException e) {
                    // Fallback: if UTF-8 font fails, try with encoded text
                    logger.warn("Failed to show date with UTF-8 font, using encoded text: {}", e.getMessage());
                    contentStream.beginText();
                    contentStream.setFont(font, FONT_SIZE);
                    contentStream.newLineAtOffset(pageWidth - MARGIN_RIGHT - dateWidth, yPosition);
                    contentStream.showText(encodeTextForType1Font(date));
                    contentStream.endText();
                }
                yPosition -= LINE_HEIGHT * 2;
                
                // Draw main content with proper line wrapping
                yPosition = drawWrappedText(contentStream, parts.getMainContent(), 
                    MARGIN_LEFT, yPosition, contentWidth, FONT_SIZE, LINE_HEIGHT, font, isUtf8Font);
            }
            
            // Convert to byte array
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.save(outputStream);
            byte[] pdfBytes = outputStream.toByteArray();
            
            logger.info("PDF generated successfully ({} bytes)", pdfBytes.length);
            return pdfBytes;
        }
    }
    
    /**
     * Parses cover letter text into structured parts
     */
    private CoverLetterParts parseCoverLetter(String text) {
        CoverLetterParts parts = new CoverLetterParts();
        String[] lines = text.split("\n");
        
        List<String> mainContentLines = new ArrayList<>();
        List<String> senderAddressLines = new ArrayList<>();
        
        // Find sender address at the end (after "Mit freundlichen Grüßen" and name)
        int greetingIndex = -1;
        int nameIndex = -1;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.equalsIgnoreCase("Mit freundlichen Grüßen") || 
                line.equalsIgnoreCase("Mit freundlichen Grüssen")) {
                greetingIndex = i;
            }
            if (greetingIndex >= 0 && i > greetingIndex && nameIndex == -1 && 
                !line.isEmpty() && !line.equalsIgnoreCase("Mit freundlichen Grüßen") &&
                !line.equalsIgnoreCase("Mit freundlichen Grüssen")) {
                nameIndex = i;
            }
        }
        
        // Extract sender address (lines after name that look like address)
        if (nameIndex >= 0) {
            for (int i = nameIndex + 1; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;
                // Check if line looks like address (contains @, postal code pattern, or phone pattern, or street)
                // Use case-insensitive patterns for German street names (Straße, Str., etc.)
                if (line.contains("@") || line.matches(".*\\d{5}.*") || 
                    line.matches(".*\\+49.*") || line.matches("(?i).*straße.*") ||
                    line.matches("(?i).*str\\..*")) {
                    senderAddressLines.add(line);
                } else if (senderAddressLines.isEmpty() && line.length() > 0) {
                    // First non-empty line after name might be part of address
                    senderAddressLines.add(line);
                } else if (!senderAddressLines.isEmpty()) {
                    senderAddressLines.add(line);
                }
            }
        }
        
        // Extract main content (everything before greeting)
        int endOfContent = greetingIndex >= 0 ? greetingIndex : lines.length;
        for (int i = 0; i < endOfContent; i++) {
            mainContentLines.add(lines[i]);
        }
        
        // Add greeting and name to main content
        if (greetingIndex >= 0) {
            mainContentLines.add("");
            mainContentLines.add(lines[greetingIndex]);
            if (nameIndex >= 0 && nameIndex != greetingIndex) {
                mainContentLines.add("");
                mainContentLines.add(lines[nameIndex]);
            }
        }
        
        parts.setMainContent(String.join("\n", mainContentLines));
        parts.setSenderAddress(String.join("\n", senderAddressLines));
        
        return parts;
    }
    
    /**
     * Draws sender address at the specified position (right aligned)
     */
    private float drawSenderAddress(PDPageContentStream contentStream, String address, 
                                   float xRight, float yStart, PDFont font, boolean isUtf8Font) throws IOException {
        String[] lines = address.split("\n");
        float yPosition = yStart;
        
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            
            String text = isUtf8Font ? line.trim() : encodeTextForType1Font(line.trim());
            float textWidth = font.getStringWidth(text) / 1000f * FONT_SIZE;
            try {
                contentStream.beginText();
                contentStream.setFont(font, FONT_SIZE);
                contentStream.newLineAtOffset(xRight - textWidth, yPosition);
                if (isUtf8Font) {
                    contentStream.showText(line.trim());
                } else {
                    contentStream.showText(text);
                }
                contentStream.endText();
            } catch (IllegalArgumentException e) {
                // Fallback: if UTF-8 font fails, try with encoded text
                logger.warn("Failed to show text with UTF-8 font, using encoded text: {}", e.getMessage());
                contentStream.beginText();
                contentStream.setFont(font, FONT_SIZE);
                contentStream.newLineAtOffset(xRight - textWidth, yPosition);
                contentStream.showText(encodeTextForType1Font(line.trim()));
                contentStream.endText();
            }
            yPosition -= LINE_HEIGHT;
        }
        
        return yPosition;
    }
    
    /**
     * Draws wrapped text with proper line breaks
     */
    private float drawWrappedText(PDPageContentStream contentStream, String text, 
                                 float x, float yStart, float maxWidth, 
                                 float fontSize, float lineHeight, PDFont font, boolean isUtf8Font) throws IOException {
        String[] lines = text.split("\n");
        float yPosition = yStart;
        
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                // Empty line - just add spacing
                yPosition -= lineHeight;
                continue;
            }
            
            List<String> wrappedLines = wrapText(line, maxWidth, fontSize, font, isUtf8Font);
            for (String wrappedLine : wrappedLines) {
                if (yPosition < MARGIN_BOTTOM) {
                    // Would go below margin, but we continue anyway for simplicity
                    // In production, you might want to add a new page
                    break;
                }
                
                String displayText = isUtf8Font ? wrappedLine : encodeTextForType1Font(wrappedLine);
                try {
                    contentStream.beginText();
                    contentStream.setFont(font, fontSize);
                    contentStream.newLineAtOffset(x, yPosition);
                    if (isUtf8Font) {
                        contentStream.showText(wrappedLine);
                    } else {
                        contentStream.showText(displayText);
                    }
                    contentStream.endText();
                } catch (IllegalArgumentException e) {
                    // Fallback: if UTF-8 font fails, try with encoded text
                    logger.warn("Failed to show text with UTF-8 font, using encoded text: {}", e.getMessage());
                    contentStream.beginText();
                    contentStream.setFont(font, fontSize);
                    contentStream.newLineAtOffset(x, yPosition);
                    contentStream.showText(encodeTextForType1Font(wrappedLine));
                    contentStream.endText();
                }
                yPosition -= lineHeight;
            }
        }
        
        return yPosition;
    }
    
    /**
     * Wraps text to fit within the specified width
     */
    private List<String> wrapText(String text, float maxWidth, float fontSize, PDFont font, boolean isUtf8Font) throws IOException {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();
        
        for (String word : words) {
            String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
            String testText = isUtf8Font ? testLine : encodeTextForType1Font(testLine);
            float width = font.getStringWidth(testText) / 1000f * fontSize;
            
            if (width > maxWidth && currentLine.length() > 0) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            } else {
                if (currentLine.length() > 0) {
                    currentLine.append(" ");
                }
                currentLine.append(word);
            }
        }
        
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        
        return lines;
    }
    
    /**
     * Helper class to hold parsed cover letter parts
     */
    private static class CoverLetterParts {
        private String mainContent = "";
        private String senderAddress = "";
        
        public String getMainContent() {
            return mainContent;
        }
        
        public void setMainContent(String mainContent) {
            this.mainContent = mainContent;
        }
        
        public String getSenderAddress() {
            return senderAddress;
        }
        
        public void setSenderAddress(String senderAddress) {
            this.senderAddress = senderAddress;
        }
        
        public boolean hasSenderAddress() {
            return senderAddress != null && !senderAddress.trim().isEmpty();
        }
    }
}

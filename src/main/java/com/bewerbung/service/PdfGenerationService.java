package com.bewerbung.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
     * Gets a font that supports UTF-8 characters (German umlauts, Cyrillic, etc.)
     * Uses only PDType0Font to avoid FontMapper initialization issues on Oracle Linux
     * @throws IOException if no suitable font can be loaded
     */
    private static PDFont getUtf8Font(PDDocument document) throws IOException {
        // Try to load a TrueType/OpenType font from the system that supports German characters
        // Common system fonts that support German: Arial, DejaVu Sans, Liberation Sans, Nimbus Sans
        String[] fontPaths = {
            // Standard Linux paths (TTF)
            "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
            "/usr/share/fonts/truetype/arial.ttf",
            "/usr/share/fonts/TTF/DejaVuSans.ttf",
            "/usr/share/fonts/TTF/LiberationSans-Regular.ttf",
            // Oracle Linux / RHEL paths (OTF)
            "/usr/share/fonts/opentype/urw-base35/NimbusSans-Regular.otf",
            "/usr/share/fonts/opentype/urw-base35/NimbusSansNarrow-Regular.otf",
            // macOS
            "/System/Library/Fonts/Helvetica.ttc",
            // Windows
            "C:/Windows/Fonts/arial.ttf"
        };
        
        for (String fontPath : fontPaths) {
            try {
                java.io.File fontFile = new java.io.File(fontPath);
                if (fontFile.exists() && fontFile.canRead()) {
                    logger.info("Found font file at: {} (size: {} bytes), attempting to load...", 
                        fontPath, fontFile.length());
                    try {
                        PDFont font = PDType0Font.load(document, fontFile);
                        // Test if font can handle a test character
                        try {
                            font.getStringWidth("Test äöü");
                            logger.info("UTF-8 font loaded successfully from: {}", fontPath);
                            return font;
                        } catch (Exception e) {
                            logger.warn("Font from {} doesn't support UTF-8 characters, trying next: {}", 
                                fontPath, e.getMessage());
                            continue;
                        }
                    } catch (Exception loadException) {
                        logger.warn("Failed to load font from {}: {} (class: {})", 
                            fontPath, loadException.getMessage(), loadException.getClass().getSimpleName());
                        // Log full exception for debugging
                        if (logger.isDebugEnabled()) {
                            logger.debug("Full exception when loading font from " + fontPath, loadException);
                        }
                        continue;
                    }
                } else {
                    logger.debug("Font file not found or not readable: {}", fontPath);
                }
            } catch (Exception e) {
                // Try next font
                logger.warn("Exception while checking font path {}: {}", fontPath, e.getMessage());
                if (logger.isDebugEnabled()) {
                    logger.debug("Full exception for font path " + fontPath, e);
                }
                continue;
            }
        }
        
        // Try to load font from resources (if available)
        try {
            InputStream fontStream = PdfGenerationService.class.getResourceAsStream("/fonts/LiberationSans-Regular.ttf");
            if (fontStream != null) {
                logger.info("Loading UTF-8 font from resources");
                PDFont font = PDType0Font.load(document, fontStream);
                try {
                    font.getStringWidth("Test äöü");
                    logger.info("UTF-8 font loaded successfully from resources");
                    return font;
                } catch (Exception e) {
                    logger.warn("Font from resources doesn't support UTF-8", e);
                }
            }
        } catch (Exception e) {
            logger.debug("Could not load font from resources: {}", e.getMessage());
        }
        
        // If no font found, throw descriptive error with details about what was checked
        StringBuilder errorDetails = new StringBuilder();
        errorDetails.append("No UTF-8 supporting font found. ");
        errorDetails.append("Checked ").append(fontPaths.length).append(" system font paths. ");
        errorDetails.append("Please ensure one of the following fonts is available: ");
        errorDetails.append("Liberation Sans, DejaVu Sans, Nimbus Sans, or Arial. ");
        errorDetails.append("Alternatively, place a TTF/OTF font file in src/main/resources/fonts/");
        String errorMsg = errorDetails.toString();
        logger.error(errorMsg);
        logger.error("Font search failed. Check logs above for details about which paths were checked.");
        throw new IOException(errorMsg);
    }
    
    /**
     * Encodes text to handle special characters (fallback encoding)
     * Replaces unsupported characters (German umlauts, Cyrillic, etc.) with ASCII equivalents
     * Note: This should not be needed if UTF-8 font is loaded correctly
     */
    private static String encodeTextForType1Font(String text) {
        if (text == null) return "";
        String result = text;
        
        // German umlauts
        result = result
            .replace("ä", "ae")
            .replace("ö", "oe")
            .replace("ü", "ue")
            .replace("Ä", "Ae")
            .replace("Ö", "Oe")
            .replace("Ü", "Ue")
            .replace("ß", "ss")
            .replace("ẞ", "SS");
        
        // Remove or replace Cyrillic and other non-ASCII characters
        // Replace with question marks or remove them
        StringBuilder sb = new StringBuilder();
        for (char c : result.toCharArray()) {
            if (c <= 127) { // ASCII range
                sb.append(c);
            } else {
                // Replace non-ASCII with '?' or remove
                sb.append('?');
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Safely gets string width, handling encoding errors
     */
    private static float getStringWidthSafe(PDFont font, String text, boolean isUtf8Font) throws IOException {
        try {
            String displayText = isUtf8Font ? text : encodeTextForType1Font(text);
            return font.getStringWidth(displayText) / 1000f;
        } catch (IllegalArgumentException e) {
            // If UTF-8 font fails, try with encoded text
            String encodedText = encodeTextForType1Font(text);
            try {
                return font.getStringWidth(encodedText) / 1000f;
            } catch (IllegalArgumentException e2) {
                // Last resort: estimate width based on character count
                logger.warn("Could not calculate string width for text, using estimation: {}", e2.getMessage());
                return encodedText.length() * 6f; // Rough estimate: ~6 points per character
            }
        }
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
            
            // If no UTF-8 font found, force encoding for all text
            if (!isUtf8Font) {
                logger.info("No UTF-8 font available, will encode all text for Type1 font compatibility");
            }
            
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
                float dateWidth = getStringWidthSafe(font, date, isUtf8Font) * FONT_SIZE;
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
            float textWidth = getStringWidthSafe(font, line.trim(), isUtf8Font) * FONT_SIZE;
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
            float width = getStringWidthSafe(font, testLine, isUtf8Font) * fontSize;
            
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

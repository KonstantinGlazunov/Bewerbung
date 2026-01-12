package com.bewerbung.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
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
    
    // DIN 5008 margins: 2.5cm top/bottom/left, 2.0cm bottom (but we'll use 2.5cm for simplicity)
    private static final float MARGIN_LEFT = 72f * 2.5f / 2.54f; // 2.5 cm in points (1 inch = 2.54 cm, 1 inch = 72 points)
    private static final float MARGIN_RIGHT = 72f * 2.5f / 2.54f;
    private static final float MARGIN_TOP = 72f * 2.5f / 2.54f;
    private static final float MARGIN_BOTTOM = 72f * 2.0f / 2.54f; // 2.0 cm bottom
    
    private static final float FONT_SIZE = 11f; // DIN 5008: 11-12pt
    private static final float LINE_HEIGHT = 13.5f; // Single line spacing (1.15 * font size)
    
    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font FONT_BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

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
            
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                float yPosition = pageHeight - MARGIN_TOP;
                
                // Parse the cover letter text to extract address and main content
                CoverLetterParts parts = parseCoverLetter(coverLetterText);
                
                // Draw sender address (if exists) - right aligned at top
                if (parts.hasSenderAddress()) {
                    yPosition = drawSenderAddress(contentStream, parts.getSenderAddress(), 
                        pageWidth - MARGIN_RIGHT, yPosition);
                    yPosition -= LINE_HEIGHT * 2; // Space after address
                }
                
                // Draw date (right aligned)
                String date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
                contentStream.beginText();
                contentStream.setFont(FONT, FONT_SIZE);
                contentStream.newLineAtOffset(pageWidth - MARGIN_RIGHT - 
                    FONT.getStringWidth(date) / 1000 * FONT_SIZE, yPosition);
                contentStream.showText(date);
                contentStream.endText();
                yPosition -= LINE_HEIGHT * 2;
                
                // Draw recipient address (if exists) - left aligned
                if (parts.hasRecipientAddress()) {
                    yPosition = drawRecipientAddress(contentStream, parts.getRecipientAddress(), 
                        MARGIN_LEFT, yPosition);
                    yPosition -= LINE_HEIGHT * 2;
                }
                
                // Draw main content with proper line wrapping
                yPosition = drawWrappedText(contentStream, parts.getMainContent(), 
                    MARGIN_LEFT, yPosition, contentWidth, FONT_SIZE, LINE_HEIGHT);
                
                // Draw signature (if exists) - left aligned
                if (parts.hasSignature()) {
                    yPosition -= LINE_HEIGHT * 2;
                    yPosition = drawWrappedText(contentStream, parts.getSignature(), 
                        MARGIN_LEFT, yPosition, contentWidth, FONT_SIZE, LINE_HEIGHT);
                }
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
                if (line.contains("@") || line.matches(".*\\d{5}.*") || 
                    line.matches(".*\\+49.*") || line.matches(".*straße.*") ||
                    line.matches(".*str\\..*") || line.matches(".*Str\\..*")) {
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
                                   float xRight, float yStart) throws IOException {
        String[] lines = address.split("\n");
        float yPosition = yStart;
        
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            
            float textWidth = FONT.getStringWidth(line.trim()) / 1000 * FONT_SIZE;
            contentStream.beginText();
            contentStream.setFont(FONT, FONT_SIZE);
            contentStream.newLineAtOffset(xRight - textWidth, yPosition);
            contentStream.showText(line.trim());
            contentStream.endText();
            yPosition -= LINE_HEIGHT;
        }
        
        return yPosition;
    }
    
    /**
     * Draws recipient address at the specified position (left aligned)
     */
    private float drawRecipientAddress(PDPageContentStream contentStream, String address, 
                                      float xLeft, float yStart) throws IOException {
        String[] lines = address.split("\n");
        float yPosition = yStart;
        
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            
            contentStream.beginText();
            contentStream.setFont(FONT, FONT_SIZE);
            contentStream.newLineAtOffset(xLeft, yPosition);
            contentStream.showText(line.trim());
            contentStream.endText();
            yPosition -= LINE_HEIGHT;
        }
        
        return yPosition;
    }
    
    /**
     * Draws wrapped text with proper line breaks
     */
    private float drawWrappedText(PDPageContentStream contentStream, String text, 
                                 float x, float yStart, float maxWidth, 
                                 float fontSize, float lineHeight) throws IOException {
        String[] paragraphs = text.split("\n\n");
        float yPosition = yStart;
        
        for (String paragraph : paragraphs) {
            List<String> lines = wrapText(paragraph, maxWidth, fontSize);
            for (String line : lines) {
                if (yPosition < MARGIN_BOTTOM) {
                    // Would go below margin, but we continue anyway for simplicity
                    // In production, you might want to add a new page
                    break;
                }
                
                contentStream.beginText();
                contentStream.setFont(FONT, fontSize);
                contentStream.newLineAtOffset(x, yPosition);
                contentStream.showText(line);
                contentStream.endText();
                yPosition -= lineHeight;
            }
            yPosition -= lineHeight * 0.5f; // Space between paragraphs
        }
        
        return yPosition;
    }
    
    /**
     * Wraps text to fit within the specified width
     */
    private List<String> wrapText(String text, float maxWidth, float fontSize) throws IOException {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();
        
        for (String word : words) {
            String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
            float width = FONT.getStringWidth(testLine) / 1000 * fontSize;
            
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
        private String recipientAddress = "";
        private String signature = "";
        private String signatureName = "";
        
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
        
        public String getRecipientAddress() {
            return recipientAddress;
        }
        
        public void setRecipientAddress(String recipientAddress) {
            this.recipientAddress = recipientAddress;
        }
        
        public boolean hasRecipientAddress() {
            return recipientAddress != null && !recipientAddress.trim().isEmpty();
        }
        
        public String getSignature() {
            return signature;
        }
        
        public void setSignature(String signature) {
            this.signature = signature;
        }
        
        public boolean hasSignature() {
            return signature != null && !signature.trim().isEmpty();
        }
        
        public String getSignatureName() {
            return signatureName;
        }
        
        public void setSignatureName(String signatureName) {
            this.signatureName = signatureName;
        }
    }
}


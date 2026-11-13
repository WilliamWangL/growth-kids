package com.growthkids.api.report.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.stereotype.Component;

/**
 * HTML → PDF 渲染（TASK-018，§99：规范 Puppeteer 为 NestJS 生态选择，
 * Java 侧等价替换为 openhtmltopdf；内嵌 Noto Sans SC 字体保证中文可嵌入）。
 * 字体必须为 TrueType 轮廓（.ttf）：openhtmltopdf/PDFBox 不支持 CFF 轮廓的 OTF，
 * 官方 noto-cjk OTF 均为 CFF，故采用 google/fonts 的 NotoSansSC variable TTF（默认实例 Regular）。
 */
@Component
public class ReportPdfRenderer {

    private static final String FONT_RESOURCE = "/fonts/NotoSansSC-Regular.ttf";

    private static final String FONT_FAMILY = "Noto Sans SC";

    private volatile Path cachedFontFile;

    /** 渲染 XHTML 为 PDF 字节（字体子集嵌入，仅含用到的字形）。 */
    public byte[] render(String xhtml) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            Path font = fontFile();
            if (font != null) {
                builder.useFont(font.toFile(), FONT_FAMILY);
            }
            builder.withHtmlContent(xhtml, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to render report PDF", e);
        }
    }

    /** classpath 字体解包到临时文件（openhtmltopdf 需要 File；进程内缓存复用）。 */
    private Path fontFile() {
        Path cached = cachedFontFile;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (cachedFontFile != null) {
                return cachedFontFile;
            }
            try (InputStream in = getClass().getResourceAsStream(FONT_RESOURCE)) {
                if (in == null) {
                    // 字体缺失不阻断：拉丁字符仍可渲染，中文会退化（开发环境告警）
                    return null;
                }
                Path temp = Files.createTempFile("growthkids-noto-sc-", ".ttf");
                temp.toFile().deleteOnExit();
                Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
                cachedFontFile = temp;
                return temp;
            } catch (IOException e) {
                return null;
            }
        }
    }
}

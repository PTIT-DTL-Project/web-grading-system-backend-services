package vn.edu.ptit.web_grading_system.executor_service.config;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

public class CappedContentCachingResponseWrapper extends HttpServletResponseWrapper {

    private final int maxCacheBytes;
    private final java.io.ByteArrayOutputStream cache = new java.io.ByteArrayOutputStream();
    private ServletOutputStream outputStream;
    private PrintWriter writer;

    public CappedContentCachingResponseWrapper(HttpServletResponse response, int maxCacheBytes) {
        super(response);
        this.maxCacheBytes = maxCacheBytes;
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (writer != null) {
            throw new IllegalStateException("getWriter() has already been called");
        }
        if (outputStream == null) {
            outputStream = new ServletOutputStream() {
                @Override
                public void write(int b) throws IOException {
                    if (cache.size() < maxCacheBytes) {
                        cache.write(b);
                    }
                    getResponse().getOutputStream().write(b);
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    int remaining = maxCacheBytes - cache.size();
                    if (remaining > 0) {
                        cache.write(b, off, Math.min(len, remaining));
                    }
                    getResponse().getOutputStream().write(b, off, len);
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setWriteListener(WriteListener writeListener) {
                }
            };
        }
        return outputStream;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (outputStream != null) {
            throw new IllegalStateException("getOutputStream() has already been called");
        }
        if (writer == null) {
            writer = new PrintWriter(new OutputStreamWriter(new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    if (cache.size() < maxCacheBytes) {
                        cache.write(b);
                    }
                    getResponse().getOutputStream().write(b);
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    int remaining = maxCacheBytes - cache.size();
                    if (remaining > 0) {
                        cache.write(b, off, Math.min(len, remaining));
                    }
                    getResponse().getOutputStream().write(b, off, len);
                }
            }, StandardCharsets.UTF_8));
        }
        return writer;
    }

    public byte[] getContentAsByteArray() {
        return cache.toByteArray();
    }

    public void flushToResponse() throws IOException {
        if (writer != null) {
            writer.flush();
        }
        getResponse().flushBuffer();
    }
}

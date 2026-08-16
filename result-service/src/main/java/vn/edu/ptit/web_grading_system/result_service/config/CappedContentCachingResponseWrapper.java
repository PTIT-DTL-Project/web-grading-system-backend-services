package vn.edu.ptit.web_grading_system.result_service.config;

import java.io.ByteArrayOutputStream;
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
    private final ByteArrayOutputStream cache = new ByteArrayOutputStream();
    private boolean overflow;
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
                    if (overflow || cache.size() >= maxCacheBytes) {
                        overflow = true;
                        getResponse().getOutputStream().write(b);
                    } else {
                        cache.write(b);
                    }
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    if (overflow) {
                        getResponse().getOutputStream().write(b, off, len);
                        return;
                    }
                    int remaining = maxCacheBytes - cache.size();
                    if (len <= remaining) {
                        cache.write(b, off, len);
                    } else {
                        if (remaining > 0) {
                            cache.write(b, off, remaining);
                        }
                        overflow = true;
                        getResponse().getOutputStream().write(b, off + remaining, len - remaining);
                    }
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
                    if (overflow || cache.size() >= maxCacheBytes) {
                        overflow = true;
                        getResponse().getOutputStream().write(b);
                    } else {
                        cache.write(b);
                    }
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    if (overflow) {
                        getResponse().getOutputStream().write(b, off, len);
                        return;
                    }
                    int remaining = maxCacheBytes - cache.size();
                    if (len <= remaining) {
                        cache.write(b, off, len);
                    } else {
                        if (remaining > 0) {
                            cache.write(b, off, remaining);
                        }
                        overflow = true;
                        getResponse().getOutputStream().write(b, off + remaining, len - remaining);
                    }
                }
            }, StandardCharsets.UTF_8));
        }
        return writer;
    }

    public byte[] getContentAsByteArray() {
        return cache.toByteArray();
    }

    public void copyBodyToResponse() throws IOException {
        if (cache.size() > 0) {
            getResponse().getOutputStream().write(cache.toByteArray());
            cache.reset();
        }
        getResponse().flushBuffer();
    }
}
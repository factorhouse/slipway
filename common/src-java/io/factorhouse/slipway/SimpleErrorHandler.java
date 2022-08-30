package io.factorhouse.slipway;

import org.eclipse.jetty.server.handler.ErrorHandler;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.Writer;

public class SimpleErrorHandler extends ErrorHandler {

    private final String content;

    public SimpleErrorHandler(String html) {
        this.content = html;
    }

    protected void writeErrorPage(HttpServletRequest request, Writer writer, int code, String message, boolean showStacks) throws IOException {
        writer.write(content);
    }
}


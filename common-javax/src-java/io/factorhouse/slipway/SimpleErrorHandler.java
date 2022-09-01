package io.factorhouse.slipway;

import org.eclipse.jetty.server.handler.ErrorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.Writer;

public class SimpleErrorHandler extends ErrorHandler {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleErrorHandler.class);

    private final String content;

    public SimpleErrorHandler(String html) {
        this.content = html;
    }

    protected void writeErrorPage(HttpServletRequest request, Writer writer, int code, String message, boolean showStacks) throws IOException {
        // We expect to handle all error codes bar unexpected server error (presume 500) in reitit ring, so we return
        // static error content here. The super class manages the remained of the response (code, setHandled, etc).
        if (code != 401) { // basic auth 401s pass through here, we don't write a body for those
            Throwable th = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
            if (th != null) {
                LOG.error("slipway server error", (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION));
            } else {
                LOG.error("slipway server error: {} - {}", code, message);
            }
            writer.write(content);
        }
    }
}


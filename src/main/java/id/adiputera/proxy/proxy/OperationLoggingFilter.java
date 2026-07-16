package id.adiputera.proxy.proxy;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.UUID;

/**
 * Global JAX-RS filter providing structured logging and tracing for all operations.
 *
 * <p>Captures or generates a correlation ID, records operation entry/exit, tracks
 * execution duration, and manages SLF4J diagnostic context (MDC).</p>
 *
 * @author Yusuf F. Adiputera
 */
@Slf4j
@Provider
@ApplicationScoped
public class OperationLoggingFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String START_TIME_PROP = "operationStartTime";
    private static final String CORRELATION_ID_PROP = "operationCorrelationId";
    private static final String OPERATION_NAME_PROP = "operationName";
    private static final String USER_ID_PROP = "operationUserId";
    private static final String CORRELATION_HEADER = "X-Correlation-ID";

    /**
     * Intercepts incoming requests to initialize tracing context and log operation start.
     *
     * @param requestContext The request context.
     * @throws IOException if an I/O error occurs processing the request.
     * @see ContainerRequestFilter#filter(ContainerRequestContext)
     */
    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String correlationId = requestContext.getHeaderString(CORRELATION_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        String method = requestContext.getMethod();
        String path = requestContext.getUriInfo().getPath();
        String operation = method + " /" + (path.startsWith("/") ? path.substring(1) : path);

        String userId = "anonymous";
        if (requestContext.getSecurityContext() != null && requestContext.getSecurityContext().getUserPrincipal() != null) {
            userId = requestContext.getSecurityContext().getUserPrincipal().getName();
        }

        requestContext.setProperty(START_TIME_PROP, System.currentTimeMillis());
        requestContext.setProperty(CORRELATION_ID_PROP, correlationId);
        requestContext.setProperty(OPERATION_NAME_PROP, operation);
        requestContext.setProperty(USER_ID_PROP, userId);

        MDC.put("correlationId", correlationId);
        MDC.put("operation", operation);
        MDC.put("userId", userId);

        log.info("Operation started: {} [correlationId={}, userId={}]", operation, correlationId, userId);
    }

    /**
     * Intercepts outgoing responses to log operation completion or failure and clean up MDC.
     *
     * @param requestContext  The request context.
     * @param responseContext The response context.
     * @throws IOException if an I/O error occurs processing the response.
     * @see ContainerResponseFilter#filter(ContainerRequestContext, ContainerResponseContext)
     */
    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        try {
            Long startTime = (Long) requestContext.getProperty(START_TIME_PROP);
            long duration = startTime != null ? System.currentTimeMillis() - startTime : 0L;

            String correlationId = (String) requestContext.getProperty(CORRELATION_ID_PROP);
            if (correlationId == null) {
                correlationId = MDC.get("correlationId");
            }
            String operation = (String) requestContext.getProperty(OPERATION_NAME_PROP);
            if (operation == null) {
                operation = requestContext.getMethod() + " " + requestContext.getUriInfo().getPath();
            }

            int status = responseContext.getStatus();
            if (status >= 500) {
                log.error("Operation failed: {} with status {} in {} ms [correlationId={}]", operation, status, duration, correlationId);
            } else {
                log.info("Operation completed: {} in {} ms [status={}, correlationId={}]", operation, duration, status, correlationId);
            }

            if (correlationId != null && responseContext.getHeaderString(CORRELATION_HEADER) == null) {
                responseContext.getHeaders().add(CORRELATION_HEADER, correlationId);
            }
        } finally {
            MDC.remove("correlationId");
            MDC.remove("operation");
            MDC.remove("userId");
        }
    }
}

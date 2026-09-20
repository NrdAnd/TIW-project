package it.polimi.tiw.utils;

import org.junit.jupiter.api.Test;
import javax.servlet.ServletContext;
import javax.servlet.UnavailableException;
import java.lang.reflect.Proxy;
import static org.junit.jupiter.api.Assertions.*;

class ConnectionHandlerTest {
    ServletContext context(String value) {
        return (ServletContext) Proxy.newProxyInstance(ServletContext.class.getClassLoader(), new Class<?>[]{ServletContext.class}, (proxy, method, args) -> value);
    }
    @Test void deploymentContextRemainsSupported() {
        assertEquals("configured", ConnectionHandler.configuration(context("configured"), "dbUser", "TIW_TEST_ABSENT_ENVIRONMENT"));
    }
    @Test void environmentTakesPrecedence() {
        assertEquals(System.getenv("PATH"), ConnectionHandler.configuration(context("context fallback"), "dbUser", "PATH"));
    }
    @Test void missingConfigurationFailsClosed() {
        // The build/test environment must not contain live database credentials.
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TIW_DB_URL") == null);
        var exception = assertThrows(UnavailableException.class, () -> ConnectionHandler.getConnection(context("")));
        assertTrue(exception.getMessage().startsWith("Database configuration is missing"));
    }
}

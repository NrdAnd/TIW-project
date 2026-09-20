package it.polimi.tiw.filters;

import org.junit.jupiter.api.Test;
import javax.servlet.*;
import javax.servlet.http.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import static org.junit.jupiter.api.Assertions.*;

class SessionFiltersTest {
    @SuppressWarnings("unchecked")
    static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
    static class Exchange {
        boolean isNew, authenticated, continued, invalidated;
        int status;
        String location;
        final StringWriter body = new StringWriter();
        final HttpSession session = proxy(HttpSession.class, (p, method, args) -> {
            switch (method.getName()) {
                case "isNew": return isNew;
                case "getAttribute": return authenticated ? new Object() : null;
                case "invalidate": invalidated = true; return null;
                default: return null;
            }
        });
        final ServletContext context = proxy(ServletContext.class, (p, m, a) -> "/document-manager");
        final HttpServletRequest request = proxy(HttpServletRequest.class, (p, method, args) -> {
            switch (method.getName()) {
                case "getSession": return session;
                case "getServletContext": return context;
                case "getMethod": return "POST";
                default: return null;
            }
        });
        final HttpServletResponse response = proxy(HttpServletResponse.class, (p, method, args) -> {
            switch (method.getName()) {
                case "setStatus": status = (int) args[0]; break;
                case "setHeader": location = (String) args[1]; break;
                case "getWriter": return new PrintWriter(body);
            }
            return null;
        });
        final FilterChain chain = (request, response) -> continued = true;
    }
    @Test void anonymousSessionIsRejected() throws Exception {
        Exchange e = new Exchange();
        new Checker().doFilter(e.request, e.response, e.chain);
        assertEquals(403, e.status); assertFalse(e.continued);
        assertEquals("/document-manager/index.html", e.location);
    }
    @Test void newSessionIsRejectedEvenWithAttribute() throws Exception {
        Exchange e = new Exchange(); e.isNew = true; e.authenticated = true;
        new Checker().doFilter(e.request, e.response, e.chain);
        assertEquals(403, e.status); assertFalse(e.continued);
    }
    @Test void establishedAuthenticatedSessionPasses() throws Exception {
        Exchange e = new Exchange(); e.authenticated = true;
        new Checker().doFilter(e.request, e.response, e.chain);
        assertTrue(e.continued); assertEquals(0, e.status);
    }
    @Test void secondLoginInvalidatesSessionAndStopsRequest() throws Exception {
        Exchange e = new Exchange(); e.authenticated = true;
        new LoggedOutChecker().doFilter(e.request, e.response, e.chain);
        assertTrue(e.invalidated); assertFalse(e.continued); assertEquals(403, e.status);
    }
    @Test void anonymousLoginCanProceed() throws Exception {
        Exchange e = new Exchange();
        new LoggedOutChecker().doFilter(e.request, e.response, e.chain);
        assertTrue(e.continued); assertFalse(e.invalidated);
    }
    @Test void allProtectedEndpointsRemainMapped() throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var document = factory.newDocumentBuilder().parse(Path.of("src/main/webapp/WEB-INF/web.xml").toFile());
        var mappings = document.getElementsByTagName("filter-mapping");
        Set<String> protectedPaths = new HashSet<>();
        for (int i = 0; i < mappings.getLength(); i++) {
            var mapping = (org.w3c.dom.Element) mappings.item(i);
            if (!mapping.getElementsByTagName("filter-name").item(0).getTextContent().equals("Checker")) continue;
            var paths = mapping.getElementsByTagName("url-pattern");
            for (int j = 0; j < paths.getLength(); j++) protectedPaths.add(paths.item(j).getTextContent());
        }
        assertTrue(protectedPaths.containsAll(Set.of("/CreateDocument", "/CreateFolder", "/DeleteDocument", "/DeleteFolder", "/GetDocument", "/GetTree", "/GetVersionHistory", "/MoveDocument", "/UploadFiles", "/DownloadFile", "/PreviewFile", "/MoveFolder", "/RenameItem", "/UndoActions", "/SessionToken", "/Logout")));
    }
}

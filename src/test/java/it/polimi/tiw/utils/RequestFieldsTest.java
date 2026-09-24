package it.polimi.tiw.utils;
import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
class RequestFieldsTest {
  private HttpServletRequest request(String query, Map<String,String[]> fields) {
    return (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{HttpServletRequest.class},
        (p,m,a) -> m.getName().equals("getQueryString") ? query : fields);
  }
  @Test void rejectsMergedDuplicatesAndQueryFields() {
    assertFalse(RequestFields.unambiguous(request("username=other", Map.of())));
    assertFalse(RequestFields.unambiguous(request(null, Map.of("username", new String[]{"one","two"}))));
    assertTrue(RequestFields.unambiguous(request(null, Map.of("username", new String[]{"one"}))));
  }
}

package sample;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.ObjectMapper;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SampleTest {

  @Autowired TestRestTemplate restTemplate;

  static ObjectMapper mapper;

  @BeforeClass
  public static void setup() {
    mapper = new ObjectMapper();
    mapper.configure(Feature.ALLOW_SINGLE_QUOTES, true);
  }

  @Test
  public void testAccessWithoutToken() throws Exception {
    ResponseEntity<String> annonymousResponse = restTemplate.getForEntity("/user/ok", String.class);

    assertThat(annonymousResponse.getStatusCode(), is(HttpStatus.FORBIDDEN));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Test
  public void testAuthentication() throws Exception {
    ResponseEntity<Map> loginResponse = doLogin("admin", "password");

    assertThat(loginResponse.getStatusCode(), is(HttpStatus.OK));

    String token = loginResponse.getBody().get("token").toString();

    Map me = doGet("/auth/me", token, Map.class).getBody();

    assertThat(me.get("loginId"), is("admin"));
    assertThat((List<String>) me.get("roles"), containsInAnyOrder("USERS", "ADMINS"));

    Map ext = (Map) me.get("ext");

    assertThat(ext.get("name"), is("Administrator"));
  }

  @Test
  public void testAuthorization() throws Exception {
    String token = doLogin("user", "password").getBody().get("token").toString();

    ResponseEntity<String> adminResponse = doGet("/admin", token, String.class);

    assertThat(adminResponse.getStatusCode(), is(HttpStatus.FORBIDDEN));

    ResponseEntity<String> userResponse = doGet("/user", token, String.class);

    assertThat(userResponse.getStatusCode(), is(HttpStatus.OK));
  }

  @SuppressWarnings({"rawtypes"})
  @Test
  public void testLoginFailure() throws Exception {
    ResponseEntity<Map> loginResponse = doLogin("admin", "incorrectpassword");

    assertThat(loginResponse.getStatusCode(), is(HttpStatus.OK));
    assertThat(loginResponse.getBody().get("success"), is(false));
  }

  @SuppressWarnings("rawtypes")
  ResponseEntity<Map> doLogin(String loginId, String password) throws IOException {
    return restTemplate.postForEntity(
        "/auth/login",
        json("{'loginId':'" + loginId + "', 'password': '" + password + "'}"),
        Map.class);
  }

  @SuppressWarnings("unchecked")
  Map<String, String> json(String json) throws IOException {
    return mapper.readValue(json, Map.class);
  }

  <T> ResponseEntity<T> doGet(String url, String token, Class<T> type) throws URISyntaxException {
    RequestEntity<?> request =
        RequestEntity.get(new URI(url)).header("Authorization", "Bearer " + token).build();

    return restTemplate.exchange(request, type);
  }
}

package dev.kotryos.minischeduler.identity;

import com.github.database.rider.core.api.dataset.DataSet;
import com.github.database.rider.junit5.api.DBRider;
import dev.kotryos.minischeduler.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@DBRider
@DataSet("datasets/identity/alice-and-admin-keys.yml")
class ApiKeyAuthenticationTest {

    private static final String HEADER = "X-API-Key";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpoint_withoutApiKey_isAccessible() throws Exception {
        // when / then
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpoint_withoutApiKey_isUnauthorized() throws Exception {
        // when / then
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withUnknownApiKey_isUnauthorized() throws Exception {
        // when / then
        mockMvc.perform(get("/actuator/info").header(HEADER, "not-a-real-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withBlankApiKey_isUnauthorized() throws Exception {
        // when / then
        mockMvc.perform(get("/actuator/info").header(HEADER, "   "))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withUserApiKey_isForbidden() throws Exception {
        // when / then
        mockMvc.perform(get("/actuator/info").header(HEADER, "alice-demo-key"))
                .andExpect(status().isForbidden());
    }

    @Test
    void protectedEndpoint_withAdminApiKey_isAccessible() throws Exception {
        // when / then
        mockMvc.perform(get("/actuator/info").header(HEADER, "admin-demo-key"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/vnd.spring-boot.actuator.v3+json"));
    }
}

package finos.traderx.accountservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

@WebMvcTest(HealthCheckController.class)
public class HealthCheckControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JdbcTemplate jdbcTemplate;

    @Test
    public void whenDatabaseIsUp_thenReturns200() throws Exception {
        when(jdbcTemplate.queryForObject(eq("SELECT 1"), eq(Integer.class))).thenReturn(1);

        mockMvc.perform(MockMvcRequestBuilders.get("/health"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.status").value("UP"))
               .andExpect(jsonPath("$.database").value("UP"))
               .andExpect(jsonPath("$.service").value("account-service"))
               .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    public void whenDatabaseIsDown_thenReturns503() throws Exception {
        when(jdbcTemplate.queryForObject(eq("SELECT 1"), eq(Integer.class)))
            .thenThrow(new RuntimeException("Database connection failed"));

        mockMvc.perform(MockMvcRequestBuilders.get("/health"))
               .andExpect(status().isServiceUnavailable())
               .andExpect(jsonPath("$.status").value("UP"))
               .andExpect(jsonPath("$.database").value("DOWN"))
               .andExpect(jsonPath("$.error").exists())
               .andExpect(jsonPath("$.service").value("account-service"));
    }
}
